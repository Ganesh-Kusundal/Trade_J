package com.tradej.broker.api.model;

import java.time.LocalTime;

public record MarketSessionPolicy(
        LocalTime openTime,
        LocalTime closeTime,
        boolean supportsLateSession
) {
}
