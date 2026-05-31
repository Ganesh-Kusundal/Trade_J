package com.tradej.broker.upstox.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;

/**
 * Parses JWT {@code exp} claim from Upstox tokens without signature verification.
 */
public final class UpstoxJwtExpiry {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private UpstoxJwtExpiry() {
    }

    /**
     * @return expiry epoch millis, or {@code -1} if the token is not a JWT or has no {@code exp}
     */
    public static long parseExpiryEpochMs(String jwt) {
        if (jwt == null || jwt.isBlank()) {
            return -1L;
        }
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return -1L;
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            JsonNode payload = MAPPER.readTree(payloadBytes);
            if (payload.has("exp")) {
                return payload.get("exp").asLong() * 1000L;
            }
            return -1L;
        } catch (Exception ex) {
            return -1L;
        }
    }

    private static String padBase64(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }
        return value + "=".repeat(4 - remainder);
    }
}
