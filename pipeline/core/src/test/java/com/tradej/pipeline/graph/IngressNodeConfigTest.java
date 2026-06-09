package com.tradej.pipeline.graph;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import java.util.Optional;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
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
    void acceptsMarketTickEventWhenConfigured() {
        IngressNodeConfig config = IngressNodeConfig.fromMap(java.util.Map.of(
                "eventTypes", List.of("MarketTickEvent")
        ));
        MarketTickEvent tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 100L, 1L, 1L, 1000L, Optional.empty(), 0L, 0L);
        assertTrue(config.accepts(tick));
    }
}
