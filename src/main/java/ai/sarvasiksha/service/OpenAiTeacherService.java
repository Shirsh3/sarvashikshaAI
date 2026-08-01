package ai.sarvasiksha.service;

import ai.sarvasiksha.config.OpenAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Service
public class OpenAiTeacherService {

    private static final String SYSTEM_PROMPT =
            "You are Sarvasiksha AI, a helpful personal voice assistant. "
                    + "Answer clearly and usefully for an adult user. "
                    + "Keep answers short enough to speak aloud (about 2–4 short sentences) unless they ask for more detail. "
                    + "Reply in Hindi if the question is in Hindi; otherwise use clear English.";

    private final OpenAiProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenAiTeacherService(OpenAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    public String ask(String question, String locale) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY is not set. Export it before starting the app.");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel());
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
        String userContent = question;
        if (locale != null && !locale.isBlank()) {
            userContent = "[locale=" + locale + "] " + question;
        }
        messages.addObject().put("role", "user").put("content", userContent);
        body.put("temperature", 0.4);
        body.put("max_tokens", 400);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        String url = properties.getBaseUrl().replaceAll("/$", "") + "/chat/completions";
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(body.toString(), headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new IllegalStateException("OpenAI returned an empty answer");
            }
            return content.asText().trim();
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(
                    "OpenAI API error: HTTP " + e.getRawStatusCode() + " — check key/quota", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to call OpenAI: " + e.getMessage(), e);
        }
    }
}
