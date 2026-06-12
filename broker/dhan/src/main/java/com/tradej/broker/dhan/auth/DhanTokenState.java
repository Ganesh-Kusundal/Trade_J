package com.tradej.broker.dhan.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @param accessToken      the Dhan access token (JWT)
 * @param expiryEpochMs    broker-reported token expiry in epoch milliseconds
 * @param issuedAtEpochMs  time the token was issued (epoch millis)
 * @param source           logical source — see {@code TokenSource} values
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record DhanTokenState(
        String accessToken,
        long expiryEpochMs,
        long issuedAtEpochMs,
        String source
) {
}
