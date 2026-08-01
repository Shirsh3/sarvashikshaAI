package com.sarvashikshaai.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.sarvashikshaai.config.AssistantSecurityProperties;
import org.springframework.stereotype.Component;

/**
 * Alexa cannot send a custom API key. Security for {@code /alexa} is:
 * <ol>
 *   <li>HTTPS (Render)</li>
 *   <li>Configured skill application id must match the request</li>
 *   <li>(Recommended next) Amazon request signature verification</li>
 * </ol>
 */
@Component
public class AlexaSkillIdValidator {

    private final AssistantSecurityProperties properties;

    public AlexaSkillIdValidator(AssistantSecurityProperties properties) {
        this.properties = properties;
    }

    public boolean isAllowed(JsonNode body) {
        if (!properties.isEnabled() || !properties.alexaSkillIdConfigured()) {
            return true;
        }
        String expected = properties.getAlexaSkillId().trim();
        String actual = extractSkillId(body);
        return actual != null && expected.equals(actual);
    }

    static String extractSkillId(JsonNode body) {
        if (body == null) {
            return null;
        }
        String fromContext = textOrNull(body.path("context").path("System").path("application").path("applicationId"));
        if (fromContext != null) {
            return fromContext;
        }
        return textOrNull(body.path("session").path("application").path("applicationId"));
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String v = node.asText("").trim();
        return v.isEmpty() ? null : v;
    }
}
