package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.Student;
import com.sarvashikshaai.model.entity.ReadingFeedbackRecord;
import com.sarvashikshaai.repository.ReadingFeedbackRepository;
import com.sarvashikshaai.repository.TeacherSettingsRepository;
import com.sarvashikshaai.service.AssemblyNewsService;
import com.sarvashikshaai.service.StudentSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/reading")
@RequiredArgsConstructor
@Slf4j
public class ReadingController {

    private final AssemblyNewsService       newsService;
    private final StudentSyncService        syncService;
    private final ReadingFeedbackRepository feedbackRepo;
    private final TeacherSettingsRepository settingsRepo;

    // ── Page ─────────────────────────────────────────────────────────────────

    @GetMapping
    public String readingPage(@AuthenticationPrincipal OAuth2User principal, Model model) {
        String teacherEmail = resolveTeacherEmail(principal);

        model.addAttribute("newsList",    newsService.getReadingContent());
        model.addAttribute("studentList", syncService.getStudents(teacherEmail));
        model.addAttribute("isTeacher",   principal != null);
        return "reading";
    }

    // ── Reading feedback AJAX endpoint ───────────────────────────────────────

    @PostMapping("/feedback")
    @ResponseBody
    public ResponseEntity<AssemblyNewsService.ReadingFeedback> feedback(
            @RequestBody AssemblyNewsService.ReadingRequest req,
            @AuthenticationPrincipal OAuth2User principal) {

        // 1. Get AI evaluation
        AssemblyNewsService.ReadingFeedback result = newsService.evaluateReading(req);

        // 2. Persist full analytics to H2
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
            log.error("Failed to save reading feedback to H2: {}", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String resolveTeacherEmail(OAuth2User principal) {
        if (principal != null) {
            String email = principal.getAttribute("email");
            if (email != null) return email;
        }
        // Single-NGO fallback: use first teacher settings found
        return settingsRepo.findAll().stream()
                .findFirst()
                .map(s -> s.getEmail())
                .orElse("_local_");
    }
}
