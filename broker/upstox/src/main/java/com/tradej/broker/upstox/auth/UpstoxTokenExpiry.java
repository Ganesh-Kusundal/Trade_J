package com.tradej.broker.upstox.auth;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Upstox access tokens expire daily at 3:30 AM IST regardless of issue time.
 */
public final class UpstoxTokenExpiry {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime EXPIRY_TIME = LocalTime.of(3, 30);

    private UpstoxTokenExpiry() {
    }

    public static long nextExpiryEpochMs() {
        return nextExpiryEpochMs(ZonedDateTime.now(IST));
    }

    static long nextExpiryEpochMs(ZonedDateTime nowIst) {
        ZonedDateTime expiryToday = ZonedDateTime.of(LocalDate.from(nowIst), EXPIRY_TIME, IST);
        if (!nowIst.isBefore(expiryToday)) {
            expiryToday = expiryToday.plusDays(1);
        }
        return expiryToday.toInstant().toEpochMilli();
    }
}
