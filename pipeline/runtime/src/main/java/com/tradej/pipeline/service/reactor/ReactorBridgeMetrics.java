package com.tradej.pipeline.service.reactor;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.SignalGenerated;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public final class ReactorBridgeMetrics {

    private final LongAdder totalEvents = new LongAdder();
    private final LongAdder candleClosedEvents = new LongAdder();
    private final LongAdder signalGeneratedEvents = new LongAdder();
    private final LongAdder orderFilledEvents = new LongAdder();
    private final LongAdder otherEvents = new LongAdder();

    public void record(DomainEvent event) {
        if (event == null) {
            return;
        }
        totalEvents.increment();
        if (event instanceof CandleClosed) {
            candleClosedEvents.increment();
        } else if (event instanceof SignalGenerated) {
            signalGeneratedEvents.increment();
        } else if (event instanceof OrderFilled) {
            orderFilledEvents.increment();
        } else {
            otherEvents.increment();
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("totalEvents", totalEvents.sum());
        payload.put("candleClosedEvents", candleClosedEvents.sum());
        payload.put("signalGeneratedEvents", signalGeneratedEvents.sum());
        payload.put("orderFilledEvents", orderFilledEvents.sum());
        payload.put("otherEvents", otherEvents.sum());
        return payload;
    }
}
