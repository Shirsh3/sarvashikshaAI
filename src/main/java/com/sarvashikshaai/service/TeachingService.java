package com.sarvashikshaai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarvashikshaai.ai.OpenAIClient;
import com.sarvashikshaai.ai.PromptBuilder;
import com.sarvashikshaai.ai.WikipediaClient;
import com.sarvashikshaai.ai.YouTubeClient;
import com.sarvashikshaai.model.TeachingRequest;
import com.sarvashikshaai.model.TeachingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TeachingService {

    private final PromptBuilder promptBuilder;
    private final OpenAIClient openAIClient;
    private final YouTubeClient youTubeClient;
    private final WikipediaClient wikipediaClient;
    private final ObjectMapper objectMapper;

    public TeachingService(PromptBuilder promptBuilder,
                           OpenAIClient openAIClient,
                           YouTubeClient youTubeClient,
                           WikipediaClient wikipediaClient,
                           ObjectMapper objectMapper) {
        this.promptBuilder = promptBuilder;
        this.openAIClient = openAIClient;
        this.youTubeClient = youTubeClient;
        this.wikipediaClient = wikipediaClient;
        this.objectMapper = objectMapper;
    }

    public TeachingResponse generateExplanation(TeachingRequest request) {
        if (request.getTopic() == null || request.getTopic().isBlank()) {
            return new TeachingResponse(
                    "Please enter a school-related question.",
                    null, null, null, null, null, true);
        }

        String prompt = promptBuilder.buildUnifiedTeachingPrompt(request);
        String llmRaw = openAIClient.generateTeachingCompletion(prompt);

        try {
            JsonNode n = objectMapper.readTree(stripCodeFence(llmRaw));
            boolean educational = n.path("educational").asBoolean(true);
            String refusal = n.path("refusal").asText("").strip();

            if (!educational) {
                String msg = refusal.isBlank()
                        ? "We can only show educational content. Please ask a school-related question."
                        : refusal;
                return new TeachingResponse(msg, null, null, null, null, null, true);
            }

            String explanationSection = n.path("explanation").asText("").strip();
            String exampleSection = n.path("example").asText("").strip();
            String keyPointSection = n.path("keyPoint").asText("").strip();
            String ytQuery = textOrNull(n.path("youtubeSearchQuery"));
            if (ytQuery == null || ytQuery.isBlank()) {
                ytQuery = request.getTopic().trim() + " explained for students";
            }

            String displayRaw = buildDisplayRaw(explanationSection, exampleSection, keyPointSection);

            String videoId = youTubeClient.fetchVideoId(ytQuery);
            String wikiTopic = request.getTopic().trim();
            String wikiGifUrl = (videoId == null) ? wikipediaClient.fetchAnimatedGif(wikiTopic) : null;

            return new TeachingResponse(
                    displayRaw.isBlank() ? llmRaw : displayRaw,
                    nullIfEmpty(explanationSection),
                    nullIfEmpty(exampleSection),
                    nullIfEmpty(keyPointSection),
                    videoId,
                    wikiGifUrl,
                    false);
        } catch (Exception e) {
            log.warn("Unified teaching JSON parse failed: {}", e.getMessage());
            return new TeachingResponse(
                    "We could not read the answer. Please try asking again in simpler words.",
                    null, null, null, null, null, true);
        }
    }

    private static String nullIfEmpty(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String buildDisplayRaw(String exp, String ex, String kp) {
        StringBuilder sb = new StringBuilder();
        if (exp != null && !exp.isBlank()) sb.append(exp);
        if (ex != null && !ex.isBlank()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(ex);
        }
        if (kp != null && !kp.isBlank()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(kp);
        }
        return sb.toString();
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        String t = node.asText("").strip();
        if (t.isEmpty() || "null".equalsIgnoreCase(t)) return null;
        return t;
    }

    private static String stripCodeFence(String raw) {
        if (raw == null) return "{}";
        String s = raw.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
        return s.isEmpty() ? "{}" : s;
    }
}
