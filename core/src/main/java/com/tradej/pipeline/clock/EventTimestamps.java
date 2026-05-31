package com.tradej.pipeline.clock;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.TickReceived;

/**
 * Extracts exchange-time or event-time timestamps for deterministic replay clock advancement.
 */
public final class EventTimestamps {

    private EventTimestamps() {
    }

    public static long exchangeOrEventTimeMs(DomainEvent event) {
        if (event instanceof TickReceived tick) {
            if (tick.exchangeTimestampMs() > 0L) {
                return tick.exchangeTimestampMs();
            }
        }
        if (event instanceof CandleDeveloping developing) {
            return developing.candle().endTimeMs();
        }
        if (event instanceof CandleClosed closed) {
            return closed.candle().endTimeMs();
        }
        return event.timestampMs();
    }
}
