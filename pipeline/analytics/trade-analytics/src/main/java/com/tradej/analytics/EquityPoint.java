package com.tradej.analytics;

import java.math.BigDecimal;
import java.time.Instant;

/** Single point on an equity curve. */
public record EquityPoint(Instant timestamp, BigDecimal equityValue) {}
