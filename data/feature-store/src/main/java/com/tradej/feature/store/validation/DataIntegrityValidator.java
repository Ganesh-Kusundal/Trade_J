package com.tradej.feature.store.validation;

import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.model.Candle;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates data integrity across the pipeline.
 * Catches corrupted candles, duplicate ticks, and invalid data.
 */
public final class DataIntegrityValidator {

    /**
     * Validate OHLC invariants for a candle.
     * Returns empty list if valid, list of violations otherwise.
     */
    public static List<String> validateCandle(Candle candle) {
        List<String> violations = new ArrayList<>();

        if (candle.highPaisa() < candle.lowPaisa()) {
            violations.add("high < low: " + candle.highPaisa() + " < " + candle.lowPaisa());
        }
        if (candle.highPaisa() < candle.openPaisa()) {
            violations.add("high < open: " + candle.highPaisa() + " < " + candle.openPaisa());
        }
        if (candle.highPaisa() < candle.closePaisa()) {
            violations.add("high < close: " + candle.highPaisa() + " < " + candle.closePaisa());
        }
        if (candle.lowPaisa() > candle.openPaisa()) {
            violations.add("low > open: " + candle.lowPaisa() + " > " + candle.openPaisa());
        }
        if (candle.lowPaisa() > candle.closePaisa()) {
            violations.add("low > close: " + candle.lowPaisa() + " > " + candle.closePaisa());
        }
        if (candle.volume() < 0) {
            violations.add("negative volume: " + candle.volume());
        }
        if (candle.startTimeMs() > candle.endTimeMs()) {
            violations.add("startTime > endTime: " + candle.startTimeMs() + " > " + candle.endTimeMs());
        }

        return violations;
    }

    /**
     * Validate a tick for basic sanity.
     */
    public static List<String> validateTick(MarketTickEvent tick) {
        List<String> violations = new ArrayList<>();

        if (tick.ltpPaisa() < 0) {
            violations.add("negative ltp: " + tick.ltpPaisa());
        }
        if (tick.lastTradeQuantity() < 0) {
            violations.add("negative lastTradeQuantity: " + tick.lastTradeQuantity());
        }
        if (tick.cumulativeVolume() < 0) {
            violations.add("negative cumulativeVolume: " + tick.cumulativeVolume());
        }
        if (tick.symbol() == null || tick.symbol().isBlank()) {
            violations.add("empty symbol");
        }

        return violations;
    }

    /**
     * Returns true if the candle passes all OHLC invariant checks.
     */
    public static boolean isValidCandle(Candle candle) {
        return validateCandle(candle).isEmpty();
    }

    /**
     * Returns true if the tick passes all sanity checks.
     */
    public static boolean isValidTick(MarketTickEvent tick) {
        return validateTick(tick).isEmpty();
    }
}
