package com.tradej.app.api.dto;

import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.value.ExchangeSegment;

/**
 * View-model for a strategy signal surfaced to the REST/WebSocket clients.
 *
 * <p>The {@code type} field is normalised into the four directions the
 * {@code StrategyVisualization} panel cares about:
 * {@code ENTRY_LONG}, {@code ENTRY_SHORT}, {@code EXIT_LONG}, {@code EXIT_SHORT}.
 * A {@code null} {@code type} is reserved for raw {@link SignalGenerated}
 * rows that have not been classified yet.
 *
 * <p>All prices are denominated in <em>paisa</em> (i.e. INR × 100) to
 * stay consistent with the rest of the API surface.
 */
public record StrategySignalResponse(
        long timestamp,
        String type,
        long price,
        long quantity,
        String strategyName,
        Long pnl,
        String symbol,
        String exchange
) {

    public static StrategySignalResponse fromSignal(SignalGenerated signal, String type) {
        return new StrategySignalResponse(
                signal.metadata() != null ? signal.metadata().timestampMs() : 0L,
                type,
                signal.entryPricePaisa(),
                0L, // SignalGenerated does not carry a quantity field
                strategyName(signal),
                null,
                signal.symbol(),
                resolveExchange(signal.symbol())
        );
    }

    private static String strategyName(SignalGenerated signal) {
        if (signal.attributes() == null) {
            return "";
        }
        Object name = signal.attributes().get("strategyName");
        return name == null ? "" : name.toString();
    }

    private static String resolveExchange(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return ExchangeSegment.UNKNOWN.name();
        }
        return ExchangeSegment.fromCode(symbol).name();
    }
}
