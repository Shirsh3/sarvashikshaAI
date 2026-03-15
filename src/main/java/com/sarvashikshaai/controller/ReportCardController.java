package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.entity.StudentReport;
import com.sarvashikshaai.repository.TeacherSettingsRepository;
import com.sarvashikshaai.service.ReportCardService;
import com.sarvashikshaai.service.StudentSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/teacher/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportCardController {

    private final ReportCardService         reportService;
    private final StudentSyncService        syncService;
    private final TeacherSettingsRepository settingsRepo;

    @GetMapping
    public String reportsPage(@AuthenticationPrincipal OAuth2User principal, Model model) {
        String email = resolveEmail(principal);
        model.addAttribute("studentList", syncService.getStudents(email));
        return "teacher/reports";
    }

    @PostMapping("/generate")
    @ResponseBody
    public ResponseEntity<Object> generate(
            @RequestParam String studentName,
            @RequestParam String termLabel,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        try {
            StudentReport report = reportService.generateOrLoad(studentName, termLabel, fromDate, toDate);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Report generation failed for {}: {}", studentName, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not generate report. " + e.getMessage()));
        }
    }

    @DeleteMapping("/invalidate")
    @ResponseBody
    public ResponseEntity<Map<String, String>> invalidate(
            @RequestParam String studentName,
            @RequestParam String termLabel) {
        reportService.invalidate(studentName, termLabel);
        return ResponseEntity.ok(Map.of("status", "invalidated"));
    }

    @GetMapping("/history")
    @ResponseBody
    public ResponseEntity<List<StudentReport>> history(@RequestParam String studentName) {
        return ResponseEntity.ok(reportService.getHistory(studentName));
    }

    private String resolveEmail(OAuth2User principal) {
        if (principal != null) {
            String e = principal.getAttribute("email");
            if (e != null) return e;
        }
        return settingsRepo.findAll().stream().findFirst().map(s -> s.getEmail()).orElse("_local_");
    }
}
