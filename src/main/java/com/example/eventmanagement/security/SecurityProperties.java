package com.example.eventmanagement.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Strongly-typed `app.security.*` properties.
 *
 * Using `@ConfigurationProperties` rather than `@Value` everywhere means a typo
 * in `application.yml` fails at startup with a clear message instead of
 * silently falling back to a default — and the CORS list can be validated
 * against `allowed-origins` at the same time.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private Jwt jwt = new Jwt();
    private int passwordStrength = 12;
    private int maxLoginAttempts = 5;
    private int lockDurationMinutes = 15;

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long expirationMs = 86_400_000L;
        private String issuer = "eventora";
        private long clockSkewSeconds = 30;
    }

    /** Injected here so `SecurityConfig` has a single bean for everything. */
    @Getter
    @Setter
    private java.util.List<String> allowedOrigins = java.util.List.of("http://localhost:5173");

    @Getter
    @Setter
    private long maxAgeSeconds = 3600;
}
