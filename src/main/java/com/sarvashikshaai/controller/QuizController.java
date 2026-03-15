package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.entity.QuizEntity;
import com.sarvashikshaai.repository.TeacherSettingsRepository;
import com.sarvashikshaai.service.FileExtractionService;
import com.sarvashikshaai.service.QuizService;
import com.sarvashikshaai.service.StudentSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/quiz")
@RequiredArgsConstructor
@Slf4j
public class QuizController {

    private final QuizService               quizService;
    private final StudentSyncService        syncService;
    private final FileExtractionService     extractor;
    private final TeacherSettingsRepository settingsRepo;

    // ── Teacher dashboard ─────────────────────────────────────────────────────

    @GetMapping("/teacher")
    public String teacherDashboard(@AuthenticationPrincipal OAuth2User principal, Model model) {
        String email = resolveEmail(principal);
        model.addAttribute("quizList",     quizService.listAll());
        model.addAttribute("studentList",  syncService.getStudents(email));
        return "quiz/teacher";
    }

    // ── Create quiz ───────────────────────────────────────────────────────────

    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createQuiz(
            @RequestParam String title,
            @RequestParam(defaultValue = "") String subject,
            @RequestParam(defaultValue = "") String grade,
            @RequestParam String questionsJson) {
        try {
            QuizEntity saved = quizService.save(title, subject, grade, questionsJson);
            return ResponseEntity.ok(Map.of("id", saved.getId(), "title", saved.getTitle(), "count", saved.getQuestionCount()));
        } catch (Exception e) {
            log.error("Save quiz failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── AI generate questions ─────────────────────────────────────────────────

    @PostMapping("/ai-generate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> aiGenerate(
            @RequestParam(required = false, defaultValue = "") String topic,
            @RequestParam(defaultValue = "5") int count,
            @RequestParam(defaultValue = "MCQ, TF") String types,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        try {
            String contextText = topic != null ? topic.trim() : "";

            if (file != null && !file.isEmpty()) {
                FileExtractionService.FileType ft = extractor.detectType(file);
                if (ft == FileExtractionService.FileType.PDF) {
                    String pdfText = extractor.extractPdfText(file);
                    contextText = contextText.isEmpty() ? pdfText : contextText + "\n\nFile content:\n" + pdfText;
                } else if (ft == FileExtractionService.FileType.IMAGE) {
                    contextText = contextText.isEmpty() ? "Generate questions from the provided image" : contextText;
                }
            }

            if (contextText.isEmpty()) {
                return ResponseEntity.ok(Map.of("questionsJson", "[]", "error", "Please enter a topic or upload a PDF/image."));
            }

            String json = quizService.generateQuestionsJson(contextText, count, types != null ? types : "MCQ, TF");
            return ResponseEntity.ok(Map.of("questionsJson", json != null ? json : "[]"));
        } catch (Exception e) {
            log.error("AI generate questions failed", e);
            return ResponseEntity.ok(Map.of("questionsJson", "[]", "error", e.getMessage() != null ? e.getMessage() : "Generation failed. Please try again."));
        }
    }

    // ── Delete quiz ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> deleteQuiz(@PathVariable Long id) {
        quizService.delete(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    // ── Student: take a quiz ─────────────────────────────────────────────────

    @GetMapping("/take/{id}")
    public String takeQuiz(@PathVariable Long id,
                           @AuthenticationPrincipal OAuth2User principal,
                           Model model) {
        return quizService.findById(id).map(quiz -> {
            String email = resolveEmail(principal);
            model.addAttribute("quiz",        quiz);
            model.addAttribute("studentList", syncService.getStudents(email));
            return "quiz/take";
        }).orElse("redirect:/quiz/teacher");
    }

    // ── Submit quiz ───────────────────────────────────────────────────────────

    @PostMapping("/submit")
    @ResponseBody
    public ResponseEntity<Object> submitQuiz(@RequestBody QuizService.SubmitRequest req) {
        log.info("Quiz submit request received: quizId={}, studentName={}", req.quizId(), req.studentName());
        try {
            QuizService.SubmitResult result = quizService.gradeAndSave(req);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Submit quiz failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Results for a quiz (all attempts by all students; explicit array in body) ─

    @GetMapping(value = "/{id}/results", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> quizResults(@PathVariable Long id) {
        java.util.List<com.sarvashikshaai.model.entity.QuizResultEntity> list = quizService.getResultsByQuiz(id);
        log.info("Quiz {} results: {} attempt(s)", id, list.size());
        return ResponseEntity.ok(Map.of("results", new java.util.ArrayList<>(list)));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String resolveEmail(OAuth2User principal) {
        if (principal != null) {
            String e = principal.getAttribute("email");
            if (e != null) return e;
        }
        return settingsRepo.findAll().stream().findFirst().map(s -> s.getEmail()).orElse("_local_");
    }
}
