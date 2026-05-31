package com.tradej.broker.upstox.auth;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UpstoxTokenExpiryTest {

    @Test
    void expiryIsAfterNowWithinSameTradingDay() {
        ZonedDateTime morning = ZonedDateTime.of(2026, 5, 28, 9, 0, 0, 0, ZoneId.of("Asia/Kolkata"));
        long expiry = UpstoxTokenExpiry.nextExpiryEpochMs(morning);
        assertTrue(expiry > morning.toInstant().toEpochMilli());
    }
}
