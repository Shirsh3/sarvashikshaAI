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
            Default: answer in 2-4 short sentences suitable for speech.
            If the user asks for more detail, a longer explanation, or "in detail", you may answer more fully.
            Reply in Hindi if the question is in Hindi; otherwise use clear English.
            Use prior turns in this conversation for follow-ups (e.g. "explain that", "make it shorter").
            """;

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
        boolean newSession = body.path("session").path("new").asBoolean(false);
        JsonNode attributes = body.path("session").path("attributes");
        List<OpenAiMessage> history = newSession ? new ArrayList<>() : readHistory(attributes);
        String lastAnswer = newSession ? "" : textAttr(attributes, "lastAnswer");

        if ("LaunchRequest".equals(type)) {
            String welcome = newSession || history.isEmpty()
                    ? "Welcome. I am Sarvasiksha AI. What would you like to talk about today?"
                    : "Welcome back. What would you like to talk about today?";
            return ResponseEntity.ok(alexaSpeak(welcome, false, List.of(), ""));
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
                return ResponseEntity.ok(alexaSpeak("Okay. Goodbye.", true, List.of(), ""));
            }
            if ("AMAZON.HelpIntent".equals(intentName)) {
                return ResponseEntity.ok(alexaSpeak(
                        "Say ask, then your question. For follow-ups, keep asking while I am open. "
                                + "Say repeat that to hear my last answer, or stop to finish.",
                        false,
                        history,
                        lastAnswer));
            }
            if ("AMAZON.RepeatIntent".equals(intentName)) {
                if (lastAnswer == null || lastAnswer.isBlank()) {
                    return ResponseEntity.ok(alexaSpeak(
                            "I do not have a previous answer yet. Ask me something first.",
                            false,
                            history,
                            lastAnswer));
                }
                return ResponseEntity.ok(alexaSpeak(lastAnswer, false, history, lastAnswer));
            }
            if ("AMAZON.FallbackIntent".equals(intentName)) {
                // Alexa does not always send the raw utterance; guide the user.
                return ResponseEntity.ok(alexaSpeak(
                        "I did not catch that as a command. Please say ask, then your question. "
                                + "For example: ask why is the sky blue.",
                        false,
                        history,
                        lastAnswer));
            }

            String question = extractQuestion(body);
            if (question == null || question.isBlank()) {
                return ResponseEntity.ok(alexaSpeak(
                        "I did not catch the question. Please say ask, then try again.",
                        false,
                        history,
                        lastAnswer));
            }
            return ResponseEntity.ok(answerQuestion(question.trim(), locale, history));
        }

        return ResponseEntity.ok(alexaSpeak(
                "Sorry, I did not understand that. Please say ask, then your question.",
                false,
                history,
                lastAnswer));
    }

    private Map<String, Object> answerQuestion(String question, String locale, List<OpenAiMessage> history) {
        try {
            List<OpenAiMessage> messages = new ArrayList<>();
            messages.add(new OpenAiMessage("system", SYSTEM_HINT + "\nLocale hint: " + locale));
            messages.addAll(history);
            messages.add(new OpenAiMessage("user", question));

            String answer = openAIClient.generateChatCompletion(messages);

            List<OpenAiMessage> updated = new ArrayList<>(history);
            updated.add(new OpenAiMessage("user", question));
            updated.add(new OpenAiMessage("assistant", answer));
            updated = trimHistory(updated);

            return alexaSpeak(answer, false, updated, answer);
        } catch (Exception e) {
            return alexaSpeak(
                    "I am having trouble reaching the AI right now. Please try again.",
                    false,
                    history,
                    historyLastAssistant(history));
        }
    }

    private static String historyLastAssistant(List<OpenAiMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("assistant".equals(history.get(i).role())) {
                return history.get(i).content();
            }
        }
        return "";
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

    private static String textAttr(JsonNode attributes, String key) {
        if (attributes == null || attributes.isMissingNode() || !attributes.hasNonNull(key)) {
            return "";
        }
        return attributes.path(key).asText("");
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

    private Map<String, Object> alexaSpeak(
            String text,
            boolean endSession,
            List<OpenAiMessage> history,
            String lastAnswer
    ) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", "1.0");
        if (!endSession) {
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("historyJson", historyToJson(history == null ? List.of() : history));
            attrs.put("lastAnswer", lastAnswer == null ? "" : lastAnswer);
            root.put("sessionAttributes", attrs);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> speech = new LinkedHashMap<>();
        speech.put("type", "PlainText");
        speech.put("text", text);
        response.put("outputSpeech", speech);
        response.put("shouldEndSession", endSession);
        if (!endSession) {
            Map<String, Object> reprompt = new LinkedHashMap<>();
            Map<String, Object> repromptSpeech = new LinkedHashMap<>();
            repromptSpeech.put("type", "PlainText");
            repromptSpeech.put("text", "Anything else you would like to know?");
            reprompt.put("outputSpeech", repromptSpeech);
            response.put("reprompt", reprompt);
        }
        root.put("response", response);
        return root;
    }
}
