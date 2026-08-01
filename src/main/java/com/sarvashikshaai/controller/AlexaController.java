package com.sarvashikshaai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sarvashikshaai.ai.OpenAIClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Alexa Custom Skill endpoint. Point the skill HTTPS URL at POST /alexa.
 */
@RestController
public class AlexaController {

    private static final String SYSTEM_HINT = """
            You are Sarvasiksha AI, a helpful personal voice assistant.
            Answer clearly for an adult user.
            Keep answers short enough to speak aloud (about 2-4 short sentences) unless they ask for more detail.
            Reply in Hindi if the question is in Hindi; otherwise use clear English.
            """;

    private final OpenAIClient openAIClient;

    public AlexaController(OpenAIClient openAIClient) {
        this.openAIClient = openAIClient;
    }

    @PostMapping(value = "/alexa", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handle(@RequestBody JsonNode body) {
        String type = body.path("request").path("type").asText("");
        String locale = body.path("request").path("locale").asText("en-IN");

        if ("LaunchRequest".equals(type)) {
            return alexaSpeak("Hi. I am Sarvasiksha AI. What can I help you with?", false);
        }

        if ("SessionEndedRequest".equals(type)) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("version", "1.0");
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("shouldEndSession", true);
            response.put("response", inner);
            return response;
        }

        if ("IntentRequest".equals(type)) {
            String intentName = body.path("request").path("intent").path("name").asText("");
            if ("AMAZON.StopIntent".equals(intentName) || "AMAZON.CancelIntent".equals(intentName)) {
                return alexaSpeak("Goodbye.", true);
            }
            if ("AMAZON.HelpIntent".equals(intentName)) {
                return alexaSpeak("Ask me anything. For example: summarize this idea, or explain a concept.", false);
            }

            String question = extractQuestion(body);
            if (question == null || question.isBlank()) {
                return alexaSpeak("I did not catch the question. Please ask again.", false);
            }
            try {
                String prompt = SYSTEM_HINT + "\nLocale hint: " + locale + "\n\nUser question:\n" + question.trim();
                String answer = openAIClient.generateCompletion(prompt);
                return alexaSpeak(answer, false);
            } catch (Exception e) {
                return alexaSpeak("Sorry, I could not answer right now. Please try again.", false);
            }
        }

        return alexaSpeak("Sorry, I did not understand that.", false);
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

    private static Map<String, Object> alexaSpeak(String text, boolean endSession) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", "1.0");
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> speech = new LinkedHashMap<>();
        speech.put("type", "PlainText");
        speech.put("text", text);
        response.put("outputSpeech", speech);
        response.put("shouldEndSession", endSession);
        root.put("response", response);
        return root;
    }
}
