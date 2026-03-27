package com.sarvashikshaai.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Resolves OpenAI settings for optional live tests.
 * <p>
 * Precedence (first non-blank wins): environment / JVM system properties, then
 * {@code classpath:application-local.properties} (same file as local Spring config:
 * {@code src/main/resources/application-local.properties} copied to {@code target/classes}).
 */
final class OpenAiCredentials {

    private static volatile Properties applicationLocalCache;

    private OpenAiCredentials() {
    }

    static boolean present() {
        return !key().isBlank();
    }

    static String key() {
        String k = System.getenv("OPENAI_API_KEY");
        if (k != null && !k.isBlank()) {
            return k.trim();
        }
        k = System.getProperty("OPENAI_API_KEY");
        if (k != null && !k.isBlank()) {
            return k.trim();
        }
        k = System.getProperty("openai.api.key");
        if (k != null && !k.isBlank()) {
            return k.trim();
        }
        k = applicationLocalProperties().getProperty("openai.api-key");
        return k != null ? k.trim() : "";
    }

    static String apiBaseUrl() {
        String u = System.getenv("OPENAI_API_BASE_URL");
        if (u != null && !u.isBlank()) {
            return u.trim().replaceAll("/$", "");
        }
        u = System.getProperty("openai.api-base-url");
        if (u != null && !u.isBlank()) {
            return u.trim().replaceAll("/$", "");
        }
        u = applicationLocalProperties().getProperty("openai.api-base-url");
        if (u != null && !u.isBlank()) {
            return u.trim().replaceAll("/$", "");
        }
        return "https://api.openai.com/v1";
    }

    static String teachingModel() {
        String m = System.getProperty("openai.model.teaching");
        if (m != null && !m.isBlank()) {
            return m.trim();
        }
        m = System.getProperty("openai.model");
        if (m != null && !m.isBlank()) {
            return m.trim();
        }
        m = applicationLocalProperties().getProperty("openai.model.teaching");
        if (m != null && !m.isBlank()) {
            return m.trim();
        }
        m = applicationLocalProperties().getProperty("openai.model");
        if (m != null && !m.isBlank()) {
            return m.trim();
        }
        return "gpt-4o-mini";
    }

    private static Properties applicationLocalProperties() {
        if (applicationLocalCache != null) {
            return applicationLocalCache;
        }
        synchronized (OpenAiCredentials.class) {
            if (applicationLocalCache != null) {
                return applicationLocalCache;
            }
            Properties p = new Properties();
            try (InputStream in = OpenAiCredentials.class.getClassLoader()
                    .getResourceAsStream("application-local.properties")) {
                if (in != null) {
                    p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // leave empty — file optional
            }
            applicationLocalCache = p;
            return p;
        }
    }
}
