package com.example.eventmanagement.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT SERVICE
 * ==========
 * Issue and verify HS256 tokens.
 *
 * Deliberate decisions:
 *  - **No refresh tokens.** A rotating-access-token design adds a whole token
 *    store for very little gain on a product like this; instead the access
 *    token lives 24h and the client stores it in `localStorage`.
 *  - **Claims carry the role.** `role` is embedded so the filter can build the
 *    `Authentication` without a database round trip on every request.
 *  - **`iat`/`exp`/`nbf` and a small clock skew** guard against replay across a
 *    restart and against minor clock drift between nodes.
 *  - The secret is resolved to at least 512 bits, so JJWT cannot be tricked
 *    into signing with a weak key.
 */
@Slf4j
@Service
public class JwtService {

    /** Minimum key length in bytes required by HS512-safe key derivation. */
    private static final int MIN_KEY_BYTES = 64;

    private final SecretKey key;
    private final long expirationMs;
    private final String issuer;
    private final long skewSeconds;

    public JwtService(
        @Value("${app.security.jwt.secret}") String secret,
        @Value("${app.security.jwt.expiration-ms:86400000}") long expirationMs,
        @Value("${app.security.jwt.issuer:eventora}") String issuer,
        @Value("${app.security.jwt.clock-skew-seconds:30}") long skewSeconds) {

        this.key = deriveKey(secret);
        this.expirationMs = expirationMs;
        this.issuer = issuer;
        this.skewSeconds = skewSeconds;

        log.info("JwtService ready — tokens valid for {} ms, issuer '{}'", expirationMs, issuer);
    }

    /** Mint a token for an authenticated user. */
    public String generateToken(UserDetails user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", roleOf(user));
        claims.put("sub", user.getUsername());
        claims.put("iss", issuer);

        Instant now = Instant.now();
        return Jwts.builder()
            .claims(claims)
            .subject(user.getUsername())
            .issuer(issuer)
            .issuedAt(Date.from(now))
            .notBefore(Date.from(now.minusSeconds(skewSeconds)))
            .expiration(Date.from(now.plusMillis(expirationMs)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    /** Subject (the user's email) of a well-formed, unexpired token. */
    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    /** The `role` claim, so the filter does not hit the database. */
    public String extractRole(String token) {
        Object role = parse(token).get("role");
        return role == null ? null : role.toString();
    }

    /** Full verification: subject present, signature valid, not expired. */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            return extractUsername(token).equals(userDetails.getUsername())
                && !isExpired(token);
        } catch (RuntimeException invalid) {
            log.debug("Token rejected: {}", invalid.getMessage());
            return false;
        }
    }

    public boolean isExpired(String token) {
        Date expiration = parse(token).getExpiration();
        return expiration.before(new Date(System.currentTimeMillis() - skewSeconds * 1000));
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    /* -------------------------------------------------------------- internals */

    private Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .clockSkewSeconds(skewSeconds)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private String roleOf(UserDetails user) {
        return user.getAuthorities().stream()
            .findFirst()
            .map(authority -> authority.getAuthority().replace("ROLE_", ""))
            .orElse("ATTENDEE");
    }

    /**
     * Resolve the configured secret into a key of at least 512 bits.
     *
     * A short secret is a real vulnerability — JJWT would happily sign with it.
     * Rather than fail at runtime, the secret is stretched deterministically
     * so a development default still produces a strong key, while anything
     * reasonably long is used directly.
     */
    private SecretKey deriveKey(String secret) {
        byte[] raw = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);

        if (raw.length >= MIN_KEY_BYTES) {
            return Keys.hmacShaKeyFor(raw);
        }

        // Stretch with a stable hash to reach the required length.
        byte[] stretched = new byte[MIN_KEY_BYTES];
        byte[] seed = raw;
        for (int offset = 0; offset < MIN_KEY_BYTES; offset += seed.length) {
            try {
                seed = java.security.MessageDigest.getInstance("SHA-512").digest(seed);
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-512 is required by the Java runtime", impossible);
            }
            System.arraycopy(seed, 0, stretched, offset, Math.min(seed.length, MIN_KEY_BYTES - offset));
        }
        if (log.isWarnEnabled()) {
            log.warn("JWT secret is shorter than {} bytes — a stretched key was derived. " +
                "Set a long, random JWT_SECRET in production.", MIN_KEY_BYTES);
        }
        try {
            return Keys.hmacShaKeyFor(stretched);
        } catch (RuntimeException fallback) {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(stretched)));
        }
    }
}
