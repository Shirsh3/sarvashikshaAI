package com.sarvashikshaai.service;

import com.sarvashikshaai.ai.OpenAIClient;
import com.sarvashikshaai.model.ConversationContext;
import com.sarvashikshaai.model.entity.LearningConversationEntity;
import com.sarvashikshaai.model.entity.LearningMessageEntity;
import com.sarvashikshaai.repository.LearningConversationRepository;
import com.sarvashikshaai.repository.LearningMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class LearningConversationService {

    private final LearningConversationRepository conversationRepository;
    private final LearningMessageRepository messageRepository;
    private final OpenAIClient openAIClient;

    @Value("${sarva.learning.recent-message-limit:6}")
    private int recentMessageLimit;

    @Value("${sarva.learning.summary-after-messages:8}")
    private int summaryAfterMessages;

    public record Scope(
            String teacherUsername,
            String grade,
            String subject,
            Long materialId,
            String chapterTitle,
            String language,
            String explanationLevel
    ) {}

    /**
     * Active conversation for this teacher + chapter scope.
     * Changing material/chapter creates a new conversation; old ones stay inactive but retained.
     */
    @Transactional
    public LearningConversationEntity getOrCreateActive(Scope scope) {
        String user = blankTo(scope.teacherUsername(), "unknown");
        String grade = blankToNull(scope.grade());
        String subject = blankToNull(scope.subject());
        Long materialId = scope.materialId();

        var existing = conversationRepository
                .findFirstByTeacherUsernameAndGradeAndSubjectAndMaterialIdAndActiveTrueOrderByUpdatedAtDesc(
                        user, grade, subject, materialId);

        if (existing.isPresent()) {
            LearningConversationEntity c = existing.get();
            boolean prefsChanged = false;
            if (scope.language() != null && !scope.language().isBlank()
                    && !scope.language().equalsIgnoreCase(c.getLanguage())) {
                c.setLanguage(normalizeLanguage(scope.language()));
                prefsChanged = true;
            }
            if (scope.explanationLevel() != null && !scope.explanationLevel().isBlank()
                    && !scope.explanationLevel().equalsIgnoreCase(c.getExplanationLevel())) {
                c.setExplanationLevel(normalizeLevel(scope.explanationLevel()));
                prefsChanged = true;
            }
            if (scope.chapterTitle() != null && !scope.chapterTitle().isBlank()
                    && (c.getChapterTitle() == null || c.getChapterTitle().isBlank())) {
                c.setChapterTitle(scope.chapterTitle().trim());
                prefsChanged = true;
            }
            if (prefsChanged) {
                c.setUpdatedAt(Instant.now());
                return conversationRepository.save(c);
            }
            return c;
        }

        // Deactivate other active conversations for same teacher when starting a different chapter
        // is handled by scope match — we only create new for this scope.
        Instant now = Instant.now();
        LearningConversationEntity c = new LearningConversationEntity();
        c.setTeacherUsername(user);
        c.setGrade(grade);
        c.setSubject(subject);
        c.setMaterialId(materialId);
        c.setChapterTitle(blankToNull(scope.chapterTitle()));
        c.setLanguage(normalizeLanguage(scope.language()));
        c.setExplanationLevel(normalizeLevel(scope.explanationLevel()));
        c.setSummary(null);
        c.setMessageCount(0);
        c.setActive(true);
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return conversationRepository.save(c);
    }

    @Transactional(readOnly = true)
    public ConversationContext buildContext(LearningConversationEntity conversation) {
        int limit = Math.max(2, recentMessageLimit);
        List<LearningMessageEntity> newestFirst =
                messageRepository.findTop20ByConversationIdOrderByCreatedAtDesc(conversation.getId());
        List<LearningMessageEntity> window = newestFirst.size() <= limit
                ? newestFirst
                : newestFirst.subList(0, limit);
        Collections.reverse(window);

        List<ConversationContext.Turn> turns = new ArrayList<>();
        for (LearningMessageEntity m : window) {
            turns.add(new ConversationContext.Turn(m.getRole(), truncate(m.getContent(), 1200)));
        }
        return new ConversationContext(
                conversation.getId(),
                conversation.getSummary(),
                turns,
                conversation.getLanguage(),
                conversation.getExplanationLevel(),
                conversation.getGrade(),
                conversation.getSubject(),
                conversation.getChapterTitle()
        );
    }

    /**
     * Expand short follow-ups for embedding search without putting history into the vector DB.
     */
    public String retrievalQuery(String currentQuestion, ConversationContext ctx) {
        String q = currentQuestion == null ? "" : currentQuestion.trim();
        if (ctx == null || !ctx.hasHistory()) return q;
        StringBuilder sb = new StringBuilder();
        if (ctx.chapterTitle() != null && !ctx.chapterTitle().isBlank()) {
            sb.append("Topic: ").append(ctx.chapterTitle()).append(". ");
        }
        if (ctx.summary() != null && !ctx.summary().isBlank()) {
            sb.append("Context: ").append(truncate(ctx.summary(), 400)).append(". ");
        } else if (ctx.recentTurns() != null && !ctx.recentTurns().isEmpty()) {
            ConversationContext.Turn lastTeacher = null;
            for (int i = ctx.recentTurns().size() - 1; i >= 0; i--) {
                ConversationContext.Turn t = ctx.recentTurns().get(i);
                if (LearningMessageEntity.ROLE_TEACHER.equals(t.role())) {
                    lastTeacher = t;
                    break;
                }
            }
            if (lastTeacher != null) {
                sb.append("Earlier: ").append(truncate(lastTeacher.content(), 200)).append(". ");
            }
        }
        sb.append("Question: ").append(q);
        return sb.toString();
    }

    @Transactional
    public void appendTurn(LearningConversationEntity conversation, String teacherText, String assistantText) {
        Instant now = Instant.now();
        LearningMessageEntity u = new LearningMessageEntity();
        u.setConversationId(conversation.getId());
        u.setRole(LearningMessageEntity.ROLE_TEACHER);
        u.setContent(teacherText == null ? "" : teacherText.trim());
        u.setCreatedAt(now);
        messageRepository.save(u);

        LearningMessageEntity a = new LearningMessageEntity();
        a.setConversationId(conversation.getId());
        a.setRole(LearningMessageEntity.ROLE_ASSISTANT);
        a.setContent(assistantText == null ? "" : assistantText.trim());
        a.setCreatedAt(now.plusMillis(1));
        messageRepository.save(a);

        conversation.setMessageCount(conversation.getMessageCount() + 2);
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        maybeRefreshSummary(conversation);
    }

    private void maybeRefreshSummary(LearningConversationEntity conversation) {
        if (conversation.getMessageCount() < summaryAfterMessages) return;
        // Refresh every few assistant turns after threshold
        if (conversation.getMessageCount() % 4 != 0 && conversation.getSummary() != null) return;
        try {
            List<LearningMessageEntity> all =
                    messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
            StringBuilder transcript = new StringBuilder();
            int start = Math.max(0, all.size() - 16);
            for (int i = start; i < all.size(); i++) {
                LearningMessageEntity m = all.get(i);
                transcript.append(m.getRole()).append(": ").append(truncate(m.getContent(), 500)).append('\n');
            }
            String prompt = """
                    Summarize this classroom teaching conversation for continuity.
                    Capture: current topic, concepts already explained, key people/events/terms,
                    language preference, explanation level, and any open questions.
                    Write 4–8 short sentences. Do not quote every message. No markdown.

                    Chapter: %s
                    Grade: %s Subject: %s
                    Prior summary (may be empty): %s

                    Transcript:
                    %s
                    """.formatted(
                    nullToEmpty(conversation.getChapterTitle()),
                    nullToEmpty(conversation.getGrade()),
                    nullToEmpty(conversation.getSubject()),
                    nullToEmpty(conversation.getSummary()),
                    transcript
            );
            String summary = openAIClient.generateTeachingCompletion(prompt);
            if (summary != null && !summary.isBlank()) {
                conversation.setSummary(truncate(summary.replaceAll("^```[\\w]*\\s*|```$", "").trim(), 2500));
                conversation.setUpdatedAt(Instant.now());
                conversationRepository.save(conversation);
            }
        } catch (Exception e) {
            log.warn("Learning conversation summary refresh skipped: {}", e.getMessage());
        }
    }

    private static String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) return "auto";
        String l = language.trim().toLowerCase(Locale.ROOT);
        if (l.startsWith("hi") || "hindi".equals(l)) return "hi";
        if (l.startsWith("en") || "english".equals(l)) return "en";
        return "auto";
    }

    private static String normalizeLevel(String level) {
        if (level == null || level.isBlank()) return "simple";
        String l = level.trim().toLowerCase(Locale.ROOT);
        if (l.contains("detail") || l.contains("deep")) return "detailed";
        return "simple";
    }

    private static String blankTo(String s, String d) {
        return (s == null || s.isBlank()) ? d : s.trim();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
