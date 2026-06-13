package com.tradej.gateway.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.GreeksComputed;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.MaxPainComputed;
import com.tradej.core.domain.event.OptionChainUpdated;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.PnlUpdatedEvent;
import com.tradej.core.domain.event.ScanResultsPublished;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.OptionGreeks;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.scan.ScanHit;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.router.GatewayTopicRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class GatewayEventBridgeContractTest {

    @Mock private GatewayTopicRouter router;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private GatewayEventBridge bridge;

    @BeforeEach
    void setUp() { bridge = new GatewayEventBridge(router, objectMapper); }

    @AfterEach
    void tearDown() { bridge.close(); }

    private static final Instrument NIFTY = new Instrument(
            "NIFTY", "NIFTY", Exchange.NSE, ExchangeSegment.IDX_I,
            "INDEX", null, null, null, null, 50L, 50L);

    private static final Order ORDER = new Order(
            "ORD-1", "corr-1", "RELIANCE", ExchangeSegment.NSE_EQ,
            Side.BUY, ProductType.CNC, OrderType.LIMIT, OrderStatus.OPEN,
            100L, 0L, 250000L, 0L, System.currentTimeMillis(), null);

    private JsonNode publishAndCapture(com.tradej.core.domain.event.DomainEvent event, GatewayTopic expectedTopic) throws Exception {
        bridge.onDomainEvent(event);
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(expectedTopic), bytesCaptor.capture());
        return objectMapper.readTree(bytesCaptor.getValue());
    }

    @Test
    void marketTickPayload_containsAllFields() throws Exception {
        MarketTickEvent tick = new MarketTickEvent(
                EventMetadata.root(), 1L, "RELIANCE", ExchangeSegment.NSE_EQ,
                FeedMode.FULL, 250000L, 100L, 5000L, 1700000000000L,
                Optional.empty(), 0L, 0L);

        JsonNode json = publishAndCapture(tick, GatewayTopic.MARKET_TICK);
        assertNotNull(json.get("symbol"));
        assertEquals(250000L, json.get("ltpPaisa").asLong());
        assertEquals(100L, json.get("lastTradeQuantity").asLong());
        assertEquals(5000L, json.get("cumulativeVolume").asLong());
        assertEquals(1700000000000L, json.get("exchangeTimestampMs").asLong());
        assertEquals("NSE_EQ", json.get("segment").asText());
    }

    @Test
    void depthPayload_containsBidsAndAsks() throws Exception {
        DepthUpdateEvent depth = new DepthUpdateEvent(
                EventMetadata.root(), "INFY", ExchangeSegment.NSE_EQ,
                List.of(), List.of(), 5, 1700000000000L);

        JsonNode json = publishAndCapture(depth, GatewayTopic.MARKET_DEPTH);
        assertNotNull(json.get("symbol"));
        assertNotNull(json.get("bids"));
        assertNotNull(json.get("asks"));
        assertEquals(5, json.get("levels").asInt());
    }

    @Test
    void candleClosedPayload_containsOHLCV() throws Exception {
        Candle candle = new Candle("TCS", "5m", 1700000000000L, 1700000300000L,
                380000L, 382000L, 379000L, 381000L, 50000L, true);

        JsonNode json = publishAndCapture(new CandleClosed(EventMetadata.root(), candle), GatewayTopic.CANDLE_CLOSED);
        assertNotNull(json.get("symbol"));
        assertEquals("5m", json.get("interval").asText());
        assertEquals(380000L, json.get("openPaisa").asLong());
        assertEquals(382000L, json.get("highPaisa").asLong());
        assertEquals(379000L, json.get("lowPaisa").asLong());
        assertEquals(381000L, json.get("closePaisa").asLong());
        assertEquals(50000L, json.get("volume").asLong());
        assertNotNull(json.get("segment"));
    }

    @Test
    void candleDevelopingPayload_containsOHLCV() throws Exception {
        Candle candle = new Candle("WIPRO", "1m", 1700000000000L, 1700000060000L,
                45000L, 45500L, 44800L, 45200L, 10000L, false);

        JsonNode json = publishAndCapture(new CandleDeveloping(EventMetadata.root(), candle), GatewayTopic.CANDLE_DEVELOPING);
        assertNotNull(json.get("symbol"));
        assertEquals("1m", json.get("interval").asText());
    }

    @Test
    void orderAcceptedPayload_containsOrderIdAndStatus() throws Exception {
        JsonNode json = publishAndCapture(new OrderAccepted(EventMetadata.root(), ORDER), GatewayTopic.ORDER_UPDATE);
        assertEquals("ORD-1", json.get("orderId").asText());
        assertEquals("ACCEPTED", json.get("status").asText());
        assertTrue(json.get("ack").asBoolean());
    }

    @Test
    void orderRejectedPayload_containsReason() throws Exception {
        JsonNode json = publishAndCapture(new OrderRejected(EventMetadata.root(), ORDER, "Insufficient margin"), GatewayTopic.ORDER_UPDATE);
        assertEquals("ORD-1", json.get("orderId").asText());
        assertEquals("REJECTED", json.get("status").asText());
        assertFalse(json.get("ack").asBoolean());
        assertEquals("Insufficient margin", json.get("reason").asText());
    }

    @Test
    void orderFilledPayload_containsFillInfo() throws Exception {
        var trade = new com.tradej.core.domain.model.Trade(
                "FILL-1", "ORD-1", "RELIANCE", ExchangeSegment.NSE_EQ,
                Side.BUY, 100L, 250000L, System.currentTimeMillis());
        JsonNode json = publishAndCapture(new OrderFilled(EventMetadata.root(), ORDER, List.of(trade)), GatewayTopic.ORDER_UPDATE);
        assertEquals("ORD-1", json.get("orderId").asText());
        assertEquals(250000L, json.get("pricePaisa").asLong());
        assertEquals("FILL-1", json.get("tradeId").asText());
    }

    @Test
    void tradeOpenedPayload_containsPositionInfo() throws Exception {
        TradeOpened opened = new TradeOpened(EventMetadata.root(), "T-1", "ORD-1", "SIG-1",
                "RELIANCE", Side.BUY, 100L, 250000L, 240000L, 260000L);
        JsonNode json = publishAndCapture(opened, GatewayTopic.POSITION_UPDATE);
        assertEquals(100L, json.get("size").asLong());
        assertEquals(250000L, json.get("entryPricePaisa").asLong());
        assertEquals("OPEN", json.get("action").asText());
    }

    @Test
    void tradeClosedPayload_containsClosedAction() throws Exception {
        TradeClosed closed = new TradeClosed(EventMetadata.root(), "T-1", "RELIANCE",
                260000L, 100000L, 100L, "TARGET_HIT");
        JsonNode json = publishAndCapture(closed, GatewayTopic.POSITION_UPDATE);
        assertEquals("CLOSED", json.get("action").asText());
    }

    @Test
    void signalPayload_containsSignalInfo() throws Exception {
        SignalGenerated signal = new SignalGenerated(EventMetadata.root(), "SIG-1",
                "RELIANCE", "5m", Side.BUY, 250000L, 240000L, 260000L,
                "BREAKOUT", Map.of());
        JsonNode json = publishAndCapture(signal, GatewayTopic.STRATEGY_SIGNAL);
        assertEquals("SIG-1", json.get("signalId").asText());
        assertEquals("BUY", json.get("side").asText());
        assertEquals("BREAKOUT", json.get("setup").asText());
    }

    @Test
    void pnlPayload_containsRealizedAndUnrealized() throws Exception {
        // P5.1 follow-up: PnlUpdatedEvent now uses the publishGeneric
        // envelope (pnlUpdatedEvent_usesPublishGenericEnvelope in this
        // test class is the canonical test for that path). This test
        // remains to verify the wire-level JSON the consumer sees for
        // the PNL_UPDATE topic — the consumer reads the payload subobject.
        PnlUpdatedEvent pnl = new PnlUpdatedEvent(EventMetadata.root(), 50000L, 25000L, 1000000L);
        bridge.onDomainEvent(pnl);
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(GatewayTopic.PNL_UPDATE), bytesCaptor.capture());
        JsonNode envelope = objectMapper.readTree(bytesCaptor.getValue());
        JsonNode payload = envelope.get("payload");
        assertEquals("PnlUpdatedEvent", envelope.get("eventType").asText());
        assertEquals(50000L, payload.get("realizedPnlPaisa").asLong());
        assertEquals(25000L, payload.get("unrealizedPnlPaisa").asLong());
        assertEquals(1000000L, payload.get("netExposurePaisa").asLong());
    }

    @Test
    void scanResultsPayload_containsHits() throws Exception {
        ScanResultsPublished.ScanHitSummary hit = new ScanResultsPublished.ScanHitSummary(
                "RELIANCE", "NSE_EQ", "RELIANCE", 85.0, List.of("Volume spike", "RSI > 60"));
        ScanResultsPublished scan = new ScanResultsPublished(
                EventMetadata.root(), "profile-1", "run-1", 1,
                System.currentTimeMillis() - 1000, System.currentTimeMillis(), List.of(hit));
        JsonNode json = publishAndCapture(scan, GatewayTopic.SCAN_COMPLETED);
        assertEquals("profile-1", json.get("profileId").asText());
        assertEquals(1, json.get("hitCount").asInt());
        assertTrue(json.get("hits").isArray());
    }

    @Test
    void greeksPayload_containsDeltaGamma() throws Exception {
        OptionGreeks greeks = new OptionGreeks(0.55, -0.02, 0.01, 25.0, 0.18);
        GreeksComputed event = new GreeksComputed(EventMetadata.root(),
                new com.tradej.core.domain.model.InstrumentKey("NIFTY25JUN24000CE", ExchangeSegment.IDX_I), greeks);
        JsonNode json = publishAndCapture(event, GatewayTopic.GREEKS_UPDATE);
        assertEquals(0.55, json.get("delta").asDouble(), 0.001);
        assertEquals(0.01, json.get("gamma").asDouble(), 0.001);
    }

    @Test
    void maxPainPayload_containsStrike() throws Exception {
        MaxPainComputed event = new MaxPainComputed(EventMetadata.root(), "NIFTY",
                LocalDate.of(2026, 6, 25), 2400000L, 50000000L);
        JsonNode json = publishAndCapture(event, GatewayTopic.MAX_PAIN_UPDATE);
        assertEquals("NIFTY", json.get("underlying").asText());
        assertEquals(2400000L, json.get("maxPainStrikePaisa").asLong());
    }

    @Test
    void unknownEventType_silentlyIgnored() {
        com.tradej.core.domain.event.DomainEvent unknown = new com.tradej.core.domain.event.TestEvent();
        assertDoesNotThrow(() -> bridge.onDomainEvent(unknown));
    }

    // ── P5.1 follow-up: publishGeneric envelope ─────────────────────

    /**
     * Verifies the publishGeneric envelope: topicId + topicVersion + eventType
     * metadata tags + event payload. The full P5.1 follow-up migrates the
     * 17+ bespoke serializers to publishGeneric; this test pins the
     * envelope shape so the migration is safe.
     */
    @Test
    void publishGeneric_envelopeContainsTopicMetadataAndPayload() throws Exception {
        MarketTickEvent tick = new MarketTickEvent(
                EventMetadata.root(), 1L, "RELIANCE", ExchangeSegment.NSE_EQ,
                FeedMode.FULL, 250000L, 100L, 5000L, 1700000000000L,
                Optional.empty(), 0L, 0L);

        bridge.publishGeneric(tick);

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(GatewayTopic.MARKET_TICK), bytesCaptor.capture());

        JsonNode envelope = objectMapper.readTree(bytesCaptor.getValue());
        assertEquals(GatewayTopic.MARKET_TICK.wireId(), envelope.get("topicId").asInt());
        assertEquals(GatewayTopic.MARKET_TICK.version(), envelope.get("topicVersion").asInt());
        assertEquals("MarketTickEvent", envelope.get("eventType").asText());

        JsonNode payload = envelope.get("payload");
        assertNotNull(payload, "publishGeneric must wrap the event in a payload field");
        assertEquals("RELIANCE", payload.get("symbol").asText());
        assertEquals(250000L, payload.get("ltpPaisa").asLong());
    }

    /**
     * Verifies that publishGeneric for an event whose class is NOT in
     * BridgeTopics.MAP is silently dropped (with a WARN log). This is
     * the contract: the map is the single source of truth; events not
     * in the map don't get auto-published.
     */
    @Test
    void publishGeneric_unknownEventType_doesNotPublish() {
        com.tradej.core.domain.event.DomainEvent unknown = new com.tradej.core.domain.event.TestEvent();
        assertDoesNotThrow(() -> bridge.publishGeneric(unknown));
        // No publish should have happened (BridgeTopics.MAP has no entry for TestEvent).
        verify(router, never()).publish(any(GatewayTopic.class), any(byte[].class));
    }

    // ── P5.1 follow-up: PnlUpdatedEvent → publishGeneric envelope ──

    /**
     * Worked example: PnlUpdatedEvent opts into the publishGeneric envelope
     * format. The consumer of the PNL_UPDATED topic now sees:
     *   { topicId, topicVersion, eventType, payload: { metadata,
     *     realizedPnlPaisa, unrealizedPnlPaisa, netExposurePaisa } }
     * Previously the consumer saw a flat { realizedPnlPaisa, ... }
     * schema. The metadata field is the breaking change for downstream
     * consumers; the envelope shape is now consistent with publishGeneric.
     */
    @Test
    void pnlUpdatedEvent_usesPublishGenericEnvelope() throws Exception {
        PnlUpdatedEvent pnl = new PnlUpdatedEvent(
                EventMetadata.root(), 100_000L, -50_000L, 1_000_000L);

        bridge.onDomainEvent(pnl);

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(GatewayTopic.PNL_UPDATE), bytesCaptor.capture());

        JsonNode envelope = objectMapper.readTree(bytesCaptor.getValue());
        assertEquals(GatewayTopic.PNL_UPDATE.wireId(), envelope.get("topicId").asInt());
        assertEquals(GatewayTopic.PNL_UPDATE.version(), envelope.get("topicVersion").asInt());
        assertEquals("PnlUpdatedEvent", envelope.get("eventType").asText());

        JsonNode payload = envelope.get("payload");
        assertNotNull(payload, "publishGeneric envelope must wrap the event in a payload field");
        assertEquals(100_000L, payload.get("realizedPnlPaisa").asLong());
        assertEquals(-50_000L, payload.get("unrealizedPnlPaisa").asLong());
        assertEquals(1_000_000L, payload.get("netExposurePaisa").asLong());
        // The metadata field is now present in the payload (was absent
        // in the bespoke serializer output). This is the breaking
        // change for downstream consumers.
        assertNotNull(payload.get("metadata"), "publishGeneric emits metadata in the payload");
    }
}
