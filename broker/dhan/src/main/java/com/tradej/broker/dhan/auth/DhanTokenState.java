package com.tradej.broker.dhan.auth;

public record DhanTokenState(
        String accessToken,
        long expiryEpochMs,
        long issuedAtEpochMs,
        String source
) {
}
