package com.tradej.broker.dhan.auth;

public record DhanTokenInfo(
        boolean valid,
        long expiryEpochMs,
        boolean refreshRecommended
) {
}
