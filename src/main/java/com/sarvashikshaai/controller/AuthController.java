package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.UserRole;
import com.sarvashikshaai.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Value("${jwt.cookie-name}")
    private String cookieName;

    @Value("${jwt.expiration-seconds}")
    private long expirationSeconds;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        if (request == null || request.username() == null || request.username().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            UsernamePasswordAuthenticationToken authRequest =
                    new UsernamePasswordAuthenticationToken(request.username().trim(), request.password());
            authRequest.setDetails(new WebAuthenticationDetailsSource().buildDetails(servletRequest));

            Authentication authentication = authenticationManager.authenticate(authRequest);
            UserRole role = roleFromAuthorities(authentication);

            String token = jwtService.generateToken(authentication.getName(), role);

            ResponseCookie cookie = ResponseCookie.from(cookieName, token)
                    .httpOnly(true)
                    .secure(servletRequest.isSecure())
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(Duration.ofSeconds(expirationSeconds))
                    .build();
            servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

            return ResponseEntity.ok(new LoginResponse(token, "Bearer", role.name()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).build();
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(servletRequest.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok().build();
    }

    private static UserRole roleFromAuthorities(Authentication authentication) {
        for (GrantedAuthority a : authentication.getAuthorities()) {
            String authority = a.getAuthority();
            // We store ROLE_TEACHER / ROLE_ADMIN
            if (authority != null && authority.startsWith("ROLE_")) {
                String raw = authority.substring("ROLE_".length()).trim().toUpperCase(Locale.ROOT);
                return UserRole.valueOf(raw);
            }
        }
        throw new IllegalStateException("No role granted for authenticated user");
    }

    public record LoginRequest(String username, String password) {}

    public record LoginResponse(String token, String tokenType, String role) {}
}

