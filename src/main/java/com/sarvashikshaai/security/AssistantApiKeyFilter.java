package com.sarvashikshaai.security;

import com.sarvashikshaai.config.AssistantSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protects {@code /api/v1/ask} with a configurable API key when security is enabled.
 * Health stays open for Render probes.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AssistantApiKeyFilter extends OncePerRequestFilter {

    private final AssistantSecurityProperties properties;

    public AssistantApiKeyFilter(AssistantSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.equals("/api/v1/ask");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.isEnabled() || !properties.apiKeyConfigured()) {
            filterChain.doFilter(request, response);
            return;
        }

        String provided = extractKey(request);
        if (provided == null || !constantTimeEquals(provided, properties.getApiKey().trim())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"Missing or invalid API key\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static String extractKey(HttpServletRequest request) {
        String apiKeyHeader = request.getHeader("X-Api-Key");
        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            return apiKeyHeader.trim();
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return auth.substring(7).trim();
        }
        return null;
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }
}
