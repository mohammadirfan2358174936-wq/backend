package com.example.eventmanagement.security;

import java.io.IOException;

import com.example.eventmanagement.entity.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * SECURITY CONFIGURATION
 * ======================
 *
 * A stateless, token-only filter chain — no sessions, no CSRF, no form login.
 *
 * * `SessionCreationPolicy.STATELESS` — the server keeps no session, which is
 *   what makes horizontal scaling trivial (any node serves any request).
 * * `@EnableMethodSecurity` — adds `@PreAuthorize`, used on the endpoints where
 *   the owner check must run (e.g. "only this event's organiser").
 * * **Defence in depth on authorisation.** Filter-chain rules decide *who may
 *   call a path*; `EventService#assertOwner` decides *whose data they may
 *   touch*. An organiser can hit `/api/v1/events/99/publish`, but the service
 *   layer rejects it if event 99 belongs to somebody else. Both are needed:
 *   the chain alone cannot express "owner or admin".
 * * CORS is an explicit allow-list, never `*`, and mirrors
 *   `app.cors.allowed-origins` so the browser never has a silent mismatch
 *   between dev and prod.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    /** Matches the frontend routes — public discovery must not 401. */
    private static final String[] PUBLIC_GET = {
        "/",
        "/index.html",
        "/favicon.ico",
        "/actuator/health",
        "/actuator/info",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/api/v1/events",
        "/api/v1/events/*",
        "/api/v1/events/featured",
        "/api/v1/categories/**",
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── stateless ─────────────────────────────────────────────────
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)

            // ── routing ───────────────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout").authenticated()

                // events: create is organiser+, mutate is owner-or-admin (see
                // EventService#assertOwner for the ownership half).
                .requestMatchers(HttpMethod.POST, "/api/v1/events").hasAnyRole(Role.ORGANIZER.name(), Role.ADMIN.name())
                .requestMatchers(HttpMethod.PUT, "/api/v1/events/**").hasAnyRole(Role.ORGANIZER.name(), Role.ADMIN.name())
                .requestMatchers(HttpMethod.DELETE, "/api/v1/events/**").hasAnyRole(Role.ORGANIZER.name(), Role.ADMIN.name())
                .requestMatchers("/api/v1/events/**").authenticated()

                // registration & tickets
                .requestMatchers(HttpMethod.POST, "/api/v1/events/*/register").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/events/*/register").authenticated()
                .requestMatchers("/api/v1/users/**").authenticated()
                .requestMatchers("/api/v1/tickets/**").authenticated()

                // dashboards
                .requestMatchers("/api/v1/dashboard/organizer").hasAnyRole(Role.ORGANIZER.name(), Role.ADMIN.name())
                .requestMatchers("/api/v1/dashboard/admin").hasRole(Role.ADMIN.name())

                .anyRequest().authenticated()
            )

            // ── failure rendering ──────────────────────────────────────────
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint((request, response, authException) ->
                    writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Authentication is required to access this resource."))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "You do not have permission to perform this action.")))

            // ── our JWT filter runs before Spring's own auth filter ────────
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Strength 12: ~250ms per hash on commodity hardware — heavy enough to
        // make offline brute force expensive, light enough to stay under a
        // second on a login.
        return new BCryptPasswordEncoder(securityProperties.getPasswordStrength());
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /** Explicit origins — never `*`, which is incompatible with credentials. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(securityProperties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of(HttpHeaders.AUTHORIZATION, "X-Error-Code", "X-Request-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(securityProperties.getMaxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /* -------------------------------------------------------------- internals */

    /**
     * Errors leave through the same JSON envelope as the rest of the API, so
     * the frontend's error handling never has to special-case a 401 from the
     * filter chain versus one from a controller.
     */
    private void writeError(HttpServletResponse response, HttpStatus status,
                            String errorCode, String message) throws IOException {

        if (response.isCommitted()) return;

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Error-Code", errorCode);

        Map<String, Object> body = Map.of(
            "success", false,
            "message", message,
            "errorCode", errorCode,
            "timestamp", Instant.now().toString());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
