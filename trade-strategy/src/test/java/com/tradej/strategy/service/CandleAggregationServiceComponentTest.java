package com.tradej.strategy.service;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TickReceived;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Tag("component")
class CandleAggregationServiceComponentTest {
    @Test
    void rollsCandleAndEmitsClosedEventWhenBucketChanges() {
        CandleAggregationService service = new CandleAggregationService();
        List<DomainEvent> emitted = new ArrayList<>();

        long t0 = 1_710_000_000_000L;
        long t1 = t0 + 60_000L;
        long t2 = t0 + 300_000L;

        service.onDomainEvent(new TickReceived(EventMetadata.root(), "SBIN", "5m", 75_000L, 10L, 10L, t0, null), emitted::add);
        service.onDomainEvent(new TickReceived(EventMetadata.root(), "SBIN", "5m", 75_500L, 5L, 15L, t1, null), emitted::add);
        service.onDomainEvent(new TickReceived(EventMetadata.root(), "SBIN", "5m", 76_000L, 7L, 22L, t2, null), emitted::add);

        assertEquals(4, emitted.size());
        CandleDeveloping firstDeveloping = assertInstanceOf(CandleDeveloping.class, emitted.get(0));
        CandleDeveloping secondDeveloping = assertInstanceOf(CandleDeveloping.class, emitted.get(1));
        CandleClosed closed = assertInstanceOf(CandleClosed.class, emitted.get(2));
        CandleDeveloping nextDeveloping = assertInstanceOf(CandleDeveloping.class, emitted.get(3));

        assertEquals(75_000L, firstDeveloping.candle().openPaisa());
        assertEquals(75_500L, secondDeveloping.candle().closePaisa());
        assertEquals(75_500L, closed.candle().closePaisa());
        assertEquals(15L, closed.candle().volume());
        assertEquals(76_000L, nextDeveloping.candle().openPaisa());
    }
}
