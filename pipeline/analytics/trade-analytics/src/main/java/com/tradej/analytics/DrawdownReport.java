package com.tradej.analytics;

import java.math.BigDecimal;
import java.util.List;

/** Summary of drawdown characteristics from an equity curve. */
public record DrawdownReport(
        BigDecimal maxDrawdownPct,
        BigDecimal maxDrawdownAbsolute,
        java.math.BigDecimal currentDrawdownPct,
        List<EquityPoint> underwaterEquityCurve,
        java.time.Duration maxDrawdownDuration
) {}
