package com.sarvashikshaai.controller;

import com.sarvashikshaai.service.AssemblyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lazy-loads Thought of the Day so {@code GET /assembly} returns quickly while
 * {@link com.sarvashikshaai.service.ThoughtConfigService#ensureThoughtPool()} may call OpenAI.
 */
@RestController
@RequestMapping("/api/assembly")
@RequiredArgsConstructor
public class AssemblyThoughtApiController {

    private final AssemblyService assemblyService;

    @GetMapping("/daily-thought")
    public Map<String, String> dailyThought() {
        AssemblyService.DailyThought t = assemblyService.getDailyThought(null);
        Map<String, String> out = new LinkedHashMap<>();
        out.put("thoughtHi", nullToEmpty(t.thoughtHi()));
        out.put("thoughtEn", nullToEmpty(t.thoughtEn()));
        out.put("wordEnglish", nullToEmpty(t.wordEnglish()));
        out.put("wordHindi", nullToEmpty(t.wordHindi()));
        out.put("habit", nullToEmpty(t.habit()));
        return out;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
