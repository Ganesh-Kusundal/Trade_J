package com.tradej.strategy.api;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.SignalGenerated;

import java.util.Optional;

/**
 * Service Provider Interface for pluggable strategy plugins.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}.
 * To register a new strategy:
 * <ol>
 *   <li>Implement this interface</li>
 *   <li>Create {@code META-INF/services/com.tradej.strategy.api.StrategyPluginProvider}
 *       containing the fully qualified class name</li>
 * </ol>
 */
public interface StrategyPluginProvider {

    /**
     * Unique name for this strategy (e.g. "breakout", "mean-reversion").
     */
    String name();

    /**
     * Human-readable display name.
     */
    default String displayName() {
        return name();
    }

    /**
     * Process a closed candle and optionally generate a trading signal.
     *
     * @param candleClosed the closed candle event
     * @return a signal if the strategy triggers, empty otherwise
     */
    Optional<SignalGenerated> onCandleClosed(CandleClosed candleClosed);

    /**
     * Whether this strategy is currently enabled.
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Semantic version of this strategy plugin.
     */
    default String version() {
        return "1.0.0";
    }
}
