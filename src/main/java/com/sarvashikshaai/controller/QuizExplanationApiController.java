package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.TeachingRequest;
import com.sarvashikshaai.model.TeachingResponse;
import com.sarvashikshaai.model.entity.QuizQuestionEntity;
import com.sarvashikshaai.repository.QuizQuestionRepository;
import com.sarvashikshaai.service.TeachingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class QuizExplanationApiController {

    private final QuizQuestionRepository quizQuestionRepository;
    private final TeachingService teachingService;

    @GetMapping("/explanation/{questionId}")
    public ResponseEntity<Map<String, Object>> getExplanation(
            @PathVariable Long questionId,
            @RequestParam(name = "includeVideo", defaultValue = "false") boolean includeVideo,
            @RequestParam(name = "grade", defaultValue = "") String grade
    ) {
        try {
            QuizQuestionEntity q = quizQuestionRepository.findById(questionId).orElse(null);
            if (q == null) {
                // Return 200 so the UI can show the message without console "400".
                return ResponseEntity.ok(Map.of(
                        "error", "Question not found.",
                        "questionId", questionId
                ));
            }

            TeachingRequest req = new TeachingRequest();
            req.setClassSessionMode("quiz");
            req.setSourceQuestionId(questionId);
            req.setIncludeVideo(includeVideo);
            req.setPrepareGrade(grade == null ? "" : grade.trim());
            req.setPrepareFocus("");

            String questionText = q.getQuestionText() == null ? "" : q.getQuestionText().trim();
            if (questionText.isBlank()) {
                req.setTopic("Explain this question for a student.");
            } else {
                // Keep topic clean so classification reliably treats it as educational.
                req.setTopic(questionText);
            }

            TeachingResponse resp = teachingService.generateExplanation(req);
            if (resp == null) {
                return ResponseEntity.ok(Map.of(
                        "error", "Unable to load explanation.",
                        "questionId", questionId
                ));
            }
            // For quiz flow, even blocked content should still show a friendly message in-panel.
            // So return 200 with the message instead of 400 which breaks the UX.

            Map<String, Object> out = new HashMap<>();
            out.put("explanation", resp.getExplanation());
            out.put("explanationSection", resp.getExplanationSection());
            out.put("exampleSection", resp.getExampleSection());
            out.put("keyPointSection", resp.getKeyPointSection());
            out.put("videoId", resp.getVideoId());       // may be null
            out.put("wikiGifUrl", resp.getWikiGifUrl()); // may be null
            out.put("youtubeSearchQuery", resp.getYoutubeSearchQuery());
            out.put("nonEducational", resp.isNonEducational());
            out.put("questionId", questionId);
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            log.warn("Quiz explanation API failed for questionId={}", questionId, e);
            // Return 200 so the UI can render the error cleanly.
            return ResponseEntity.ok(Map.of(
                    "error", "Could not load explanation. Please try again.",
                    "questionId", questionId,
                    "exception", e.getClass().getSimpleName()
            ));
        }
    }
}

