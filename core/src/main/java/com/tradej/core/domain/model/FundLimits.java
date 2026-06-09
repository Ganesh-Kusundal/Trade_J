package com.tradej.core.domain.model;

import java.math.BigDecimal;

/**
 * Fund limits representing available balance and margin utilization.
 */
public record FundLimits(
    BigDecimal availableBalance,
    BigDecimal utilizedMargin,
    BigDecimal totalLimit
) {
}
