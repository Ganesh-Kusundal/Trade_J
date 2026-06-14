package com.tradej.app.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Generates and validates HMAC-SHA256 signed JWTs.
 *
 * <p>The signing secret is loaded from {@code trade.auth.jwt-secret} (a 32+ char
 * string). If unset, the secret is auto-generated on first boot and persisted to
 * {@code runtime/jwt-secret.txt} (chmod 600) so subsequent restarts can reuse it.
 *
 * <p>When the active profile is {@code prod} the secret MUST be supplied via
 * the {@code TRADE_JWT_SECRET} environment variable; the service fails fast on
 * a missing secret in that profile.
 */
@Service
public class JwtTokenService {

    public static final String CLAIM_USERNAME = "usr";

    private final SecretKey signingKey;
    private final long expirationSeconds;

    @org.springframework.beans.factory.annotation.Autowired
    public JwtTokenService(
            @Value("${trade.auth.jwt-secret:}") String secret,
            @Value("${trade.auth.jwt-expiration-seconds:86400}") long expirationSeconds
    ) {
        this.expirationSeconds = expirationSeconds;
        String resolved = resolveSecret(secret);
        this.signingKey = Keys.hmacShaKeyFor(resolved.getBytes(StandardCharsets.UTF_8));
    }

    /** Test-only constructor: takes a pre-resolved secret directly (bypasses auto-generation). */
    JwtTokenService(String resolvedSecret, long expirationSeconds, boolean unused) {
        this.expirationSeconds = expirationSeconds;
        this.signingKey = Keys.hmacShaKeyFor(resolvedSecret.getBytes(StandardCharsets.UTF_8));
    }

    private String resolveSecret(String configured) {
        if (configured != null && !configured.isBlank() && configured.length() >= 32) {
            return configured;
        }
        String activeProfile = System.getProperty("spring.profiles.active", "");
        if (activeProfile.contains("prod")) {
            throw new IllegalStateException(
                    "trade.auth.jwt-secret must be set in the prod profile "
                            + "(provide TRADE_JWT_SECRET with at least 32 characters)");
        }
        try {
            java.nio.file.Path runtimeDir = java.nio.file.Paths.get("runtime");
            java.nio.file.Files.createDirectories(runtimeDir);
            java.nio.file.Path secretFile = runtimeDir.resolve("jwt-secret.txt");
            if (java.nio.file.Files.exists(secretFile)) {
                String existing = java.nio.file.Files.readString(secretFile, StandardCharsets.UTF_8).trim();
                if (existing.length() >= 32) {
                    return existing;
                }
            }
            byte[] random = new byte[48];
            new java.security.SecureRandom().nextBytes(random);
            String generated = java.util.Base64.getEncoder().encodeToString(random);
            java.nio.file.Files.writeString(
                    secretFile, generated, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            java.nio.file.attribute.PosixFileAttributeView view =
                    java.nio.file.Files.getFileAttributeView(secretFile, java.nio.file.attribute.PosixFileAttributeView.class);
            if (view != null) {
                java.nio.file.Files.setPosixFilePermissions(secretFile, java.util.Set.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            }
            return generated;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to provision JWT signing secret", e);
        }
    }

    public String generateToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USERNAME, username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    /** Returns the validated claims, or {@code null} if the token is invalid. */
    public Claims validate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Jws<Claims> parsed = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return parsed.getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
