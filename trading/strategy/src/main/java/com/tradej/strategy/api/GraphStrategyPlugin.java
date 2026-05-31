package com.tradej.strategy.api;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.SignalGenerated;

import java.util.List;
import java.util.Optional;

/**
 * Strategy plugin that receives arbitrary {@link DomainEvent} types for
 * tick-level, depth-level, and multi-event strategy evaluation.
 * <p>
 * Unlike {@link StrategyPlugin} which only receives {@code CandleClosed},
 * this interface supports:
 * <ul>
 *   <li>Tick strategies (e.g., market-making, tick-IM balance)</li>
 *   <li>Depth strategies (e.g., L2 imbalance, order book pressure)</li>
 *   <li>Multi-event strategies (e.g., tick + depth + candle combined)</li>
 *   <li>Reactive streaming evaluation</li>
 * </ul>
 * <p>
 * Plugins declare their subscribed event types via {@link #subscribedEventTypes()}
 * so the graph runtime can route only relevant events.
 */
public interface GraphStrategyPlugin {

    /** Human-readable strategy name for logging and attribution. */
    String name();

    /**
     * Returns the event types this plugin wants to receive.
     * Only events matching these types are routed to {@link #onEvent}.
     */
    List<Class<? extends DomainEvent>> subscribedEventTypes();

    /**
     * Evaluate an incoming event and optionally produce a signal.
     * Called from the pipeline graph runtime on the hot path or in the sandbox.
     *
     * @param event the incoming domain event (tick, depth update, candle, etc.)
     * @return a signal if the strategy triggers, empty otherwise
     */
    Optional<SignalGenerated> onEvent(DomainEvent event);

    /**
     * Called when the strategy is registered into the runtime.
     * Use for initialization (e.g., subscribing to data feeds, allocating state).
     */
    default void onStart() {
    }

    /**
     * Called when the strategy is removed from the runtime.
     * Use for cleanup (e.g., releasing resources, persisting state).
     */
    default void onStop() {
    }
}
