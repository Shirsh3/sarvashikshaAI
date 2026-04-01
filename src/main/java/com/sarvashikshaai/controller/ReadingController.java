package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.ReadingContentItem;
import com.sarvashikshaai.model.dto.ReadingSessionSummaryDto;
import com.sarvashikshaai.model.entity.ReadingFeedbackRecord;
import com.sarvashikshaai.repository.ReadingFeedbackRepository;
import com.sarvashikshaai.service.ReadingEvaluationService;
import com.sarvashikshaai.service.StudentListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
            "Open the Textbook tab for mic-only reading, or the Class passage tab for a shared AI or pasted passage.",
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
     * Teacher asked to avoid exposing AI feedback text here. This is a neutral summary
     * built only from title + who + numeric metrics.
     */
    private static String nonFeedbackSummary(ReadingFeedbackRecord r) {
        String title = r.getArticleTitle() != null && !r.getArticleTitle().isBlank()
                ? r.getArticleTitle().trim()
                : "Reading session";
        String who = r.getStudentName() != null && !r.getStudentName().isBlank()
                ? r.getStudentName().trim()
                : "Unknown student";

        StringBuilder sb = new StringBuilder();
        sb.append(title).append(". ");
        sb.append("Read by ").append(who).append(". ");

        boolean any = false;
        any = appendMetric(sb, any, "Fluency", r.getFluencyScore(), "/10");
        any = appendMetric(sb, any, "Pronunciation", r.getPronunciationScore(), "/10");
        any = appendMetric(sb, any, "Pace", r.getPaceScore(), "/10");
        any = appendMetric(sb, any, "Accuracy", r.getAccuracyScore(), "/10");
        any = appendMetric(sb, any, "Confidence", r.getConfidenceScore(), "/10");
        any = appendMetric(sb, any, "Word match", r.getAccuracyPercent(), "%");

        Integer spoken = r.getSpokenWordCount();
        Integer orig = r.getOriginalWordCount();
        if (spoken != null || orig != null) {
            if (any) sb.append(". ");
            sb.append("Words: ");
            if (spoken != null) sb.append(spoken).append(" spoken");
            if (orig != null) sb.append(spoken != null ? " vs " : "").append(orig).append(" in passage");
            sb.append(".");
        }

        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    private static boolean appendMetric(StringBuilder sb, boolean anyAlready, String label, Integer v, String suffix) {
        if (v == null) return anyAlready;
        if (!anyAlready) sb.append("Scores: ");
        else sb.append(", ");
        sb.append(label).append(" ").append(v).append(suffix);
        return true;
    }

    /** At most {@code maxWords} words. */
    private static String firstWords(String text, int maxWords) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] parts = text.trim().split("\\s+");
        if (parts.length <= maxWords) {
            return String.join(" ", parts);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxWords; i++) {
            if (i > 0) sb.append(' ');
            sb.append(parts[i]);
        }
        sb.append("…");
        return sb.toString();
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
        String grade = body != null && body.get("grade") != null ? body.get("grade").trim() : "";
        ReadingEvaluationService.GeneratedReadingPassage gen = evaluationService.generateReadingPassage(prompt, grade.isEmpty() ? null : grade);
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
