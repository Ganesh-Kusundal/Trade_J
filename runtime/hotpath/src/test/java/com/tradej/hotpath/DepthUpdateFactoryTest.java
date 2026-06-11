package com.tradej.hotpath;

import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class DepthUpdateFactoryTest {

    @Test
    void returnsNullWhenTickHasNoDepth() {
        MarketTickEvent tick = tick(Optional.empty());

        DepthUpdateEvent event = DepthUpdateFactory.fromMarketTickEvent(tick);

        assertNull(event);
    }

    @Test
    void returnsNullWhenDepthHasNoInstrument() {
        MarketTickEvent tick = tick(Optional.of(new MarketDepth(null, List.of(), List.of(), 0, 1_000L)));

        DepthUpdateEvent event = DepthUpdateFactory.fromMarketTickEvent(tick);

        assertNull(event);
    }

    @Test
    void returnsNullWhenDepthBookIsEmpty() {
        MarketTickEvent tick = tick(Optional.of(new MarketDepth(
                new Instrument("SBIN", "SBIN", null, ExchangeSegment.NSE_EQ, "EQ", null, null, null, null, 1L, 100L),
                List.of(),
                List.of(),
                0,
                1_000L
        )));

        DepthUpdateEvent event = DepthUpdateFactory.fromMarketTickEvent(tick);

        assertNull(event);
    }

    @Test
    void createsDepthUpdateWhenDepthBookHasBidsOrAsks() {
        DepthLevel bid = new DepthLevel(749_00L, 100L, 2);
        MarketTickEvent tick = tick(Optional.of(new MarketDepth(
                new Instrument("SBIN", "SBIN", null, ExchangeSegment.NSE_EQ, "EQ", null, null, null, null, 1L, 100L),
                List.of(bid),
                List.of(),
                1,
                1_000L
        )));

        DepthUpdateEvent event = DepthUpdateFactory.fromMarketTickEvent(tick);

        assertNotNull(event);
        assertEquals("SBIN", event.symbol());
        assertEquals(ExchangeSegment.NSE_EQ, event.segment());
        assertEquals(List.of(bid), event.bids());
        assertEquals(List.of(), event.asks());
        assertEquals(1, event.levels());
        assertEquals(tick.exchangeTimestampEpochMs(), event.exchangeTimestampMs());
    }

    private static MarketTickEvent tick(Optional<MarketDepth> depth) {
        return new MarketTickEvent(
                EventMetadata.root(),
                1L,
                "SBIN",
                ExchangeSegment.NSE_EQ,
                FeedMode.TICKER,
                750_00L,
                10L,
                10L,
                1_000L,
                depth,
                2_000L,
                3_000L
        );
    }
}
