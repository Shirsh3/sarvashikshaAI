package com.sarvashikshaai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarvashikshaai.ai.OpenAIClient;
import com.sarvashikshaai.model.entity.TeacherAssistantLog;
import com.sarvashikshaai.repository.TeacherAssistantLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Builds the AI prompt from a teacher's template request + optional uploaded file,
 * calls OpenAI (text or vision), and saves the output to H2.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeacherAssistantService {

    private final OpenAIClient                openAIClient;
    private final FileExtractionService       extractor;
    private final TeacherAssistantLogRepository logRepo;

    @Value("${openai.api-key:}")
    private String apiKeyProperty;

    @Value("${openai.api-base-url:https://api.openai.com/v1}")
    private String apiBaseUrl;

    private final ObjectMapper mapper = new ObjectMapper();

    // ── History ───────────────────────────────────────────────────────────────
    public List<TeacherAssistantLog> getHistory() {
        return logRepo.findTop10ByOrderByCreatedAtDesc();
    }

    // ── Generate (text prompt only) ───────────────────────────────────────────
    public String generate(String templateType, String userPrompt, MultipartFile file) {
        String result;
        String fileName = null;

        if (file != null && !file.isEmpty()) {
            fileName = file.getOriginalFilename();
            FileExtractionService.FileType type = extractor.detectType(file);

            if (type == FileExtractionService.FileType.PDF) {
                String extractedText = extractor.extractPdfText(file);
                result = callTextOpenAI(buildPromptWithContext(templateType, userPrompt, extractedText));

            } else if (type == FileExtractionService.FileType.IMAGE) {
                String base64 = extractor.encodeImageToBase64(file);
                if (base64 != null) {
                    result = callVisionOpenAI(userPrompt, base64);
                } else {
                    result = callTextOpenAI(buildPromptWithContext(templateType, userPrompt, "[Image could not be read]"));
                }
            } else {
                result = "Unsupported file type. Please upload a PDF or an image (JPG, PNG, WEBP).";
            }
        } else {
            result = callTextOpenAI(buildBasePrompt(templateType, userPrompt));
        }

        // Save to log
        String summary = userPrompt.length() > 200 ? userPrompt.substring(0, 197) + "..." : userPrompt;
        logRepo.save(new TeacherAssistantLog(templateType, summary, fileName, result));
        pruneLog();
        return result;
    }

    // ── Prompt builders ───────────────────────────────────────────────────────

    private String buildBasePrompt(String templateType, String userPrompt) {
        String context = switch (templateType == null ? "" : templateType) {
            case "lesson_plan"  -> "You are an experienced school teacher in India. Create a structured lesson plan.";
            case "worksheet"    -> "You are an experienced school teacher. Create a printable class worksheet with clear instructions.";
            case "story"        -> "You are a creative storyteller for school children. Write an engaging story with a moral.";
            case "notice"       -> "You are a school administrator. Write a clear, professional notice or letter to parents.";
            case "activity"     -> "You are a creative school teacher. Suggest fun, educational classroom activities.";
            case "hindi_explain"-> "You are a teacher. Explain the following concept in very simple Hindi that a student aged 8-14 can understand.";
            case "quiz_topic"   -> "You are an experienced school teacher. Generate quiz questions.";
            default             -> "You are a helpful school teacher assistant in India.";
        };
        return context + "\n\nRequest: " + userPrompt
               + "\n\nProvide clear, well-formatted output suitable for classroom use. Use plain text with simple headings.";
    }

    private String buildPromptWithContext(String templateType, String userPrompt, String fileContent) {
        return buildBasePrompt(templateType, userPrompt)
               + "\n\n--- Content from uploaded file ---\n" + fileContent + "\n--- End of file content ---";
    }

    // ── OpenAI calls ──────────────────────────────────────────────────────────

    private String callTextOpenAI(String prompt) {
        try {
            return openAIClient.generateCompletion(prompt);
        } catch (Exception e) {
            log.error("OpenAI text call failed: {}", e.getMessage());
            return "Sorry, could not generate content. Please try again.";
        }
    }

    /**
     * Calls gpt-4o with an image (vision) and a text prompt.
     * Uses WebClient directly since OpenAIClient only handles text.
     */
    private String resolveOpenAiKey() {
        if (apiKeyProperty != null && !apiKeyProperty.isBlank()) return apiKeyProperty.trim();
        String env = System.getenv("OPENAI_API_KEY");
        return env != null ? env : "";
    }

    private String callVisionOpenAI(String prompt, String base64DataUri) {
        try {
            String key = resolveOpenAiKey();
            if (key.isBlank()) throw new IllegalStateException("OpenAI API key not set (openai.api-key or OPENAI_API_KEY)");
            WebClient client = WebClient.builder()
                    .baseUrl(apiBaseUrl)
                    .defaultHeader("Authorization", "Bearer " + key)
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            Map<String, Object> imageUrl   = Map.of("url", base64DataUri);
            Map<String, Object> imagePart  = Map.of("type", "image_url", "image_url", imageUrl);
            Map<String, Object> textPart   = Map.of("type", "text", "text", prompt);
            Map<String, Object> message    = Map.of("role", "user", "content", List.of(textPart, imagePart));
            Map<String, Object> body       = Map.of("model", "gpt-4o", "messages", List.of(message), "max_tokens", 1500);

            String raw = client.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode node = mapper.readTree(raw);
            return node.path("choices").get(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.error("OpenAI vision call failed: {}", e.getMessage());
            return "Sorry, could not analyse the image. Please try again.";
        }
    }

    private void pruneLog() {
        List<TeacherAssistantLog> all = logRepo.findTop10ByOrderByCreatedAtDesc();
        // Keep only the last 10; delete older ones
        long total = logRepo.count();
        if (total > 10) {
            logRepo.findAll().stream()
                    .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .limit(total - 10)
                    .forEach(logRepo::delete);
        }
    }
}
