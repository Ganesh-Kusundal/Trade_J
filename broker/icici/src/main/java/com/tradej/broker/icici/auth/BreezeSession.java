package com.tradej.broker.icici.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record BreezeSession(
        String userId,
        String sessionKey,
        String base64SessionToken,
        long issuedAtEpochMs,
        long expiresAtEpochMs
) {
    public static BreezeSession fromEncodedToken(String base64SessionToken, long issuedAtEpochMs, long expiresAtEpochMs) {
        String decoded = new String(Base64.getDecoder().decode(base64SessionToken), StandardCharsets.US_ASCII);
        int separator = decoded.indexOf(':');
        if (separator <= 0 || separator >= decoded.length() - 1) {
            throw new IllegalArgumentException("Invalid ICICI session token format");
        }
        return new BreezeSession(
                decoded.substring(0, separator),
                decoded.substring(separator + 1),
                base64SessionToken,
                issuedAtEpochMs,
                expiresAtEpochMs
        );
    }

    /** Session token for {@code X-SessionToken}, matching Python SDK re-encoding of {@code user_id:session_key}. */
    public String headerSessionToken() {
        String raw = userId + ":" + sessionKey;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.US_ASCII));
    }

    public static BreezeSession fromUserAndKey(
            String userId,
            String sessionKey,
            long issuedAtEpochMs,
            long expiresAtEpochMs
    ) {
        String raw = userId + ":" + sessionKey;
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.US_ASCII));
        return new BreezeSession(userId, sessionKey, encoded, issuedAtEpochMs, expiresAtEpochMs);
    }
}
