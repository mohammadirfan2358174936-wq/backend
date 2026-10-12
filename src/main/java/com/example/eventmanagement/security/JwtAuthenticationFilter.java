package com.example.eventmanagement.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT AUTHENTICATION FILTER
 * ========================
 * Runs once per request, ahead of `AuthorizationFilter`.
 *
 * Order of operations:
 *  1. Read the `Authorization: Bearer <token>` header.
 *  2. Verify the signature and expiry **without** a DB lookup.
 *  3. Only if valid, load the user once to confirm the account still exists and
 *     is enabled — this is what makes a revocation (disable account) take effect
 *     immediately instead of when the token expires.
 *  4. Populate the `SecurityContext`.
 *
 * The filter never rejects a request itself; missing headers are simply ignored
 * so the security chain can decide (public routes continue, protected ones 401).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (StringUtils.hasText(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String username = jwtService.extractUsername(token);
                UserDetails user = userDetailsService.loadUserByUsername(username);

                if (jwtService.isTokenValid(token, user) && user.isEnabled()) {
                    authenticate(request, user);
                } else {
                    log.debug("Token for {} failed verification", username);
                }
            } catch (RuntimeException invalid) {
                // Expired / malformed / revoked token. Clear anything partial.
                SecurityContextHolder.clearContext();
                log.debug("Authentication rejected: {}", invalid.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        // 1. Header — the canonical way.
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length()).trim();
        }

        // 2. Query-string fallback for the ticket download link.
        String param = request.getParameter("access_token");
        if (StringUtils.hasText(param)) {
            return param;
        }
        return null;
    }

    private void authenticate(HttpServletRequest request, UserDetails user) {
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities());

        authentication.setDetails(
            new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.trace("Authenticated {} with {}", user.getUsername(), user.getAuthorities());
    }
}
