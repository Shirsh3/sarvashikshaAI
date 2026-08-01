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

/**
 * Verifies Amazon request signatures on {@code POST /alexa} when enabled.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class AlexaSignatureFilter extends OncePerRequestFilter {

    private final AssistantSecurityProperties properties;
    private final AlexaSignatureVerifier verifier;

    public AlexaSignatureFilter(AssistantSecurityProperties properties, AlexaSignatureVerifier verifier) {
        this.properties = properties;
        this.verifier = verifier;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI() == null || !request.getRequestURI().equals("/alexa");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.isEnabled() || !properties.isAlexaVerifySignatures()) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request);
        try {
            verifier.verify(
                    wrapped.getHeader("SignatureCertChainUrl"),
                    wrapped.getHeader("Signature"),
                    wrapped.getCachedBody()
            );
        } catch (SecurityException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"error\":\"invalid_alexa_signature\",\"message\":\"Request signature verification failed\"}");
            return;
        }

        filterChain.doFilter(wrapped, response);
    }
}
