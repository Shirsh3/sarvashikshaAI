package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.ReadingContentItem;
import com.sarvashikshaai.model.entity.ReadingFeedbackRecord;
import com.sarvashikshaai.repository.ReadingFeedbackRepository;
import com.sarvashikshaai.service.ReadingEvaluationService;
import com.sarvashikshaai.service.StudentListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/reading")
@RequiredArgsConstructor
@Slf4j
public class ReadingController {

    private static final List<ReadingContentItem> DEFAULT_READING_CONTENT = List.of(
        new ReadingContentItem(
            "Reading practice",
            "Choose a passage from your textbook or paste text below. Read it aloud and get feedback on fluency and pronunciation.",
            ""
        )
    );

    private final ReadingEvaluationService evaluationService;
    private final StudentListService       studentListService;
    private final ReadingFeedbackRepository feedbackRepo;

    @GetMapping
    public String readingPage(Model model) {
        model.addAttribute("newsList",    DEFAULT_READING_CONTENT);
        model.addAttribute("studentList", studentListService.getStudents());
        model.addAttribute("isTeacher",   true);
        return "reading";
    }

    /**
     * Teacher box: may be a generation prompt or pasted passage. Returns JSON title + passage for the UI.
     */
    @PostMapping("/generate-passage")
    @ResponseBody
    public ResponseEntity<Map<String, String>> generatePassage(@RequestBody(required = false) Map<String, String> body) {
        String prompt = body != null && body.get("prompt") != null ? body.get("prompt").trim() : "";
        if (prompt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "prompt is required"));
        }
        ReadingEvaluationService.GeneratedReadingPassage gen = evaluationService.generateReadingPassage(prompt);
        return ResponseEntity.ok(Map.of(
                "title", gen.title() != null ? gen.title() : "Reading passage",
                "passage", gen.passage() != null ? gen.passage() : prompt));
    }

    @PostMapping("/feedback")
    @ResponseBody
    public ResponseEntity<ReadingEvaluationService.ReadingFeedback> feedback(
            @RequestBody ReadingEvaluationService.ReadingRequest req) {

        ReadingEvaluationService.ReadingFeedback result = evaluationService.evaluateReading(req);

        try {
            ReadingFeedbackRecord record = new ReadingFeedbackRecord(
                req.studentName(),
                req.articleTitle(),
                result.fluencyScore(),
                result.pronunciationScore(),
                result.paceScore(),
                result.accuracyScore(),
                result.confidenceScore(),
                result.originalWordCount(),
                result.spokenWordCount(),
                result.accuracyPercent(),
                result.hindiFeedback(),
                result.englishFeedback(),
                result.comprehensionQuestion(),
                result.difficultWords(),
                result.goodWords(),
                result.improvementTip()
            );
            feedbackRepo.save(record);
            log.info("Saved reading feedback for '{}' — fluency={} accuracy={}% words={}/{}",
                req.studentName(), result.fluencyScore(), result.accuracyPercent(),
                result.spokenWordCount(), result.originalWordCount());
        } catch (Exception e) {
            log.error("Failed to save reading feedback: {}", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }
}
