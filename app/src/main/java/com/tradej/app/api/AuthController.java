package com.tradej.app.api;

import com.tradej.app.security.JwtTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Map;

/**
 * Issues short-lived JWTs for the single-admin operator user.
 *
 * <p>Login credentials are: a configured username (default {@code admin}) and a
 * password loaded from {@code trade.auth.admin-password}. If the password is
 * unset, a random UUID is generated on first boot and persisted to
 * {@code runtime/auth-admin-password.txt} (chmod 600) so the operator can
 * read it once and store it in a secret manager.
 *
 * <p>{@code POST /api/v1/auth/refresh} accepts a still-valid JWT and returns a
 * freshly-signed one with a new expiration. Both endpoints are
 * permit-all in the security configuration — login is the way you GET a token.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtTokenService tokenService;
    private final String adminUsername;
    private final String adminPassword;

    public AuthController(
            JwtTokenService tokenService,
            @Value("${trade.auth.admin-username:admin}") String adminUsername,
            @Value("${trade.auth.admin-password:}") String adminPassword
    ) {
        this.tokenService = tokenService;
        this.adminUsername = adminUsername;
        this.adminPassword = resolvePassword(adminPassword);
    }

    private String resolvePassword(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String activeProfile = System.getProperty("spring.profiles.active", "");
        if (activeProfile.contains("prod")) {
            throw new IllegalStateException(
                    "trade.auth.admin-password must be set in the prod profile "
                            + "(provide TRADE_ADMIN_PASSWORD)");
        }
        try {
            Path runtimeDir = Path.of("runtime");
            Files.createDirectories(runtimeDir);
            Path passwordFile = runtimeDir.resolve("auth-admin-password.txt");
            if (Files.exists(passwordFile)) {
                String existing = Files.readString(passwordFile, StandardCharsets.UTF_8).trim();
                if (!existing.isEmpty()) {
                    return existing;
                }
            }
            byte[] random = new byte[24];
            new SecureRandom().nextBytes(random);
            String generated = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            Files.writeString(
                    passwordFile, generated, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            PosixFileAttributeView view = Files.getFileAttributeView(
                    passwordFile, PosixFileAttributeView.class);
            if (view != null) {
                Files.setPosixFilePermissions(passwordFile, java.util.Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE));
            }
            return generated;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to provision admin password", e);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest body) {
        if (!constantTimeEquals(body.username(), adminUsername)
                || !constantTimeEquals(body.password(), adminPassword)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        String token = tokenService.generateToken(body.username());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "expiresInSeconds", tokenService.getExpirationSeconds()
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@Valid @RequestBody RefreshRequest body) {
        var claims = tokenService.validate(body.token());
        if (claims == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
        String username = claims.get(JwtTokenService.CLAIM_USERNAME, String.class);
        if (username == null || username.isBlank()) {
            username = claims.getSubject();
        }
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject");
        }
        String token = tokenService.generateToken(username);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "expiresInSeconds", tokenService.getExpirationSeconds()
        ));
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(ab, bb);
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String token) {}
}
