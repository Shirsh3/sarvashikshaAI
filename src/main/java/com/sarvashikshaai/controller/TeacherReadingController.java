package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.AssemblyNews;
import com.sarvashikshaai.service.AssemblyNewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/teacher/reading")
@RequiredArgsConstructor
@Slf4j
public class TeacherReadingController {

    private final AssemblyNewsService newsService;

    @PostMapping("/generate")
    @ResponseBody
    public ResponseEntity<Map<String, List<AssemblyNews>>> generate(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal OAuth2User principal) {
        String prompt = body != null ? body.get("prompt") : null;
        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        List<AssemblyNews> newsList = newsService.generateFromPrompt(prompt.trim());
        return ResponseEntity.ok(Map.of("newsList", newsList));
    }

    @PostMapping("/fetch-news")
    @ResponseBody
    public ResponseEntity<Map<String, List<AssemblyNews>>> fetchNews(@AuthenticationPrincipal OAuth2User principal) {
        List<AssemblyNews> newsList = newsService.getTechScienceNewsLong();
        return ResponseEntity.ok(Map.of("newsList", newsList));
    }

    @PostMapping("/use-default")
    @ResponseBody
    public ResponseEntity<Map<String, String>> useDefault(@AuthenticationPrincipal OAuth2User principal) {
        newsService.clearReadingOverride();
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
