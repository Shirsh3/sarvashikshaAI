package com.sarvashikshaai.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarvashikshaai.ai.OpenAIClient;
import com.sarvashikshaai.model.dto.OpenAiMessage;
import com.sarvashikshaai.security.AlexaSkillIdValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Alexa Custom Skill endpoint. Point the skill HTTPS URL at POST /alexa.
 * Conversation continuity uses Alexa {@code sessionAttributes.historyJson}.
 */
@RestController
public class AlexaController {

    private static final String SYSTEM_HINT = """
            You are Sarvasiksha AI, a helpful personal voice assistant.
            Answer clearly for an adult user.
            Keep answers short enough to speak aloud (about 2-4 short sentences) unless they ask for more detail.
            Reply in Hindi if the question is in Hindi; otherwise use clear English.
            Use prior turns in this conversation for follow-up questions (e.g. "explain that", "make it shorter").
            """;

    /** Keep last N messages (user+assistant pairs) inside Alexa session size limits. */
    private static final int MAX_HISTORY_MESSAGES = 12;

    private final OpenAIClient openAIClient;
    private final AlexaSkillIdValidator skillIdValidator;
    private final ObjectMapper objectMapper;

    public AlexaController(
            OpenAIClient openAIClient,
            AlexaSkillIdValidator skillIdValidator,
            ObjectMapper objectMapper
    ) {
        this.openAIClient = openAIClient;
        this.skillIdValidator = skillIdValidator;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/alexa", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handle(@RequestBody JsonNode body) {
        if (!skillIdValidator.isAllowed(body)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "message", "Invalid Alexa skill id"));
        }

        String type = body.path("request").path("type").asText("");
        String locale = body.path("request").path("locale").asText("en-IN");
        List<OpenAiMessage> history = readHistory(body.path("session").path("attributes"));

        if ("LaunchRequest".equals(type)) {
            // Fresh chat when skill opens
            return ResponseEntity.ok(alexaSpeak(
                    "Hi. I am Sarvasiksha AI. What can I help you with?",
                    false,
                    List.of()));
        }

        if ("SessionEndedRequest".equals(type)) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("version", "1.0");
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("shouldEndSession", true);
            response.put("response", inner);
            return ResponseEntity.ok(response);
        }

        if ("IntentRequest".equals(type)) {
            String intentName = body.path("request").path("intent").path("name").asText("");
            if ("AMAZON.StopIntent".equals(intentName) || "AMAZON.CancelIntent".equals(intentName)) {
                return ResponseEntity.ok(alexaSpeak("Goodbye.", true, List.of()));
            }
            if ("AMAZON.HelpIntent".equals(intentName)) {
                return ResponseEntity.ok(alexaSpeak(
                        "Ask me anything. For follow-ups, just ask again while I am still open. Say stop to finish.",
                        false,
                        history));
            }

            String question = extractQuestion(body);
            if (question == null || question.isBlank()) {
                return ResponseEntity.ok(alexaSpeak(
                        "I did not catch the question. Please ask again.",
                        false,
                        history));
            }
            try {
                List<OpenAiMessage> messages = new ArrayList<>();
                messages.add(new OpenAiMessage("system", SYSTEM_HINT + "\nLocale hint: " + locale));
                messages.addAll(history);
                messages.add(new OpenAiMessage("user", question.trim()));

                String answer = openAIClient.generateChatCompletion(messages);

                List<OpenAiMessage> updated = new ArrayList<>(history);
                updated.add(new OpenAiMessage("user", question.trim()));
                updated.add(new OpenAiMessage("assistant", answer));
                updated = trimHistory(updated);

                return ResponseEntity.ok(alexaSpeak(answer, false, updated));
            } catch (Exception e) {
                return ResponseEntity.ok(alexaSpeak(
                        "Sorry, I could not answer right now. Please try again.",
                        false,
                        history));
            }
        }

        return ResponseEntity.ok(alexaSpeak("Sorry, I did not understand that.", false, history));
    }

    private List<OpenAiMessage> readHistory(JsonNode attributes) {
        if (attributes == null || attributes.isMissingNode() || !attributes.hasNonNull("historyJson")) {
            return new ArrayList<>();
        }
        try {
            String json = attributes.path("historyJson").asText("");
            if (json.isBlank()) {
                return new ArrayList<>();
            }
            List<Map<String, String>> raw = objectMapper.readValue(json, new TypeReference<>() {});
            List<OpenAiMessage> out = new ArrayList<>();
            for (Map<String, String> row : raw) {
                String role = row.get("role");
                String content = row.get("content");
                if (role != null && content != null && !content.isBlank()
                        && ("user".equals(role) || "assistant".equals(role))) {
                    out.add(new OpenAiMessage(role, content));
                }
            }
            return trimHistory(out);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static List<OpenAiMessage> trimHistory(List<OpenAiMessage> history) {
        if (history.size() <= MAX_HISTORY_MESSAGES) {
            return history;
        }
        return new ArrayList<>(history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size()));
    }

    private String historyToJson(List<OpenAiMessage> history) {
        try {
            List<Map<String, String>> raw = new ArrayList<>();
            for (OpenAiMessage m : history) {
                raw.add(Map.of("role", m.role(), "content", m.content()));
            }
            return objectMapper.writeValueAsString(raw);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String extractQuestion(JsonNode body) {
        JsonNode slots = body.path("request").path("intent").path("slots");
        if (slots.has("question") && slots.path("question").hasNonNull("value")) {
            return slots.path("question").path("value").asText();
        }
        if (slots.has("query") && slots.path("query").hasNonNull("value")) {
            return slots.path("query").path("value").asText();
        }
        return null;
    }

    private Map<String, Object> alexaSpeak(String text, boolean endSession, List<OpenAiMessage> history) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", "1.0");
        if (!endSession) {
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("historyJson", historyToJson(history == null ? List.of() : history));
            root.put("sessionAttributes", attrs);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> speech = new LinkedHashMap<>();
        speech.put("type", "PlainText");
        speech.put("text", text);
        response.put("outputSpeech", speech);
        response.put("shouldEndSession", endSession);
        // Keep mic open for follow-ups when session continues
        if (!endSession) {
            Map<String, Object> reprompt = new LinkedHashMap<>();
            Map<String, Object> repromptSpeech = new LinkedHashMap<>();
            repromptSpeech.put("type", "PlainText");
            repromptSpeech.put("text", "Anything else?");
            reprompt.put("outputSpeech", repromptSpeech);
            response.put("reprompt", reprompt);
        }
        root.put("response", response);
        return root;
    }
}
