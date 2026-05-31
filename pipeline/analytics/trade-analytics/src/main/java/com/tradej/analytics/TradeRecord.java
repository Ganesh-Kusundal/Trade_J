package com.tradej.analytics;

import java.math.BigDecimal;
import java.time.Instant;

/** Canonical trade record consumed by analytics engines. */
public record TradeRecord(
        String tradeId,
        String symbol,
        Instant entryTime,
        Instant exitTime,
        BigDecimal entryPrice,
        BigDecimal exitPrice,
        BigDecimal quantity,
        BigDecimal pnl,
        BigDecimal returnPct,
        String direction
) {}
