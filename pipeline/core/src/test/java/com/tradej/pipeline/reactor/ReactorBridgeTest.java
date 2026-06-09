package com.tradej.pipeline.reactor;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.MarketTickEvent;
import java.util.Optional;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import com.tradej.core.domain.value.Side;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class ReactorBridgeTest {

    @Test
    void coldPathCandidatesIncludeSelectedDomainEvents() {
        CandleClosed candleClosed = new CandleClosed(
                EventMetadata.root(),
                new Candle("SBIN", "5m", 1L, 2L, 1L, 2L, 1L, 2L, 10L, true)
        );
        SignalGenerated signal = new SignalGenerated(
                EventMetadata.root(),
                "sig-1",
                "SBIN",
                "5m",
                Side.BUY,
                100L,
                90L,
                110L,
                "test",
                Map.of()
        );
        Order order = new com.tradej.core.domain.model.Order(
                "ord-1", "sig-1", "SBIN",
                com.tradej.core.domain.value.ExchangeSegment.NSE_EQ,
                Side.BUY,
                com.tradej.core.domain.value.ProductType.INTRADAY,
                com.tradej.core.domain.value.OrderType.MARKET,
                com.tradej.core.domain.value.OrderStatus.TRADED,
                10L, 10L, 100L, 0L, 1000L, ""
        );
        OrderFilled filled = new OrderFilled(EventMetadata.root(), order, List.of());
        MarketTickEvent tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 100L, 1L, 1L, 1000L, Optional.empty(), 0L, 0L);

        assertTrue(ReactorBridge.isColdPathCandidate(candleClosed));
        assertTrue(ReactorBridge.isColdPathCandidate(signal));
        assertTrue(ReactorBridge.isColdPathCandidate(filled));
        assertFalse(ReactorBridge.isColdPathCandidate(tick));
    }
}
