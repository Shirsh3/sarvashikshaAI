package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.TeachingRequest;
import com.sarvashikshaai.model.TeachingResponse;
import com.sarvashikshaai.service.TeachingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON API for Learning teaching conversations (context + Neon RAG).
 */
@RestController
@RequestMapping("/api/learning")
@RequiredArgsConstructor
public class LearningAskController {

    private final TeachingService teachingService;

    public record AskBody(
            String question,
            String grade,
            String subject,
            Long materialId,
            String chapterTitle,
            String language,
            String explanationLevel,
            Long conversationId
    ) {}

    @PostMapping("/ask")
    public ResponseEntity<?> ask(@RequestBody AskBody body) {
        String q = body == null || body.question() == null ? "" : body.question().trim();
        if (q.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Question is required"));
        }

        TeachingRequest req = new TeachingRequest();
        req.setTopic(q);
        req.setLearningMode(true);
        req.setClassSessionMode("live");
        req.setPrepareFocus("revision");
        req.setPrepareGrade(blankToNull(body.grade()));
        req.setPrepareSubject(blankToNull(body.subject()));
        req.setPrepareMaterialId(body.materialId());
        req.setChapterTitle(blankToNull(body.chapterTitle()));
        req.setLanguage(blankToNull(body.language()));
        req.setExplanationLevel(blankToNull(body.explanationLevel()));
        req.setLearningConversationId(body.conversationId());
        req.setTeacherUsername(currentUsername());
        req.setIncludeVideo(false);

        TeachingResponse resp = teachingService.generateExplanation(req);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("conversationId", resp.getConversationId());
        out.put("nonEducational", resp.isNonEducational());
        out.put("explanation", resp.getExplanation());
        out.put("explanationSection", resp.getExplanationSection());
        out.put("exampleSection", resp.getExampleSection());
        out.put("keyPointSection", resp.getKeyPointSection());
        out.put("youtubeSearchQuery", resp.getYoutubeSearchQuery());
        out.put("followUps", resp.getFollowUpSuggestions() == null ? List.of() : resp.getFollowUpSuggestions());
        out.put("sourceNote", resp.getSourceNote());
        out.put("sourcesUsed", resp.getSourcesUsed());
        out.put("grade", req.getPrepareGrade());
        out.put("subject", req.getPrepareSubject());
        out.put("chapterTitle", req.getChapterTitle());
        return ResponseEntity.ok(out);
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return "unknown";
        return auth.getName();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
