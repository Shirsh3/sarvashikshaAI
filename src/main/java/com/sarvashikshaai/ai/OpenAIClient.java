package com.sarvashikshaai.ai;

import com.sarvashikshaai.model.dto.OpenAiChatRequest;
import com.sarvashikshaai.model.dto.OpenAiChatResponse;
import com.sarvashikshaai.model.dto.OpenAiMessage;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@Slf4j
public class OpenAIClient {

    private static final double STRICT_TEMPERATURE = 0.3;
    private static final double STRICT_TOP_P = 0.3;

    private final WebClient webClient;
    private final String teachingModel;
    private final String assemblyModel;
    private final String quizModel;
    private final String quizCoverModel;
    private final String quizCoverImageSize;

    public OpenAIClient(@Qualifier("openAiWebClient") WebClient webClient,
                        @Qualifier("openAiTeachingModel") String teachingModel,
                        @Qualifier("openAiAssemblyModel") String assemblyModel,
                        @Qualifier("openAiQuizModel") String quizModel,
                        @Value("${openai.model.quiz-cover:dall-e-3}") String quizCoverModel,
                        @Value("${openai.quiz.cover-image.size:1792x1024}") String quizCoverImageSize) {
        this.webClient = webClient;
        this.teachingModel = teachingModel;
        this.assemblyModel = assemblyModel;
        this.quizModel = quizModel;
        this.quizCoverModel = quizCoverModel;
        this.quizCoverImageSize = quizCoverImageSize;
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

                IMPORTANT:
                - If the query is a school subject/topic/question, quiz, homework, exam, lesson, chapter, or any curriculum learning request,
                  classify as LEARNING even if it is short, vague, or missing details.
                - Treat names of topics (e.g. "photosynthesis", "fractions", "Mughal empire", "Miss World") as LEARNING.
                - Only use OFF_TOPIC for clearly non-educational requests (jokes, flirting, movies unrelated to study, etc.).

                Respond with ONLY one word: LEARNING or OFF_TOPIC or UNSAFE.

                Query: "%s"
                """.formatted(query.replace("\"", "\\\""));
        try {
            String raw = generateWithModel(prompt, teachingModel);
            String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if (normalized.contains("UNSAFE")) return QueryCategory.UNSAFE;
            if (normalized.contains("OFF_TOPIC")) return QueryCategory.OFF_TOPIC;
            if (normalized.contains("LEARNING")) return QueryCategory.LEARNING;
            return QueryCategory.LEARNING;
        } catch (Exception ex) {
            return QueryCategory.LEARNING;
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

    /**
     * OpenAI Images API — educational quiz cover. Empty if the API fails or returns no URL.
     */
    public Optional<String> generateQuizCoverImageUrl(String topic, String grade) {
        String t = topic != null ? topic.trim() : "General";
        String g = grade != null ? grade.trim() : "";
        String prompt = """
                Flat, friendly educational illustration for a school quiz cover — no text, no letters, no numbers in the image.
                Topic hint: %s. Audience: Indian school students%s.
                Bright, classroom-safe, abstract or symbolic only — no real people faces, no logos, no brand names.
                """.formatted(
                t.replace("\"", "'"),
                g.isBlank() ? "" : (" — grade band: " + g));

        Map<String, Object> body = new HashMap<>();
        body.put("model", quizCoverModel);
        body.put("prompt", prompt.trim());
        body.put("n", 1);
        body.put("size", quizCoverImageSize);
        body.put("response_format", "url");

        try {
            JsonNode response = webClient.post()
                    .uri("/images/generations")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .onErrorResume(ex -> {
                        log.warn("OpenAI images API error: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .block();
            if (response == null || !response.path("data").isArray() || response.path("data").isEmpty()) {
                return Optional.empty();
            }
            String url = response.path("data").get(0).path("url").asText("");
            if (url == null || url.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(url.trim());
        } catch (Exception e) {
            log.warn("Quiz cover image generation failed: {}", e.getMessage());
            return Optional.empty();
        }
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
