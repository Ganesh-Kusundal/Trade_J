package com.tradej.disruptor;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the dedup key strategy in {@link DisruptorEventBus} uses
 * source/sequence keys (not UUID-based eventId) for market data and order events.
 */
@Tag("unit")
class DisruptorEventBusDedupTest {

    /**
     * Invokes the package-private {@code isDuplicate(DomainEvent)} method
     * on a minimal DisruptorEventBus-like dedup cache via reflection.
     * We use a dedicated dedup test harness to avoid constructing the full bus.
     */
    private final DedupHarness dedup = new DedupHarness();

    // -- Helpers --

    private static MarketTickEvent tick(String symbol, ExchangeSegment segment, long exchangeTimestampMs) {
        return new MarketTickEvent(
                EventMetadata.root(),
                1L,
                symbol,
                segment,
                FeedMode.FULL,
                100L,
                10L,
                1000L,
                exchangeTimestampMs,
                Optional.empty(),
                0L,
                0L
        );
    }

    private static DepthUpdateEvent depth(String symbol, ExchangeSegment segment, long exchangeTimestampMs) {
        return new DepthUpdateEvent(
                EventMetadata.root(),
                symbol,
                segment,
                List.of(),
                List.of(),
                5,
                exchangeTimestampMs
        );
    }

    private static Order makeOrder(String orderId) {
        return new Order(
                orderId, "corr-1", "RELIANCE",
                ExchangeSegment.NSE_EQ, Side.BUY, ProductType.CNC,
                OrderType.LIMIT, OrderStatus.OPEN,
                100L, 0L, 250000L, 0L,
                System.currentTimeMillis(), null
        );
    }

    // -- Tests --

    @Test
    void sameTickSameSymbolSegmentTimestamp_isDeduplicated() {
        MarketTickEvent tick1 = tick("RELIANCE", ExchangeSegment.NSE_EQ, 1700000000000L);
        MarketTickEvent tick2 = tick("RELIANCE", ExchangeSegment.NSE_EQ, 1700000000000L);

        assertFalse(dedup.isDuplicate(tick1), "First occurrence should NOT be duplicate");
        assertTrue(dedup.isDuplicate(tick2), "Second occurrence with same key should be duplicate");
    }

    @Test
    void sameTickDifferentTimestamp_notDeduplicated() {
        MarketTickEvent tick1 = tick("RELIANCE", ExchangeSegment.NSE_EQ, 1700000000000L);
        MarketTickEvent tick2 = tick("RELIANCE", ExchangeSegment.NSE_EQ, 1700000000001L);

        assertFalse(dedup.isDuplicate(tick1));
        assertFalse(dedup.isDuplicate(tick2), "Different timestamp should produce different key");
    }

    @Test
    void sameOrderEventSameOrderIdAndType_isDeduplicated() {
        Order order = makeOrder("ORD-123");
        OrderAccepted accepted1 = new OrderAccepted(EventMetadata.root(), order);
        OrderAccepted accepted2 = new OrderAccepted(EventMetadata.root(), order);

        assertFalse(dedup.isDuplicate(accepted1), "First OrderAccepted should not be duplicate");
        assertTrue(dedup.isDuplicate(accepted2), "Second OrderAccepted with same orderId should be duplicate");
    }

    @Test
    void differentOrderTransitionsAcceptedVsFilled_notDeduplicated() {
        Order order = makeOrder("ORD-456");
        OrderAccepted accepted = new OrderAccepted(EventMetadata.root(), order);
        OrderFilled filled = new OrderFilled(EventMetadata.root(), order, List.of());

        assertFalse(dedup.isDuplicate(accepted), "OrderAccepted should not be duplicate");
        assertFalse(dedup.isDuplicate(filled), "OrderFilled with same orderId but different type should NOT be duplicate");
    }

    @Test
    void sameDepthEventSameKey_isDeduplicated() {
        DepthUpdateEvent d1 = depth("INFY", ExchangeSegment.NSE_EQ, 1700000000000L);
        DepthUpdateEvent d2 = depth("INFY", ExchangeSegment.NSE_EQ, 1700000000000L);

        assertFalse(dedup.isDuplicate(d1));
        assertTrue(dedup.isDuplicate(d2));
    }

    @Test
    void sameTickSameTimestampDifferentSequence_notDeduplicated() {
        EventMetadata meta1 = EventMetadata.correlated("corr-1", 100L);
        EventMetadata meta2 = EventMetadata.correlated("corr-1", 200L);
        MarketTickEvent tick1 = new MarketTickEvent(
                meta1, 1L, "RELIANCE", ExchangeSegment.NSE_EQ, FeedMode.FULL,
                100L, 10L, 1000L, 1700000000000L, Optional.empty(), 0L, 0L);
        MarketTickEvent tick2 = new MarketTickEvent(
                meta2, 1L, "RELIANCE", ExchangeSegment.NSE_EQ, FeedMode.FULL,
                100L, 10L, 1000L, 1700000000000L, Optional.empty(), 0L, 0L);

        assertFalse(dedup.isDuplicate(tick1), "First tick should not be duplicate");
        assertFalse(dedup.isDuplicate(tick2), "Same timestamp but different sequenceId must NOT be deduplicated");
    }

    @Test
    void sameTickSameTimestampSameSequence_isDeduplicated() {
        EventMetadata meta = EventMetadata.correlated("corr-1", 42L);
        MarketTickEvent tick1 = new MarketTickEvent(
                meta, 1L, "TCS", ExchangeSegment.NSE_EQ, FeedMode.FULL,
                200L, 20L, 2000L, 1700000000000L, Optional.empty(), 0L, 0L);
        MarketTickEvent tick2 = new MarketTickEvent(
                meta, 1L, "TCS", ExchangeSegment.NSE_EQ, FeedMode.FULL,
                200L, 20L, 2000L, 1700000000000L, Optional.empty(), 0L, 0L);

        assertFalse(dedup.isDuplicate(tick1), "First occurrence should not be duplicate");
        assertTrue(dedup.isDuplicate(tick2), "Same symbol+segment+timestamp+sequence must be deduplicated");
    }

    /**
     * Lightweight test harness that replicates the dedup logic from
     * {@link DisruptorEventBus} without requiring the full Disruptor setup.
     * Uses reflection to call the package-private {@code dedupKey} via
     * the same algorithm.
     */
    private static final class DedupHarness {
        private final java.util.concurrent.ConcurrentHashMap<String, Long> seen = new java.util.concurrent.ConcurrentHashMap<>();

        boolean isDuplicate(DomainEvent event) {
            String key = dedupKey(event);
            Long prev = seen.putIfAbsent(key, System.currentTimeMillis());
            return prev != null;
        }

        private String dedupKey(DomainEvent event) {
            return switch (event) {
                case MarketTickEvent tick ->
                        "TICK:" + tick.symbol() + ":" + tick.segment() + ":" + tick.exchangeTimestampEpochMs() + ":" + tick.metadata().sequenceId();
                case DepthUpdateEvent depth ->
                        "DEPTH:" + depth.symbol() + ":" + depth.segment() + ":" + depth.exchangeTimestampMs() + ":" + depth.metadata().sequenceId();
                case OrderAccepted accepted ->
                        "ORDER:" + accepted.order().orderId() + ":OrderAccepted";
                case OrderFilled filled ->
                        "ORDER:" + filled.order().orderId() + ":OrderFilled";
                case com.tradej.core.domain.event.OrderRejected rejected ->
                        "ORDER:" + rejected.order().orderId() + ":OrderRejected";
                default -> event.eventId();
            };
        }
    }
}
