package com.tradej.app.pipeline;

import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.persistence.replay.HistoricalRangeService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Replay parity: production replay must drive the same {@link MarketTickEvent} type as live brokers.
 */
@Tag("unit")
class ReplayMarketTickParityTest {

    @Test
    void historicalRangeServiceExposesMarketTickReplay() throws NoSuchMethodException {
        assertNotNull(HistoricalRangeService.class.getMethod(
                "replayMarketTicks", String.class, long.class, long.class, EventBus.class));
    }

    @Test
    void marketTickAndTickReceivedAreDistinctEventTypes() {
        assertNotEquals(MarketTickEvent.class, TickReceived.class);
        assertEquals("MarketTickEvent", MarketTickEvent.class.getSimpleName());
    }

    @Test
    void marketTickEventCarriesExchangeSegment() {
        var tick = new MarketTickEvent(
                com.tradej.core.domain.event.EventMetadata.root(),
                0L,
                "SBIN",
                ExchangeSegment.NSE_EQ,
                FeedMode.TICKER,
                100_00L,
                1L,
                10L,
                System.currentTimeMillis(),
                java.util.Optional.empty(),
                0L,
                0L
        );
        assertEquals(ExchangeSegment.NSE_EQ, tick.segment());
    }
}
