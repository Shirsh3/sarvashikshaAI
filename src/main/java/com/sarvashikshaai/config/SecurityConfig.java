package com.sarvashikshaai.config;

import com.sarvashikshaai.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Public paths — everything is open for now.
     * Role-based login with form authentication will be added in a later milestone.
     * <p>Marketing / UX login screen: {@code GET /login} ({@code templates/login.html} via {@code LoginController}).
     * When real auth is enabled, add {@code .formLogin(f -> f.loginPage("/login").permitAll())} (and routes)
     * after {@code authorizeHttpRequests}; wire {@code UserDetailsService} and protect paths as needed.</p>
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/js/**", "/favicon.svg", "/", "/index.html").permitAll()
                .requestMatchers("/logout").permitAll()
                .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/menu").permitAll()
                .requestMatchers("/api/superadmin/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/superadmin/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/admin/cleanup/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/**").authenticated()
                .requestMatchers("/teacher/**").hasAnyRole("TEACHER", "ADMIN", "SUPER_ADMIN")
                .requestMatchers("/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers("/reading/api/**").authenticated()
                // Everything else should require auth (e.g. /quiz/**, /attendance, /reading)
                .anyRequest().authenticated()
            )
            // CSRF: keep enabled for form POSTs, but allow fetch-based JSON endpoints
            .csrf(csrf -> csrf
                // Avoid HttpSession CSRF tokens (JWT auth is mostly stateless; session creation can fail late).
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers(
                    "/api/**",
                    "/explain",
                    "/assembly/regenerate",
                    "/reading/feedback",
                    "/reading/generate-passage",
                    "/attendance/mark",
                    "/quiz/create", "/quiz/ai-generate", "/quiz/**"
                )
            );

        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    String path = request.getRequestURI();
                    boolean isApi = false;
                    if (path != null) {
                        // Treat nested API routes (e.g. /admin/api/**, /teacher/api/**) as API too
                        isApi = path.startsWith("/api/") || path.contains("/api/");
                    }
                    if (isApi) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"unauthorized\"}");
                        return;
                    }
                    response.sendRedirect("/login");
                })
        );

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
