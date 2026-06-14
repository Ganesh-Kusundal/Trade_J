package com.tradej.app.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class JwtTokenServiceTest {

    private static final String TEST_SECRET =
            "test-secret-with-at-least-32-characters-please-thanks";

    private JwtTokenService service;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<JwtTokenService> ctor =
                JwtTokenService.class.getDeclaredConstructor(String.class, long.class, boolean.class);
        ctor.setAccessible(true);
        service = ctor.newInstance(TEST_SECRET, 3600L, false);
    }

    @Test
    @DisplayName("generateToken returns a valid JWT")
    void generateTokenReturnsValidJwt() {
        String token = service.generateToken("admin");

        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3, "JWT must have three dot-separated segments");
        Claims claims = service.validate(token);
        assertNotNull(claims);
        assertEquals("admin", claims.get(JwtTokenService.CLAIM_USERNAME, String.class));
    }

    @Test
    @DisplayName("validateToken accepts a freshly issued token")
    void validateTokenAcceptsFreshToken() {
        String token = service.generateToken("admin");
        Claims claims = service.validate(token);

        assertNotNull(claims);
        assertEquals("admin", claims.getSubject());
    }

    @Test
    @DisplayName("validateToken rejects an expired token")
    void validateTokenRejectsExpiredToken() throws Exception {
        Constructor<JwtTokenService> ctor =
                JwtTokenService.class.getDeclaredConstructor(String.class, long.class, boolean.class);
        ctor.setAccessible(true);
        JwtTokenService shortLived = ctor.newInstance(TEST_SECRET, -10L, false);
        String token = shortLived.generateToken("admin");

        assertNull(service.validate(token), "Expired tokens must be rejected");
    }

    @Test
    @DisplayName("validateToken rejects a token with a tampered signature")
    void validateTokenRejectsTamperedSignature() {
        String token = service.generateToken("admin");
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
        String tampered = parts[0] + "." + parts[1] + "." + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

        assertNull(service.validate(tampered), "Tampered tokens must be rejected");
    }
}
