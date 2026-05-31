package com.tradej.broker.icici.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Checksum and timestamp generation aligned with the official Breeze Python SDK
 * ({@code breeze_connect.ApificationBreeze.generate_headers}).
 */
final class BreezeRequestSigner {

    private static final DateTimeFormatter UTC_TO_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

    private BreezeRequestSigner() {
    }

    /**
     * ISO-8601 UTC timestamp with zero milliseconds, e.g. {@code 2024-06-01T10:23:56.000Z}.
     * ICICI validates checksums against this format (not sub-second precision).
     */
    static String timestamp(Instant instant) {
        return UTC_TO_SECONDS.format(instant) + ".000Z";
    }

    static String checksum(String timestamp, String body, String secretKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((timestamp + body + secretKey).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    static String checksumHeaderValue(String timestamp, String body, String secretKey) {
        return "token " + checksum(timestamp, body, secretKey);
    }

    /**
     * Compact JSON body matching Python {@code json.dumps(body, separators=(',', ':'))}.
     */
    static String jsonBody(ObjectMapper objectMapper, ObjectNode payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize ICICI request body", ex);
        }
    }
}
