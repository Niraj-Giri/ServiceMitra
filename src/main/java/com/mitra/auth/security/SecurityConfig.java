package com.mitra.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration.
 *
 * SEC-02 FIX: Previously this config used anyRequest().permitAll() which
 * made ALL endpoints - including admin - accessible to anyone without a token.
 *
 * Now:
 * - Public endpoints (auth) are explicitly permitted.
 * - Admin endpoints require ROLE_ADMIN.
 * - All other authenticated endpoints require any valid role.
 * - CORS is restricted to configured origins (not wildcard *).
 *
 * @EnableMethodSecurity enables @PreAuthorize at the method level for
 * fine-grained access control within controllers.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    /** Allowed origins loaded from environment variable.
     *  Dev default: http://localhost:5173 (Vite dev server).
     *  Production: set CORS_ALLOWED_ORIGINS to your actual domain.
     */
    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://localhost:5174,http://127.0.0.1:5173,http://127.0.0.1:3000,http://127.0.0.1:5174,https://service-mitra-frontend.vercel.app}")
    private String allowedOriginsRaw;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())   // JWT is stateless - CSRF token not needed
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                // -- Public auth endpoints ----------------------------------
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Public service browsing (no login needed to see services)
                .requestMatchers(HttpMethod.GET, "/api/v1/services", "/api/v1/services/**").permitAll()

                // -- Admin-only endpoints -----------------------------------
                // All /admin/** paths require ROLE_ADMIN
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // Admin task management endpoints
                .requestMatchers("/api/v1/tasks/admin/**").hasRole("ADMIN")

                // -- All other endpoints require any authenticated user -----
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // SEC-02 FIX: Replace wildcard * with specific allowed origins.
        // Configured via CORS_ALLOWED_ORIGINS env variable.
        // Dev default: localhost Vite + React dev servers.
        List<String> origins = Arrays.asList(allowedOriginsRaw.split(","));
        configuration.setAllowedOrigins(origins);

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // Cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
