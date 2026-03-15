package com.sarvashikshaai.controller;

import com.sarvashikshaai.service.TeacherAssistantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Controller
@RequestMapping("/teacher/assistant")
@RequiredArgsConstructor
@Slf4j
public class TeacherAssistantController {

    private final TeacherAssistantService assistantService;

    @GetMapping
    public String assistantPage(@AuthenticationPrincipal OAuth2User principal, Model model) {
        model.addAttribute("history", assistantService.getHistory());
        return "teacher/assistant";
    }

    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, String> generate(
            @RequestParam(defaultValue = "") String templateType,
            @RequestParam String prompt,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        log.info("Teacher assistant: template={} hasFile={}", templateType, file != null && !file.isEmpty());
        String output = assistantService.generate(templateType, prompt, file);
        return Map.of("output", output);
    }
}
