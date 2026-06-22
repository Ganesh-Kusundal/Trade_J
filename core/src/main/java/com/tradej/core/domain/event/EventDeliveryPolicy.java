package com.tradej.core.domain.event;

import com.tradej.core.domain.config.TradeDefaults;

/**
 * Explicit event bus delivery/backpressure contract.
 */
public record EventDeliveryPolicy(
        int downstreamQueueCapacity,
        int dispatchQueueCapacity,
        OverflowStrategy overflowStrategy
) {

    public enum OverflowStrategy {
        DEAD_LETTER_AND_DROP
    }

    public EventDeliveryPolicy {
        if (downstreamQueueCapacity <= 0) {
            throw new IllegalArgumentException("downstreamQueueCapacity must be positive");
        }
        if (dispatchQueueCapacity <= 0) {
            throw new IllegalArgumentException("dispatchQueueCapacity must be positive");
        }
        if (overflowStrategy == null) {
            overflowStrategy = OverflowStrategy.DEAD_LETTER_AND_DROP;
        }
    }

    public static EventDeliveryPolicy defaults() {
        return new EventDeliveryPolicy(
                TradeDefaults.EVENT_DOWNSTREAM_QUEUE_CAPACITY,
                TradeDefaults.EVENT_DISPATCH_QUEUE_CAPACITY,
                OverflowStrategy.DEAD_LETTER_AND_DROP
        );
    }
}
