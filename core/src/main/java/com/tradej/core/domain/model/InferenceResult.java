package com.tradej.core.domain.model;

import com.tradej.core.domain.value.Side;

/**
 * Result of running a model inference on a {@link FeatureVector}.
 *
 * <p>Contains the trading decision (side, entry price, confidence) produced
 * by an ML model or a rule-based inference engine.
 *
 * @param side             predicted trading side (BUY / SELL)
 * @param quantity         suggested trade quantity (lots/shares)
 * @param entryPricePaisa  suggested entry price in paisa
 * @param stopLossPaisa    suggested stop-loss price in paisa
 * @param takeProfitPaisa  suggested take-profit price in paisa
 * @param confidence       model confidence in the prediction (0.0 – 1.0)
 * @param setup            human-readable label describing the signal regime
 */
public record InferenceResult(
        Side side,
        int quantity,
        long entryPricePaisa,
        long stopLossPaisa,
        long takeProfitPaisa,
        double confidence,
        String setup
) {
}
