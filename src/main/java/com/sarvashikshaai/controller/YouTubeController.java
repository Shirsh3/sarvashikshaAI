package com.sarvashikshaai.controller;

import com.sarvashikshaai.ai.YouTubeClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class YouTubeController {

    private final YouTubeClient youTubeClient;

    @GetMapping("/api/youtube/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> search(@RequestParam("q") String q) {
        if (q == null || q.trim().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing query."));
        }
        String query = q.trim();
        String videoId = youTubeClient.fetchVideoId(query);
        return ResponseEntity.ok(Map.of("videoId", videoId));
    }
}

