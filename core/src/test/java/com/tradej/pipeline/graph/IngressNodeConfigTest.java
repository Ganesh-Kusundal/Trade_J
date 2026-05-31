package com.tradej.pipeline.graph;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class IngressNodeConfigTest {

    @Test
    void acceptsConfiguredCandleClosedEvents() {
        IngressNodeConfig config = new IngressNodeConfig(
                List.of("CandleClosed"),
                List.of("5m"),
                List.of("SBIN")
        );
        CandleClosed event = new CandleClosed(
                EventMetadata.root(),
                new Candle("SBIN", "5m", 1L, 2L, 1L, 2L, 1L, 2L, 10L, true)
        );
        assertTrue(config.accepts(event));
    }

    @Test
    void rejectsMismatchedInterval() {
        IngressNodeConfig config = new IngressNodeConfig(
                List.of("CandleClosed"),
                List.of("1m"),
                List.of()
        );
        CandleClosed event = new CandleClosed(
                EventMetadata.root(),
                new Candle("SBIN", "5m", 1L, 2L, 1L, 2L, 1L, 2L, 10L, true)
        );
        assertFalse(config.accepts(event));
    }

    @Test
    void acceptsTickReceivedWhenConfigured() {
        IngressNodeConfig config = IngressNodeConfig.fromMap(java.util.Map.of(
                "eventTypes", List.of("TickReceived")
        ));
        TickReceived tick = new TickReceived(
                EventMetadata.root(), "SBIN", "5m", 100L, 1L, 1L, 1000L, null
        );
        assertTrue(config.accepts(tick));
    }
}
