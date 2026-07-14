package com.mitra.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SEC-12: Startup security configuration validator.
 *
 * Runs at application start and warns if any critical secrets are still
 * using the CHANGE_ME placeholder values from application.properties.
 *
 * This is a safety net — if a developer forgets to set environment variables,
 * they will see prominent WARNING logs at startup rather than discovering the
 * issue after deployment.
 *
 * Does NOT fail startup (to preserve dev convenience). In a production
 * hardening step this could be changed to throw an exception.
 *
 * TODO Production: Change log.warn to throw new IllegalStateException(...)
 * so the app refuses to start with insecure placeholder values.
 */
@Slf4j
@Component
public class StartupSecurityCheck {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${cloudinary.api-secret}")
    private String cloudinarySecret;

    @Value("${cors.allowed-origins}")
    private String corsOrigins;

    @PostConstruct
    public void checkSecurityConfiguration() {
        boolean hasWarnings = false;

        if (isPlaceholder(jwtSecret)) {
            log.warn("⚠️  SECURITY WARNING: JWT secret is using a placeholder value (CHANGE_ME). " +
                     "Set JWT_SECRET environment variable to a secure random string of at least 32 bytes. " +
                     "A weak JWT secret allows token forgery.");
            hasWarnings = true;
        } else if (jwtSecret.length() < 32) {
            log.warn("⚠️  SECURITY WARNING: JWT secret is shorter than 32 bytes ({}). " +
                     "HMAC-SHA256 requires at least 32 bytes for adequate security.", jwtSecret.length());
            hasWarnings = true;
        }

        if (isPlaceholder(dbPassword)) {
            log.warn("⚠️  SECURITY WARNING: Database password is using a placeholder value (CHANGE_ME). " +
                     "Set SPRING_DATASOURCE_PASSWORD environment variable.");
            hasWarnings = true;
        }

        if (isPlaceholder(cloudinarySecret)) {
            log.warn("⚠️  SECURITY WARNING: Cloudinary API secret is using a placeholder value (CHANGE_ME). " +
                     "Set CLOUDINARY_API_SECRET environment variable. File uploads will fail.");
            hasWarnings = true;
        }

        if (corsOrigins.contains("*")) {
            log.warn("⚠️  SECURITY WARNING: CORS allowed origins contains wildcard '*'. " +
                     "Set CORS_ALLOWED_ORIGINS to your specific frontend domain(s).");
            hasWarnings = true;
        }

        if (!hasWarnings) {
            log.info("✅  Security configuration check passed.");
        }
    }

    private boolean isPlaceholder(String value) {
        return value == null
                || value.isBlank()
                || value.toUpperCase().contains("CHANGE_ME")
                || value.toUpperCase().contains("CHANGE-ME");
    }
}
