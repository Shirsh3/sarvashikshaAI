package com.sarvashikshaai.controller;

import com.sarvashikshaai.ai.OpenAIClient;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Personal assistant JSON API (Alexa skill backend companion).
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class PersonalAskController {

    private static final String SYSTEM_HINT = """
            You are Sarvasiksha AI, a helpful personal voice assistant.
            Answer clearly for an adult user.
            Keep answers short enough to speak aloud (about 2-4 short sentences) unless they ask for more detail.
            Reply in Hindi if the question is in Hindi; otherwise use clear English.
            """;

    private final OpenAIClient openAIClient;

    public PersonalAskController(OpenAIClient openAIClient) {
        this.openAIClient = openAIClient;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("service", "sarvashiksha-ai");
        String key = System.getenv("OPENAI_API_KEY");
        body.put("openaiConfigured", key != null && !key.isBlank());
        return body;
    }

    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> ask(@RequestBody @Validated AskBody request) {
        String locale = request.locale() == null ? "" : request.locale().trim();
        String prompt = SYSTEM_HINT
                + (locale.isBlank() ? "" : "\nLocale hint: " + locale + "\n")
                + "\nUser question:\n"
                + request.question().trim();
        String answer = openAIClient.generateCompletion(prompt);
        return ResponseEntity.ok(Map.of("answer", answer));
    }

    public record AskBody(
            @NotBlank String question,
            String locale
    ) {}
}
