package com.sarvashikshaai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Public paths — everything is open for now.
     * Role-based login with form authentication will be added in a later milestone.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )
            // CSRF: keep enabled for form POSTs, but allow fetch-based JSON endpoints
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(
                    "/explain",
                    "/reading/feedback",
                    "/reading/generate-passage",
                    "/attendance/mark",
                    "/quiz/create", "/quiz/ai-generate", "/quiz/**"
                )
            );

        return http.build();
    }
}
