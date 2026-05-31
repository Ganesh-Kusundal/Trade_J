package com.tradej.core.domain.port;

import com.tradej.core.domain.event.SignalGenerated;

/**
 * Port interface for computing trade quantity (position size) from a signal.
 *
 * <p>Position sizing is the primary lever for controlling risk per trade.
 * Implementations may use fixed quantity, risk-parity, ATR-based, or
 * volatility-scaled approaches. When a signal already carries an explicit
 * {@code "quantity"} attribute, the caller should prefer that value and
 * only invoke the sizer as a fallback.
 *
 * @see DefaultPositionSizer
 */
public interface PositionSizer {

    /**
     * Computes the quantity (in units/lots) for a trading signal.
     *
     * <p>The default implementation uses the signal's entry price and stop-loss
     * distance to derive a risk-based size:
     * <pre>
     * quantity = min(riskCapital / stopLossDistance, maxOrderValue / entryPrice)
     * riskCapital = strategyCapital * riskPerTradePct
     * </pre>
     *
     * @param signal the signal to size
     * @return positive quantity in units, or {@code 0L} if sizing cannot be
     *         performed (e.g., missing stop-loss or zero risk distance)
     */
    long computeQuantity(SignalGenerated signal);
}