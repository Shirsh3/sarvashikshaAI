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

@Component
public class OpenAIClient {

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

    /**
     * Multimodal call for quiz generation from screenshot/image.
     * Uses chat/completions content blocks (text + image_url).
     */
    public String generateQuizCompletionFromImage(String prompt, String imageDataUri) {
        Map<String, Object> request = Map.of(
                "model", quizModel,
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
                List.of(new OpenAiMessage("user", prompt))
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
