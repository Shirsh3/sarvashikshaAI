package com.sarvashikshaai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Security for personal-assistant endpoints ({@code /api/v1/*} and {@code /alexa}).
 *
 * <p>Env mapping (Render):
 * <ul>
 *   <li>{@code ASSISTANT_SECURITY_ENABLED}</li>
 *   <li>{@code ASSISTANT_SECURITY_API_KEY}</li>
 *   <li>{@code ASSISTANT_SECURITY_ALEXA_SKILL_ID}</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "assistant.security")
public class AssistantSecurityProperties {

    /**
     * Master switch. When false, API key / skill-id checks are skipped (local only).
     */
    private boolean enabled = true;

    /**
     * Shared secret for {@code POST /api/v1/ask}.
     * Send as {@code Authorization: Bearer <key>} or {@code X-Api-Key: <key>}.
     * Empty = ask endpoint open (not recommended in prod).
     */
    private String apiKey = "";

    /**
     * Alexa skill application id, e.g. {@code amzn1.ask.skill.xxxxxxxx}.
     * When set, {@code POST /alexa} rejects requests whose skill id does not match.
     * Empty = skill-id check disabled.
     */
    private String alexaSkillId = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAlexaSkillId() {
        return alexaSkillId;
    }

    public void setAlexaSkillId(String alexaSkillId) {
        this.alexaSkillId = alexaSkillId;
    }

    public boolean apiKeyConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean alexaSkillIdConfigured() {
        return alexaSkillId != null && !alexaSkillId.isBlank();
    }
}
