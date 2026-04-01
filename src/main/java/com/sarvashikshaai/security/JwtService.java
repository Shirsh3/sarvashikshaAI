package com.sarvashikshaai.security;

import com.sarvashikshaai.model.UserRole;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.JwsHeader;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.nio.charset.StandardCharsets;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

/**
 * Small HS256 JWT helper used for issuing and validating auth tokens.
 * <p>
 * We keep claims minimal: {@code sub=username} and {@code role=TEACHER|ADMIN}.
 */
@Service
public class JwtService {

    private final String secret;
    private final String issuer;
    private final long expirationSeconds;

    private JwtEncoder encoder;
    private JwtDecoder decoder;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.expiration-seconds}") long expirationSeconds
    ) {
        this.secret = secret;
        this.issuer = issuer;
        this.expirationSeconds = expirationSeconds;
    }

    @PostConstruct
    void init() {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        // Encoder/Decoder are symmetric for HS256 (shared secret).
        JWKSource<SecurityContext> jwkSource = new ImmutableSecret<>(key);
        this.encoder = new NimbusJwtEncoder(jwkSource);
        this.decoder = NimbusJwtDecoder.withSecretKey(key).build();
    }

    public String generateToken(String username, UserRole role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirationSeconds);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(username)
                .claim("role", role.name())
                .build();

        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder
                .encode(JwtEncoderParameters.from(headers, claims))
                .getTokenValue();
    }

    public JwtClaims decodeAndValidate(String token) {
        Jwt jwt = decoder.decode(token);
        String username = jwt.getSubject();
        String roleRaw = jwt.getClaimAsString("role");
        UserRole role = roleRaw != null ? UserRole.valueOf(roleRaw) : null;
        return new JwtClaims(username, role);
    }

    public record JwtClaims(String username, UserRole role) {}
}

