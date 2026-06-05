package com.tradej.core.testing;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Centralized factory for creating domain events and models in tests.
 * All methods are deterministic when given deterministic inputs (IDs, timestamps).
 * <p>
 * Usage:
 * <pre>{@code
 * var tick = EventFactories.tick("RELIANCE", 250000L, fixedClock);
 * var candle = EventFactories.candleClosed("RELIANCE", 250000L, fixedClock);
 * var signal = EventFactories.signal("RELIANCE", Side.BUY, fixedClock);
 * }</pre>
 */
public final class EventFactories {

    private EventFactories() {
    }

    // -- Market Tick --

    public static MarketTickEvent tick(String symbol, long ltpPaisa, TestClock clock) {
        return tick(symbol, ltpPaisa, 1L, 100L, clock);
    }

    public static MarketTickEvent tick(String symbol, long ltpPaisa, long qty, long volume, TestClock clock) {
        return new MarketTickEvent(
                new EventMetadata("tick-" + symbol + "-" + ltpPaisa, clock.instant().toEpochMilli(),
                        clock.monotonicNanos(), 0L, "", 1),
                0L,
                symbol,
                ExchangeSegment.NSE_EQ,
                FeedMode.TICKER,
                ltpPaisa,
                qty,
                volume,
                clock.instant().toEpochMilli(),
                Optional.empty(), 0L, 0L
        );
    }

    public static MarketTickEvent tickWithDepth(String symbol, long ltpPaisa, TestClock clock,
                                                 MarketDepth depth) {
        return new MarketTickEvent(
                new EventMetadata("tick-" + symbol + "-" + ltpPaisa, clock.instant().toEpochMilli(),
                        clock.monotonicNanos(), 0L, "", 1),
                0L,
                symbol,
                ExchangeSegment.NSE_EQ,
                FeedMode.DEPTH_20,
                ltpPaisa,
                1L,
                100L,
                clock.instant().toEpochMilli(),
                Optional.of(depth), 0L, 0L
        );
    }

    // -- Candle --

    public static CandleClosed candleClosed(String symbol, long closePaisa, TestClock clock) {
        return candleClosed(symbol, closePaisa, 1000L, clock);
    }

    public static CandleClosed candleClosed(String symbol, long closePaisa, long volume, TestClock clock) {
        long startMs = clock.instant().toEpochMilli();
        return new CandleClosed(
                new EventMetadata("candle-" + symbol, clock.instant().toEpochMilli(),
                        clock.monotonicNanos(), 0L, "", 1),
                new Candle(
                        symbol,
                        "5m",
                        startMs,
                        startMs + 300_000L,
                        closePaisa,
                        closePaisa,
                        closePaisa - 5_000L,
                        closePaisa,
                        volume,
                        true
                )
        );
    }

    public static CandleClosed candleFromTicks(String symbol, String interval,
                                                long openPaisa, long highPaisa, long lowPaisa,
                                                long closePaisa, long volume,
                                                long startTimeMs, long endTimeMs,
                                                TestClock clock) {
        return new CandleClosed(
                new EventMetadata("candle-" + symbol + "-" + interval, clock.instant().toEpochMilli(),
                        clock.monotonicNanos(), 0L, "", 1),
                new Candle(
                        symbol,
                        interval,
                        startTimeMs,
                        endTimeMs,
                        openPaisa,
                        highPaisa,
                        lowPaisa,
                        closePaisa,
                        volume,
                        true
                )
        );
    }

    // -- Signal --

    public static SignalGenerated signal(String symbol, Side side, TestClock clock) {
        return signal(symbol, side, 250000L, clock);
    }

    public static SignalGenerated signal(String symbol, Side side, long entryPricePaisa, TestClock clock) {
        return new SignalGenerated(
                new EventMetadata("sig-" + symbol, clock.instant().toEpochMilli(),
                        clock.monotonicNanos(), 0L, "", 1),
                "sig-" + symbol + "-" + clock.instant().toEpochMilli(),
                symbol,
                "5m",
                side,
                entryPricePaisa,
                side == Side.BUY ? entryPricePaisa - 5_000L : entryPricePaisa + 5_000L,
                side == Side.BUY ? entryPricePaisa + 10_000L : entryPricePaisa - 10_000L,
                "test-setup",
                Map.of()
        );
    }

    public static SignalSuppressed signalSuppressed(String symbol, String reason, TestClock clock) {
        String signalId = "suppressed-" + symbol + "-" + clock.instant().toEpochMilli();
        return new SignalSuppressed(
                new EventMetadata(signalId, clock.instant().toEpochMilli(),
                        clock.monotonicNanos(), 0L, "", 1),
                signalId,
                symbol,
                reason,
                Map.of()
        );
    }

    // -- Order --

    public static Order order(String orderId, String symbol, Side side, long quantity, long pricePaisa) {
        return new Order(
                orderId,
                null, // correlationId
                symbol,
                ExchangeSegment.NSE_EQ,
                side,
                ProductType.INTRADAY,
                OrderType.LIMIT,
                OrderStatus.PENDING,
                quantity,
                0L, // filledQuantity
                pricePaisa,
                0L, // triggerPricePaisa
                System.currentTimeMillis(),
                null // rejectionReason
        );
    }

    public static OrderAccepted orderAccepted(String orderId, String symbol, TestClock clock) {
        return new OrderAccepted(
                new EventMetadata("oa-" + orderId, clock.instant().toEpochMilli(),
                        clock.monotonicNanos(), 0L, "", 1),
                order(orderId, symbol, Side.BUY, 1L, 250000L)
        );
    }

    public static OrderFilled orderFilled(String orderId, String symbol, long fillPricePaisa,
                                           long fillQty, TestClock clock) {
        return new OrderFilled(
                new EventMetadata("of-" + orderId, clock.instant().toEpochMilli(),
                        clock.monotonicNanos(), 0L, "", 1),
                order(orderId, symbol, Side.BUY, fillQty, fillPricePaisa),
                List.of(trade("trade-" + orderId, orderId, symbol, fillQty, fillPricePaisa))
        );
    }

    // -- Trade --

    public static Trade trade(String tradeId, String orderId, String symbol,
                               long size, long entryPricePaisa) {
        return new Trade(
                tradeId,
                orderId,
                symbol,
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                size,
                entryPricePaisa,
                System.currentTimeMillis()
        );
    }

    public static TradeOpened tradeOpened(String tradeId, String orderId, String symbol,
                                           Side side, long size, long entryPricePaisa,
                                           TestClock clock) {
        return new TradeOpened(
                new EventMetadata("to-" + tradeId, clock.instant().toEpochMilli(),
                        clock.monotonicNanos(), 0L, "", 1),
                tradeId,
                orderId,
                null,
                symbol,
                side,
                size,
                entryPricePaisa,
                side == Side.BUY ? entryPricePaisa - 5_000L : entryPricePaisa + 5_000L,
                side == Side.BUY ? entryPricePaisa + 10_000L : entryPricePaisa - 10_000L
        );
    }
}
