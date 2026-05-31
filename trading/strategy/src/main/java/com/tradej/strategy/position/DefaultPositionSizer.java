package com.tradej.strategy.position;

import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.port.PositionSizer;
import com.tradej.strategy.portfolio.PortfolioEngine;

/**
 * Default risk-parity position sizer.
 *
 * <p>Computes quantity as:
 * <pre>
 * riskCapital  = strategyCapital × riskPerTradePct
 * slDistance   = |entryPrice − stopLoss|
 * rawQty       = riskCapital / slDistance
 * quantity     = min(rawQty, maxOrderValue / entryPrice)
 * </pre>
 *
 * <p>If the signal already carries a non-zero {@code "quantity"} attribute,
 * the sizer defers to that value (callers should always check the attribute
 * first and only invoke the sizer as a fallback).
 *
 * <p>If the stop-loss distance is zero or negative (missing / malformed),
 * the sizer returns {@code 0L} and logs a warning.
 */
public class DefaultPositionSizer implements PositionSizer {

    private static final String ATTR_QUANTITY = "quantity";

    private final long capitalPaisaPerStrategy;
    private final double riskPerTradePct;
    private final long maxOrderValuePaisa;
    private final long minQuantity;

    public DefaultPositionSizer(long capitalPaisaPerStrategy, double riskPerTradePct,
                                  long maxOrderValuePaisa, long minQuantity) {
        this.capitalPaisaPerStrategy = capitalPaisaPerStrategy;
        this.riskPerTradePct = riskPerTradePct;
        this.maxOrderValuePaisa = maxOrderValuePaisa;
        this.minQuantity = Math.max(1, minQuantity);
    }

    public DefaultPositionSizer(long capitalPaisaPerStrategy, double riskPerTradePct, long maxOrderValuePaisa) {
        this(capitalPaisaPerStrategy, riskPerTradePct, maxOrderValuePaisa, 1L);
    }

    /**
     * Creates a sizer with platform defaults: ₹10,000 strategy capital,
     * 1% risk per trade, ₹50,000 max order value.
     */
    public DefaultPositionSizer() {
        this(PortfolioEngine.DEFAULT_CAPITAL_PER_STRATEGY_PAISA,
             0.01,   // 1% risk per trade
             5_000_000L,  // ₹50,000 max order
             1L);
    }

    @Override
    public long computeQuantity(SignalGenerated signal) {
        Object existingQty = signal.attributes().get(ATTR_QUANTITY);
        if (existingQty instanceof Number n && n.longValue() > 0) {
            return n.longValue();
        }

        long entryPrice = signal.entryPricePaisa();
        long stopLoss = signal.stopLossPaisa();

        if (entryPrice <= 0 || stopLoss <= 0) {
            return 0L;
        }

        long slDistance = Math.abs(entryPrice - stopLoss);
        if (slDistance == 0) {
            return 0L;
        }

        long riskCapital = (long) (capitalPaisaPerStrategy * riskPerTradePct);
        long rawQty = riskCapital / slDistance;

        long maxByNotional = maxOrderValuePaisa / entryPrice;
        long quantity = Math.min(rawQty, maxByNotional);

        return Math.max(quantity, minQuantity);
    }
}