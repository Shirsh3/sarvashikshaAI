package com.sarvashikshaai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.JdbcOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Public paths — students and the classroom device access these without any login.
     * Teacher panel (/teacher/**) requires Google OAuth2 login.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ClientRegistrationRepository clientRepo,
                                           OAuth2AuthorizedClientService clientService) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/explain", "/assembly/**",
                    "/reading", "/reading/**",
                    "/attendance", "/attendance/**",
                    "/quiz", "/quiz/**",
                    "/css/**", "/js/**", "/images/**",
                    "/actuator/**", "/error",
                    "/h2-console", "/h2-console/**"
                ).permitAll()
                .requestMatchers("/teacher/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(ep -> ep
                    .authorizationRequestResolver(offlineAccessResolver(clientRepo))
                )
                .defaultSuccessUrl("/teacher/setup", true)
                .authorizedClientService(clientService)
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
            )
            // CSRF: keep enabled for form POSTs, but allow fetch-based JSON endpoints
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(
                    "/explain",
                    "/teacher/sync", "/teacher/sheet",
                    "/teacher/assistant/generate",
                    "/teacher/reading/generate", "/teacher/reading/fetch-news", "/teacher/reading/use-default",
                    "/teacher/reports/generate", "/teacher/reports/invalidate",
                    "/reading/feedback",
                    "/attendance/mark",
                    "/quiz/create", "/quiz/ai-generate", "/quiz/submit", "/quiz/**",
                    "/h2-console/**"
                )
            )
            // Allow H2 console iframes
            .headers(headers -> headers.frameOptions(fo -> fo.sameOrigin()));

        return http.build();
    }

    /**
     * Requests offline access + consent prompt so Google returns a refresh token.
     * This keeps the teacher logged in across sessions without re-authentication.
     */
    private OAuth2AuthorizationRequestResolver offlineAccessResolver(ClientRegistrationRepository repo) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
            new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(customizer ->
            customizer.additionalParameters(params -> {
                params.put("access_type", "offline");
                params.put("prompt", "consent");
            })
        );
        return resolver;
    }

    /**
     * JDBC-backed authorized client service — persists OAuth tokens to H2 file DB
     * so the teacher stays logged in across server restarts.
     */
    @Bean
    public OAuth2AuthorizedClientService authorizedClientService(
            JdbcTemplate jdbcTemplate,
            ClientRegistrationRepository clientRegistrationRepository) {

        // Create the OAuth2 token table if it doesn't exist yet
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS oauth2_authorized_client (
              client_registration_id VARCHAR(100) NOT NULL,
              principal_name         VARCHAR(200) NOT NULL,
              access_token_type      VARCHAR(100) NOT NULL,
              access_token_value     BLOB         NOT NULL,
              access_token_issued_at TIMESTAMP    NOT NULL,
              access_token_expires_at TIMESTAMP   NOT NULL,
              access_token_scopes    VARCHAR(1000) DEFAULT NULL,
              refresh_token_value    BLOB          DEFAULT NULL,
              refresh_token_issued_at TIMESTAMP   DEFAULT NULL,
              created_at             TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
              PRIMARY KEY (client_registration_id, principal_name)
            )
            """);

        return new JdbcOAuth2AuthorizedClientService(jdbcTemplate, clientRegistrationRepository);
    }
}
