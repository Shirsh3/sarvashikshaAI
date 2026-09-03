package com.sarvashikshaai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarvashikshaai.ai.OpenAIClient;
import com.sarvashikshaai.ai.PromptBuilder;
import com.sarvashikshaai.ai.StrictEducationalGuard;
import com.sarvashikshaai.ai.WikipediaClient;
import com.sarvashikshaai.ai.YouTubeClient;
import com.sarvashikshaai.model.ConversationContext;
import com.sarvashikshaai.model.TeachingRequest;
import com.sarvashikshaai.model.TeachingResponse;
import com.sarvashikshaai.model.entity.LearningConversationEntity;
import com.sarvashikshaai.model.entity.QuizExplanationCacheEntity;
import com.sarvashikshaai.repository.QuizExplanationCacheRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class TeachingService {

    private final PromptBuilder promptBuilder;
    private final OpenAIClient openAIClient;
    private final YouTubeClient youTubeClient;
    private final WikipediaClient wikipediaClient;
    private final ObjectMapper objectMapper;
    private final QuizExplanationCacheRepository quizExplainCacheRepo;
    private final EmbeddingSearchService embeddingSearchService;
    private final LearningConversationService learningConversationService;

    @Value("${sarva.learning.rag-top-k:5}")
    private int learningRagTopK;

    @Value("${sarva.learning.min-rag-score:0.42}")
    private double learningMinRagScore;

    public TeachingService(PromptBuilder promptBuilder,
                           OpenAIClient openAIClient,
                           YouTubeClient youTubeClient,
                           WikipediaClient wikipediaClient,
                           ObjectMapper objectMapper,
                           QuizExplanationCacheRepository quizExplainCacheRepo,
                           EmbeddingSearchService embeddingSearchService,
                           LearningConversationService learningConversationService) {
        this.promptBuilder = promptBuilder;
        this.openAIClient = openAIClient;
        this.youTubeClient = youTubeClient;
        this.wikipediaClient = wikipediaClient;
        this.objectMapper = objectMapper;
        this.quizExplainCacheRepo = quizExplainCacheRepo;
        this.embeddingSearchService = embeddingSearchService;
        this.learningConversationService = learningConversationService;
    }

    public TeachingResponse generateExplanation(TeachingRequest request) {
        if (request.getTopic() == null || request.getTopic().isBlank()) {
            return new TeachingResponse(
                    "Please enter a school-related question.",
                    null, null, null, null, null, null, true);
        }
        String mode = request.getClassSessionMode() == null ? "" : request.getClassSessionMode().trim().toLowerCase();
        Long qid = request.getSourceQuestionId();
        if ("quiz".equals(mode) && qid != null) {
            Optional<QuizExplanationCacheEntity> cached = quizExplainCacheRepo.findByQuestionId(qid);
            if (cached.isPresent()) {
                QuizExplanationCacheEntity c = cached.get();
                return new TeachingResponse(
                        c.getExplanation(),
                        c.getExplanationSection(),
                        c.getExampleSection(),
                        c.getKeyPointSection(),
                        c.getVideoId(),
                        c.getWikiGifUrl(),
                        null,
                        false
                );
            }
        }

        long t0 = System.currentTimeMillis();
        OpenAIClient.QueryCategory category = openAIClient.classifyUserQuery(request.getTopic());
        if (category != OpenAIClient.QueryCategory.LEARNING) {
            return new TeachingResponse(
                    StrictEducationalGuard.refusalMessage(),
                    null, null, null, null, null, null, true);
        }
        if (StrictEducationalGuard.isBlocked(request.getTopic())) {
            return new TeachingResponse(
                    StrictEducationalGuard.refusalMessage(),
                    null, null, null, null, null, null, true);
        }

        LearningConversationEntity conversation = null;
        ConversationContext conversationContext = null;
        if (request.isLearningMode()) {
            conversation = learningConversationService.getOrCreateActive(
                    new LearningConversationService.Scope(
                            request.getTeacherUsername(),
                            request.getPrepareGrade(),
                            request.getPrepareSubject(),
                            request.getPrepareMaterialId(),
                            request.getChapterTitle(),
                            request.getLanguage(),
                            request.getExplanationLevel()
                    ));
            conversationContext = learningConversationService.buildContext(conversation);
        }

        String ragQuery = request.getTopic();
        if (conversationContext != null) {
            ragQuery = learningConversationService.retrievalQuery(request.getTopic(), conversationContext);
        }

        String ragContext = "";
        int sourcesUsed = 0;
        try {
            var hits = embeddingSearchService.search(
                    ragQuery,
                    request.getPrepareGrade(),
                    request.getPrepareSubject(),
                    request.getPrepareMaterialId(),
                    request.isLearningMode() ? Math.max(1, learningRagTopK) : 5);
            // Learning + selected chapter: refuse out-of-textbook topics with the same educational-only gate UX.
            if (request.isLearningMode()
                    && request.getPrepareMaterialId() != null
                    && !TextbookCoverageGate.isCovered(request.getTopic(), hits, learningMinRagScore)) {
                return new TeachingResponse(
                        StrictEducationalGuard.notInSelectedChapterMessage(),
                        null, null, null, null, null, null, true);
            }
            sourcesUsed = hits.size();
            ragContext = embeddingSearchService.formatContext(hits);
        } catch (Exception e) {
            log.warn("RAG retrieval skipped: {}", e.getMessage());
        }

        String prompt = promptBuilder.buildUnifiedTeachingPrompt(request, ragContext, conversationContext);
        String llmRaw;
        try {
            llmRaw = openAIClient.generateTeachingCompletion(prompt);
        } finally {
            long dt = System.currentTimeMillis() - t0;
            if (dt > 8000) {
                log.warn("AI explain slow: {} ms (mode={})", dt, request.getClassSessionMode());
            }
        }

        try {
            JsonNode n = objectMapper.readTree(stripCodeFence(llmRaw));
            boolean educational = n.path("educational").asBoolean(true);
            String refusal = n.path("refusal").asText("").strip();

            if (!educational) {
                String msg = refusal.isBlank()
                        ? "We can only show educational content. Please ask a school-related question."
                        : refusal;
                return new TeachingResponse(msg, null, null, null, null, null, null, true);
            }

            String explanationSection = n.path("explanation").asText("").strip();
            String exampleSection = n.path("example").asText("").strip();
            String keyPointSection = n.path("keyPoint").asText("").strip();
            String ytQuery = textOrNull(n.path("youtubeSearchQuery"));
            if (ytQuery == null || ytQuery.isBlank()) {
                ytQuery = request.getTopic().trim() + " explained for students";
            }
            List<String> followUps = parseFollowUps(n.path("followUps"));

            String displayRaw = buildDisplayRaw(explanationSection, exampleSection, keyPointSection);

            String videoId = null;
            String wikiGifUrl = null;
            if (request.isIncludeVideo()) {
                videoId = youTubeClient.fetchVideoId(ytQuery);
            }
            if (!"quiz".equals(mode)) {
                String wikiTopic = request.getTopic().trim();
                wikiGifUrl = (videoId == null) ? wikipediaClient.fetchAnimatedGif(wikiTopic) : null;
            }

            TeachingResponse resp = new TeachingResponse(
                    displayRaw.isBlank() ? llmRaw : displayRaw,
                    nullIfEmpty(explanationSection),
                    nullIfEmpty(exampleSection),
                    nullIfEmpty(keyPointSection),
                    videoId,
                    wikiGifUrl,
                    nullIfEmpty(ytQuery),
                    false);
            resp.setFollowUpSuggestions(followUps);
            resp.setSourcesUsed(sourcesUsed);
            if (sourcesUsed > 0) {
                String chapter = request.getChapterTitle();
                resp.setSourceNote(chapter != null && !chapter.isBlank()
                        ? "Based on your textbook · " + sourcesUsed + " section"
                        + (sourcesUsed == 1 ? "" : "s") + " used"
                        : sourcesUsed + " textbook section" + (sourcesUsed == 1 ? "" : "s") + " used");
            }

            if (conversation != null) {
                String assistantForMemory = buildDisplayRaw(
                        explanationSection,
                        exampleSection,
                        keyPointSection);
                if (assistantForMemory.isBlank()) assistantForMemory = displayRaw;
                learningConversationService.appendTurn(conversation, request.getTopic(), truncate(assistantForMemory, 4000));
                resp.setConversationId(conversation.getId());
            }

            if ("quiz".equals(mode) && qid != null) {
                try {
                    QuizExplanationCacheEntity e = quizExplainCacheRepo.findByQuestionId(qid)
                            .orElseGet(QuizExplanationCacheEntity::new);
                    if (e.getCreatedAt() == null) e.setCreatedAt(Instant.now());
                    e.setUpdatedAt(Instant.now());
                    e.setQuestionId(qid);
                    e.setExplanation(resp.getExplanation());
                    e.setExplanationSection(resp.getExplanationSection());
                    e.setExampleSection(resp.getExampleSection());
                    e.setKeyPointSection(resp.getKeyPointSection());
                    e.setVideoId(resp.getVideoId());
                    e.setWikiGifUrl(resp.getWikiGifUrl());
                    quizExplainCacheRepo.save(e);
                } catch (Exception ex) {
                    log.warn("Quiz explain cache save failed for question {}: {}", qid, ex.getMessage());
                }
            }

            return resp;
        } catch (Exception e) {
            log.warn("Unified teaching JSON parse failed: {}", e.getMessage());
            return new TeachingResponse(
                    "We could not read the answer. Please try asking again in simpler words.",
                    null, null, null, null, null, null, true);
        }
    }

    private static List<String> parseFollowUps(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String t = item.asText("").strip();
                if (!t.isEmpty() && out.size() < 6) out.add(t);
            }
        }
        return out;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String nullIfEmpty(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String buildDisplayRaw(String exp, String ex, String kp) {
        StringBuilder sb = new StringBuilder();
        if (exp != null && !exp.isBlank()) sb.append(exp);
        if (ex != null && !ex.isBlank()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(ex);
        }
        if (kp != null && !kp.isBlank()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(kp);
        }
        return sb.toString();
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        String t = node.asText("").strip();
        if (t.isEmpty() || "null".equalsIgnoreCase(t)) return null;
        return t;
    }

    private static String stripCodeFence(String raw) {
        if (raw == null) return "{}";
        String s = raw.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
        return s.isEmpty() ? "{}" : s;
    }
}
