package com.sarvashikshaai.security;

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
 * Always verifies Amazon request signatures on {@code POST /alexa}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class AlexaSignatureFilter extends OncePerRequestFilter {

    private final AlexaSignatureVerifier verifier;

    public AlexaSignatureFilter(AlexaSignatureVerifier verifier) {
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
