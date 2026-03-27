package com.sarvashikshaai.ai;

import com.sarvashikshaai.model.dto.OpenAiChatRequest;
import com.sarvashikshaai.model.dto.OpenAiChatResponse;
import com.sarvashikshaai.model.dto.OpenAiMessage;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.List;
import java.util.Locale;

@Component
public class OpenAIClient {

    private static final double STRICT_TEMPERATURE = 0.3;
    private static final double STRICT_TOP_P = 0.3;

    private final WebClient webClient;
    private final String teachingModel;
    private final String assemblyModel;
    private final String quizModel;

    public OpenAIClient(@Qualifier("openAiWebClient") WebClient webClient,
                        @Qualifier("openAiTeachingModel") String teachingModel,
                        @Qualifier("openAiAssemblyModel") String assemblyModel,
                        @Qualifier("openAiQuizModel") String quizModel) {
        this.webClient = webClient;
        this.teachingModel = teachingModel;
        this.assemblyModel = assemblyModel;
        this.quizModel = quizModel;
    }

    public String generateTeachingCompletion(String prompt) {
        return generateWithModel(prompt, teachingModel);
    }

    public String generateAssemblyCompletion(String prompt) {
        return generateWithModel(prompt, assemblyModel);
    }

    public String generateQuizCompletion(String prompt) {
        return generateWithModel(prompt, quizModel);
    }

    public enum QueryCategory {
        LEARNING,
        OFF_TOPIC,
        UNSAFE
    }

    /**
     * Runs a separate classification-only model call before downstream processing.
     */
    public QueryCategory classifyUserQuery(String userQuery) {
        String query = userQuery == null ? "" : userQuery.trim();
        String prompt = """
                Classify the user query into one of these categories:
                - LEARNING (school-related, educational)
                - OFF_TOPIC (not related to studies, casual, entertainment)
                - UNSAFE (harmful, abusive, adult, illegal)

                Respond with ONLY one word: LEARNING or OFF_TOPIC or UNSAFE.

                Query: "%s"
                """.formatted(query.replace("\"", "\\\""));
        try {
            String raw = generateWithModel(prompt, teachingModel);
            String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if (normalized.contains("UNSAFE")) return QueryCategory.UNSAFE;
            if (normalized.contains("OFF_TOPIC")) return QueryCategory.OFF_TOPIC;
            if (normalized.contains("LEARNING")) return QueryCategory.LEARNING;
            return QueryCategory.OFF_TOPIC;
        } catch (Exception ex) {
            return QueryCategory.OFF_TOPIC;
        }
    }

    /**
     * Multimodal call for quiz generation from screenshot/image.
     * Uses chat/completions content blocks (text + image_url).
     */
    public String generateQuizCompletionFromImage(String prompt, String imageDataUri) {
        Map<String, Object> request = Map.of(
                "model", quizModel,
                "temperature", STRICT_TEMPERATURE,
                "top_p", STRICT_TOP_P,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of("type", "text", "text", prompt),
                                        Map.of("type", "image_url", "image_url", Map.of("url", imageDataUri))
                                )
                        )
                )
        );

        JsonNode response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(ex -> Mono.error(new IllegalStateException("Failed to call OpenAI Vision API", ex)))
                .block();

        if (response == null
                || response.path("choices").isMissingNode()
                || !response.path("choices").isArray()
                || response.path("choices").isEmpty()) {
            throw new IllegalStateException("OpenAI Vision API returned no choices");
        }

        String text = response.path("choices").get(0).path("message").path("content").asText("");
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("OpenAI Vision API returned empty content");
        }
        return text.trim();
    }

    /** Backwards-compatible default: use teaching model. */
    public String generateCompletion(String prompt) {
        return generateTeachingCompletion(prompt);
    }

    private String generateWithModel(String prompt, String model) {
        OpenAiChatRequest request = new OpenAiChatRequest(
                model,
                List.of(new OpenAiMessage("user", prompt)),
                STRICT_TEMPERATURE,
                STRICT_TOP_P
        );

        OpenAiChatResponse response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OpenAiChatResponse.class)
                .onErrorResume(ex -> Mono.error(new IllegalStateException("Failed to call OpenAI API", ex)))
                .block();

        if (response == null || response.choices == null || response.choices.isEmpty()) {
            throw new IllegalStateException("OpenAI API returned no choices");
        }

        return response.choices.get(0).message.content().trim();
    }
}
