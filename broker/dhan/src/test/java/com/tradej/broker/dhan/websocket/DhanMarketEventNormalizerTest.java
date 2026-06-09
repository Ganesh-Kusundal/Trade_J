package com.tradej.broker.dhan.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.adapter.DhanInstrumentResolver;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanPayloadNormalizer;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.broker.dhan.websocket.feed.DhanMarketFeedPacket;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DhanMarketEventNormalizer}.
 *
 * <p>Covers feed packet normalization (Ticker, Quote, Full, Index, Oi, Heartbeat,
 * MarketStatus, PrevClose), order payload handling with dedup, trade payload
 * handling, and dedup filter reset.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DhanMarketEventNormalizerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DhanInstrumentDefinition TCS_DEF = new DhanInstrumentDefinition(
            "TCS", "TCS-EQ", Exchange.NSE, ExchangeSegment.NSE_EQ, "11536",
            "EQUITY", "", null, null, null, 1L, 1L, null
    );
    private static final FeedMode FEED_MODE = FeedMode.QUOTE;
    private static final Order ORDER_PLACED = new Order(
            "ORD-001", "corr-001", "TCS-EQ", ExchangeSegment.NSE_EQ,
            Side.BUY, null, null, OrderStatus.OPEN, 10L, 0L, 0L, 0L, 0L, ""
    );
    private static final Order ORDER_REJECTED = new Order(
            "ORD-002", "corr-002", "TCS-EQ", ExchangeSegment.NSE_EQ,
            Side.BUY, null, null, OrderStatus.REJECTED, 10L, 0L, 0L, 0L, 0L,
            "Insufficient margin"
    );

    @Mock private DhanInstrumentResolver resolver;
    @Mock private DhanPayloadNormalizer normalizer;

    private DhanMarketEventNormalizer normalizerUnderTest;

    @BeforeEach
    void setUp() {
        var metadataFactory = new EventMetadataFactory(new LiveTradingClock());
        normalizerUnderTest = new DhanMarketEventNormalizer(resolver, normalizer, metadataFactory);
    }

    // ─── Feed packet normalization ──────────────────────────────────────

    @Nested
    class NormalizeFeedPacketTests {

        @Test
        void heartbeat_returnsEmpty() {
            var packet = new DhanMarketFeedPacket.Heartbeat();

            var result = normalizerUnderTest.normalizeFeedPacket(packet, FEED_MODE);

            assertTrue(result.isEmpty());
            verifyNoInteractions(resolver, normalizer);
        }

        @Test
        void marketStatus_returnsEmpty() {
            var packet = new DhanMarketFeedPacket.MarketStatus(
                    ExchangeSegment.NSE_EQ, "11536", 1);

            var result = normalizerUnderTest.normalizeFeedPacket(packet, FEED_MODE);

            assertTrue(result.isEmpty());
            verifyNoInteractions(resolver, normalizer);
        }

        @Test
        void prevClose_returnsEmpty() {
            var packet = new DhanMarketFeedPacket.PrevClose(
                    ExchangeSegment.NSE_EQ, "11536", "3500.00", "1000");

            var result = normalizerUnderTest.normalizeFeedPacket(packet, FEED_MODE);

            assertTrue(result.isEmpty());
            verifyNoInteractions(resolver, normalizer);
        }

        @Test
        void ticker_returnsMarketTickEvent() {
            var ticker = new DhanMarketFeedPacket.Ticker(
                    ExchangeSegment.NSE_EQ, "11536", "3500.00", 1717000000L);
            var expectedTick = new MarketTickEvent(
                    null, 1, "TCS-EQ", ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                    350000L, 0L, 0L, 1717000000000L, java.util.Optional.empty(), 0L, 0L);

            when(resolver.requireSecurityId("11536")).thenReturn(TCS_DEF);
            when(normalizer.normalizeFeedPacket(ticker, TCS_DEF, FEED_MODE)).thenReturn(expectedTick);

            var result = normalizerUnderTest.normalizeFeedPacket(ticker, FEED_MODE);

            assertTrue(result.isPresent());
            assertSame(expectedTick, result.get());
            verify(resolver).requireSecurityId("11536");
            verify(normalizer).normalizeFeedPacket(ticker, TCS_DEF, FEED_MODE);
        }

        @Test
        void quote_returnsMarketTickEvent() {
            var quote = new DhanMarketFeedPacket.Quote(
                    ExchangeSegment.NSE_EQ, "11536", "3500.50", 100, 1717000000L,
                    "3500.00", 50000L, 30000L, 20000L, "3490.00", "3480.00", "3510.00", "3490.00");
            var expectedTick = new MarketTickEvent(
                    null, 1, "TCS-EQ", ExchangeSegment.NSE_EQ, FEED_MODE,
                    350050L, 100L, 50000L, 1717000000000L, java.util.Optional.empty(), 0L, 0L);

            when(resolver.requireSecurityId("11536")).thenReturn(TCS_DEF);
            when(normalizer.normalizeFeedPacket(quote, TCS_DEF, FEED_MODE)).thenReturn(expectedTick);

            var result = normalizerUnderTest.normalizeFeedPacket(quote, FEED_MODE);

            assertTrue(result.isPresent());
            assertSame(expectedTick, result.get());
        }

        @Test
        void full_returnsMarketTickEvent() {
            var full = new DhanMarketFeedPacket.Full(
                    ExchangeSegment.NSE_EQ, "11536", "3500.50", 100, 1717000000L,
                    "3500.00", 50000L, 30000L, 20000L, 15000L, 20000L, 10000L,
                    "3490.00", "3480.00", "3510.00", "3490.00",
                    List.of(), List.of());
            var expectedTick = new MarketTickEvent(
                    null, 1, "TCS-EQ", ExchangeSegment.NSE_EQ, FEED_MODE,
                    350050L, 100L, 50000L, 1717000000000L, java.util.Optional.empty(), 15000L, 15000L);

            when(resolver.requireSecurityId("11536")).thenReturn(TCS_DEF);
            when(normalizer.normalizeFeedPacket(full, TCS_DEF, FEED_MODE)).thenReturn(expectedTick);

            var result = normalizerUnderTest.normalizeFeedPacket(full, FEED_MODE);

            assertTrue(result.isPresent());
            assertSame(expectedTick, result.get());
        }

        @Test
        void index_returnsMarketTickEvent() {
            var index = new DhanMarketFeedPacket.Index(
                    ExchangeSegment.NSE_EQ, "NIFTY 50", "22500.00",
                    "22400.00", "22600.00", "22350.00", "22450.00", "0.22");
            var expectedTick = new MarketTickEvent(
                    null, 1, "NIFTY", ExchangeSegment.NSE_EQ, FEED_MODE,
                    2250000L, 0L, 0L, 1717000000000L, java.util.Optional.empty(), 0L, 0L);

            when(resolver.requireSecurityId("NIFTY 50")).thenReturn(TCS_DEF);
            when(normalizer.normalizeFeedPacket(index, TCS_DEF, FEED_MODE)).thenReturn(expectedTick);

            var result = normalizerUnderTest.normalizeFeedPacket(index, FEED_MODE);

            assertTrue(result.isPresent());
            assertSame(expectedTick, result.get());
        }

        @Test
        void oi_returnsMarketTickEvent() {
            var oi = new DhanMarketFeedPacket.Oi(ExchangeSegment.NSE_EQ, "11536", 15000L);
            var expectedTick = new MarketTickEvent(
                    null, 1, "TCS-EQ", ExchangeSegment.NSE_EQ, FEED_MODE,
                    0L, 0L, 0L, System.currentTimeMillis(), java.util.Optional.empty(), 15000L, 15000L);

            when(resolver.requireSecurityId("11536")).thenReturn(TCS_DEF);
            when(normalizer.normalizeFeedPacket(oi, TCS_DEF, FEED_MODE)).thenReturn(expectedTick);

            var result = normalizerUnderTest.normalizeFeedPacket(oi, FEED_MODE);

            assertTrue(result.isPresent());
            assertSame(expectedTick, result.get());
        }

        @Test
        void nullFromNormalizer_returnsEmpty() {
            var ticker = new DhanMarketFeedPacket.Ticker(
                    ExchangeSegment.NSE_EQ, "11536", "3500.00", 1717000000L);

            when(resolver.requireSecurityId("11536")).thenReturn(TCS_DEF);
            when(normalizer.normalizeFeedPacket(ticker, TCS_DEF, FEED_MODE)).thenReturn(null);

            var result = normalizerUnderTest.normalizeFeedPacket(ticker, FEED_MODE);

            assertTrue(result.isEmpty());
        }

        @Test
        void unknownSecurityId_propagatesException() {
            var ticker = new DhanMarketFeedPacket.Ticker(
                    ExchangeSegment.NSE_EQ, "99999", "100.00", 1717000000L);

            when(resolver.requireSecurityId("99999")).thenThrow(
                    new IllegalArgumentException("Unknown security ID: 99999"));

            assertThrows(IllegalArgumentException.class, () ->
                    normalizerUnderTest.normalizeFeedPacket(ticker, FEED_MODE));
        }

        @Test
        void duplicateTick_droppedByDedupFilter() {
            var ticker1 = new DhanMarketFeedPacket.Ticker(
                    ExchangeSegment.NSE_EQ, "11536", "3500.00", 1717000000L);
            var ticker2 = new DhanMarketFeedPacket.Ticker(
                    ExchangeSegment.NSE_EQ, "11536", "3500.00", 1717000000L);
            var tick = new MarketTickEvent(
                    null, 1, "TCS-EQ", ExchangeSegment.NSE_EQ, FEED_MODE,
                    350000L, 0L, 0L, 1717000000000L, java.util.Optional.empty(), 0L, 0L);

            when(resolver.requireSecurityId("11536")).thenReturn(TCS_DEF);
            when(normalizer.normalizeFeedPacket(ticker1, TCS_DEF, FEED_MODE)).thenReturn(tick);
            // Second call returns a tick with the same exchangeTimestampEpochMs
            var duplicateTick = new MarketTickEvent(
                    null, 1, "TCS-EQ", ExchangeSegment.NSE_EQ, FEED_MODE,
                    350000L, 0L, 0L, 1717000000000L, java.util.Optional.empty(), 0L, 0L);
            when(normalizer.normalizeFeedPacket(ticker2, TCS_DEF, FEED_MODE)).thenReturn(duplicateTick);

            var first = normalizerUnderTest.normalizeFeedPacket(ticker1, FEED_MODE);
            var second = normalizerUnderTest.normalizeFeedPacket(ticker2, FEED_MODE);

            assertTrue(first.isPresent());
            assertTrue(second.isEmpty(), "Duplicate tick with same timestamp should be dropped");
        }

        @Test
        void dedupResets_afterResetCall() {
            var ticker = new DhanMarketFeedPacket.Ticker(
                    ExchangeSegment.NSE_EQ, "11536", "3500.00", 1717000000L);
            var tick = new MarketTickEvent(
                    null, 1, "TCS-EQ", ExchangeSegment.NSE_EQ, FEED_MODE,
                    350000L, 0L, 0L, 1717000000000L, java.util.Optional.empty(), 0L, 0L);

            when(resolver.requireSecurityId("11536")).thenReturn(TCS_DEF);
            when(normalizer.normalizeFeedPacket(eq(ticker), eq(TCS_DEF), eq(FEED_MODE)))
                    .thenReturn(tick);

            // First call passes
            assertTrue(normalizerUnderTest.normalizeFeedPacket(ticker, FEED_MODE).isPresent());
            // Second call is duplicate
            assertTrue(normalizerUnderTest.normalizeFeedPacket(ticker, FEED_MODE).isEmpty());

            // After reset
            normalizerUnderTest.resetDedup();
            assertTrue(normalizerUnderTest.normalizeFeedPacket(ticker, FEED_MODE).isPresent(),
                    "After reset, same tick should pass through dedup");
        }
    }

    // ─── Order payload normalization ────────────────────────────────────

    @Nested
    class NormalizeOrderPayloadTests {

        @Test
        void firstOccurrence_returnsOrderAccepted() {
            ObjectNode json = MAPPER.createObjectNode();
            json.put("orderId", "ORD-001");
            json.put("orderStatus", "OPEN");
            json.put("transactionType", "BUY");

            when(resolver.resolveDhanPayload(json)).thenReturn(TCS_DEF);
            when(normalizer.normalizeOrder(any(), eq(TCS_DEF))).thenReturn(ORDER_PLACED);

            var result = normalizerUnderTest.normalizeOrderPayload(json);

            assertTrue(result.isPresent());
            assertInstanceOf(OrderAccepted.class, result.get());
            var accepted = (OrderAccepted) result.get();
            assertEquals("ORD-001", accepted.order().orderId());
        }

        @Test
        void rejectedOrder_returnsOrderRejected() {
            ObjectNode json = MAPPER.createObjectNode();
            json.put("orderId", "ORD-002");
            json.put("orderStatus", "REJECTED");
            json.put("transactionType", "BUY");

            when(resolver.resolveDhanPayload(json)).thenReturn(TCS_DEF);
            when(normalizer.normalizeOrder(any(), eq(TCS_DEF))).thenReturn(ORDER_REJECTED);

            var result = normalizerUnderTest.normalizeOrderPayload(json);

            assertTrue(result.isPresent());
            assertInstanceOf(OrderRejected.class, result.get());
            var rejected = (OrderRejected) result.get();
            assertEquals("ORD-002", rejected.order().orderId());
            assertEquals("Insufficient margin", rejected.reason());
        }

        @Test
        void followUpStatusUpdate_returnsEmpty() {
            ObjectNode json = MAPPER.createObjectNode();
            json.put("orderId", "ORD-001");
            json.put("orderStatus", "OPEN");
            json.put("transactionType", "BUY");

            ObjectNode followUpJson = MAPPER.createObjectNode();
            followUpJson.put("orderId", "ORD-001");
            followUpJson.put("orderStatus", "TRADED");
            followUpJson.put("transactionType", "BUY");

            var orderFollowUp = new Order(
                    "ORD-001", "corr-001", "TCS-EQ", ExchangeSegment.NSE_EQ,
                    Side.BUY, null, null, OrderStatus.TRADED, 10L, 10L, 0L, 0L, 0L, "");

            when(resolver.resolveDhanPayload(any(JsonNode.class))).thenReturn(TCS_DEF);
            when(normalizer.normalizeOrder(any(), eq(TCS_DEF)))
                    .thenReturn(ORDER_PLACED, orderFollowUp);

            // First call: first occurrence → OrderAccepted
            var first = normalizerUnderTest.normalizeOrderPayload(json);
            assertTrue(first.isPresent());
            assertInstanceOf(OrderAccepted.class, first.get());

            // Second call: status transition → suppressed (handled by trade payload)
            var second = normalizerUnderTest.normalizeOrderPayload(followUpJson);
            assertTrue(second.isEmpty(),
                    "Follow-up status (TRADED) should be suppressed; fill events come from trade stream");
        }

        @Test
        void alreadyEmittedRejection_doesNotEmitAgain() {
            ObjectNode json = MAPPER.createObjectNode();
            json.put("orderId", "ORD-002");
            json.put("orderStatus", "REJECTED");
            json.put("transactionType", "BUY");
            json.put("omsErrorDescription", "Insufficient margin");

            when(resolver.resolveDhanPayload(any(JsonNode.class))).thenReturn(TCS_DEF);
            when(normalizer.normalizeOrder(any(), eq(TCS_DEF))).thenReturn(ORDER_REJECTED);

            // First call: rejected → OrderRejected
            var first = normalizerUnderTest.normalizeOrderPayload(json);
            assertTrue(first.isPresent());
            assertInstanceOf(OrderRejected.class, first.get());

            // Second call: same rejection → should be empty (already emitted)
            var second = normalizerUnderTest.normalizeOrderPayload(json);
            assertTrue(second.isEmpty(),
                    "Re-rejection of same order should not emit a second OrderRejected event");
        }

        @Test
        void malformedJson_returnsEmpty() {
            ObjectNode json = MAPPER.createObjectNode();
            json.put("orderId", "ORD-003");
            // Missing required fields that cause exception

            when(resolver.resolveDhanPayload(json)).thenReturn(TCS_DEF);
            when(normalizer.normalizeOrder(any(), eq(TCS_DEF)))
                    .thenThrow(new RuntimeException("Mapping failed"));

            var result = normalizerUnderTest.normalizeOrderPayload(json);

            assertTrue(result.isEmpty(), "Malformed payload should return empty, not propagate exception");
        }
    }

    // ─── Trade payload normalization ────────────────────────────────────

    @Nested
    class NormalizeTradePayloadTests {

        @Test
        void validTrade_returnsOrderFilled() {
            ObjectNode json = MAPPER.createObjectNode();
            json.put("orderId", "ORD-001");
            json.put("exchangeTradeId", "TRD-001");
            json.put("tradedQuantity", 5L);
            json.put("tradedPrice", "3500.00");
            json.put("transactionType", "BUY");
            json.put("orderStatus", "TRADED");

            var trade = new Trade("TRD-001", "ORD-001", "TCS-EQ",
                    ExchangeSegment.NSE_EQ, Side.BUY, 5L, 350000L, 1717000000000L);
            var filledOrder = new Order(
                    "ORD-001", "corr-001", "TCS-EQ", ExchangeSegment.NSE_EQ,
                    Side.BUY, null, null, OrderStatus.TRADED, 10L, 5L, 0L, 0L, 0L, "");

            when(resolver.resolveDhanPayload(json)).thenReturn(TCS_DEF);
            when(normalizer.normalizeTrade(any(), eq(TCS_DEF))).thenReturn(trade);
            when(normalizer.normalizeOrder(any(), eq(TCS_DEF))).thenReturn(filledOrder);

            var result = normalizerUnderTest.normalizeTradePayload(json);

            assertTrue(result.isPresent());
            assertInstanceOf(OrderFilled.class, result.get());
            var filled = result.get();
            assertEquals("TRD-001", filled.fills().get(0).tradeId());
            assertEquals("ORD-001", filled.order().orderId());
            assertEquals(1, filled.fills().size());
        }

        @Test
        void malformedTradePayload_returnsEmpty() {
            ObjectNode json = MAPPER.createObjectNode();
            json.put("orderId", "ORD-003");
            // Missing fields cause exception

            when(resolver.resolveDhanPayload(json)).thenReturn(TCS_DEF);
            when(normalizer.normalizeTrade(any(), eq(TCS_DEF)))
                    .thenThrow(new RuntimeException("Mapping failed"));

            var result = normalizerUnderTest.normalizeTradePayload(json);

            assertTrue(result.isEmpty(),
                    "Malformed trade payload should return empty, not propagate exception");
        }
    }

    // ─── Reset ──────────────────────────────────────────────────────────

    @Nested
    class ResetTests {

        @Test
        void resetDedup_clearsState() {
            var ticker = new DhanMarketFeedPacket.Ticker(
                    ExchangeSegment.NSE_EQ, "11536", "3500.00", 1717000000L);
            var tick = new MarketTickEvent(
                    null, 1, "TCS-EQ", ExchangeSegment.NSE_EQ, FEED_MODE,
                    350000L, 0L, 0L, 1717000000000L, java.util.Optional.empty(), 0L, 0L);

            when(resolver.requireSecurityId("11536")).thenReturn(TCS_DEF);
            when(normalizer.normalizeFeedPacket(eq(ticker), eq(TCS_DEF), eq(FEED_MODE)))
                    .thenReturn(tick);

            // Consume first tick, duplicate gets dropped
            assertTrue(normalizerUnderTest.normalizeFeedPacket(ticker, FEED_MODE).isPresent());
            assertTrue(normalizerUnderTest.normalizeFeedPacket(ticker, FEED_MODE).isEmpty());

            // Reset
            normalizerUnderTest.resetDedup();

            // After reset, same tick passes through
            assertTrue(normalizerUnderTest.normalizeFeedPacket(ticker, FEED_MODE).isPresent());
        }
    }
}
