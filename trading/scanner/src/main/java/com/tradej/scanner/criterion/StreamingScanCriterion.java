package com.tradej.scanner.criterion;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.scanner.model.ScanContext;

/**
 * Extension of {@link ScanCriterion} that supports incremental, event-driven evaluation.
 * <p>
 * Unlike the batch-oriented base interface which evaluates a snapshot,
 * streaming criteria maintain internal state updated on each event and
 * can trigger matches reactively.
 */
public interface StreamingScanCriterion extends ScanCriterion {

    /**
     * Feed an incoming event to this criterion for incremental evaluation.
     * Called on every matching event type from the pipeline graph.
     *
     * @param event   the domain event (tick, depth update, candle, etc.)
     * @param context the scan context for this symbol
     */
    void onEvent(DomainEvent event, ScanContext context);

    /**
     * Returns the event types this criterion should receive.
     * Only events matching these types are routed to {@link #onEvent}.
     */
    java.util.List<Class<? extends DomainEvent>> subscribedEventTypes();

    /**
     * Reset all accumulated state. Called when a scan cycle completes or
     * when the criterion is re-registered.
     */
    default void reset() {
    }
}
