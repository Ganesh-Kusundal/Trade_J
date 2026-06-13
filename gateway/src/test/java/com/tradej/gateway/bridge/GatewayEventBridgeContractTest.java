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
        // P5.1 follow-up: MarketTickEvent now uses the publishGeneric
        // envelope. The bespoke dropped the 'depth' (Optional<MarketDepth>)
        // field entirely; the generic envelope preserves it (null when
        // Optional.empty()). The record field 'segment' is also exposed
        // (the bespoke had it as both a top-level field and embedded
        // via putSymbolFields).
        MarketTickEvent tick = new MarketTickEvent(
                EventMetadata.root(), 1L, "RELIANCE", ExchangeSegment.NSE_EQ,
                FeedMode.FULL, 250000L, 100L, 5000L, 1700000000000L,
                Optional.empty(), 0L, 0L);

        bridge.onDomainEvent(tick);
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(GatewayTopic.MARKET_TICK), bytesCaptor.capture());
        JsonNode envelope = objectMapper.readTree(bytesCaptor.getValue());
        JsonNode payload = envelope.get("payload");
        assertEquals("MarketTickEvent", envelope.get("eventType").asText());
        assertEquals("RELIANCE", payload.get("symbol").asText());
        assertEquals(250000L, payload.get("ltpPaisa").asLong());
        assertEquals(100L, payload.get("lastTradeQuantity").asLong());
        assertEquals(5000L, payload.get("cumulativeVolume").asLong());
        assertEquals(1700000000000L, payload.get("exchangeTimestampEpochMs").asLong());
        // The record field is 'segment', not 'exchangeSegment' (the
        // bespoke used 'segment' as a separate field from the symbol
        // fields helper, which also emitted it). Both shapes are now
        // consolidated: just 'segment' in the generic envelope.
        assertEquals("NSE_EQ", payload.get("segment").asText());
        // depth is null when Optional.empty() — Jackson + Jdk8Module
        // serialize Optional.empty() as null (or sometimes absent).
        assertTrue(payload.get("depth") == null || payload.get("depth").isNull(),
                "depth must be null/absent for empty Optional");
    }

    @Test
    void depthPayload_containsBidsAndAsks() throws Exception {
        // P5.1 follow-up: DepthUpdateEvent now uses the publishGeneric
        // envelope. The consumer reads the payload subobject.
        var bid = new com.tradej.core.domain.model.DepthLevel(250_000L, 100L, 5);
        var ask = new com.tradej.core.domain.model.DepthLevel(251_000L, 200L, 3);
        DepthUpdateEvent depth = new DepthUpdateEvent(
                EventMetadata.root(), "INFY", ExchangeSegment.NSE_EQ,
                List.of(bid), List.of(ask), 5, 1700000000000L);

        bridge.onDomainEvent(depth);
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(GatewayTopic.MARKET_DEPTH), bytesCaptor.capture());
        JsonNode envelope = objectMapper.readTree(bytesCaptor.getValue());
        JsonNode payload = envelope.get("payload");
        assertEquals("DepthUpdateEvent", envelope.get("eventType").asText());
        assertEquals("INFY", payload.get("symbol").asText());
        assertEquals(5, payload.get("levels").asInt());
        assertNotNull(payload.get("bids"), "bids list must be present");
        assertNotNull(payload.get("asks"), "asks list must be present");
        assertEquals(1, payload.get("bids").size());
        assertEquals(1, payload.get("asks").size());
        assertEquals(250_000L, payload.get("bids").get(0).get("pricePaisa").asLong());
        assertEquals(251_000L, payload.get("asks").get(0).get("pricePaisa").asLong());
    }

    @Test
    void candleClosedPayload_containsOHLCV() throws Exception {
        // P5.1 follow-up: CandleClosed now uses the publishGeneric
        // envelope. The Candle record is nested under payload.candle.
        Candle candle = new Candle("TCS", "5m", 1700000000000L, 1700000300000L,
                380000L, 382000L, 379000L, 381000L, 50000L, true);

        bridge.onDomainEvent(new CandleClosed(EventMetadata.root(), candle));
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(GatewayTopic.CANDLE_CLOSED), bytesCaptor.capture());
        JsonNode envelope = objectMapper.readTree(bytesCaptor.getValue());
        JsonNode payload = envelope.get("payload");
        assertEquals("CandleClosed", envelope.get("eventType").asText());
        // The Candle record is nested under payload.candle; the
        // bespoke had it flattened to the top level.
        JsonNode candleNode = payload.get("candle");
        assertNotNull(candleNode, "Candle record must be nested under payload.candle");
        assertEquals("TCS", candleNode.get("symbol").asText());
        assertEquals("5m", candleNode.get("interval").asText());
        assertEquals(380000L, candleNode.get("openPaisa").asLong());
        assertEquals(382000L, candleNode.get("highPaisa").asLong());
        assertEquals(379000L, candleNode.get("lowPaisa").asLong());
        assertEquals(381000L, candleNode.get("closePaisa").asLong());
        assertEquals(50000L, candleNode.get("volume").asLong());
        // 'segment' was added by the bespoke via resolveSegment; the
        // generic envelope does NOT add a segment. The Candle record
        // doesn't have a segment field. Consumers that need the
        // segment should resolve it locally via the same
        // resolveSegment() helper.
    }

    @Test
    void candleDevelopingPayload_containsOHLCV() throws Exception {
        // P5.1 follow-up: CandleDeveloping now uses the publishGeneric
        // envelope (same as CandleClosed).
        Candle candle = new Candle("WIPRO", "1m", 1700000000000L, 1700000060000L,
                45000L, 45500L, 44800L, 45200L, 10000L, false);

        bridge.onDomainEvent(new CandleDeveloping(EventMetadata.root(), candle));
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(GatewayTopic.CANDLE_DEVELOPING), bytesCaptor.capture());
        JsonNode envelope = objectMapper.readTree(bytesCaptor.getValue());
        JsonNode payload = envelope.get("payload");
        assertEquals("CandleDeveloping", envelope.get("eventType").asText());
        JsonNode candleNode = payload.get("candle");
        assertNotNull(candleNode, "Candle record must be nested under payload.candle");
        assertEquals("WIPRO", candleNode.get("symbol").asText());
        assertEquals("1m", candleNode.get("interval").asText());
    }

    @Test
    void orderAcceptedPayload_containsOrderIdAndStatus() throws Exception {
        // P5.1 follow-up: OrderAccepted now uses the publishGeneric envelope.
        OrderAccepted accepted = new OrderAccepted(EventMetadata.root(), ORDER);
        bridge.onDomainEvent(accepted);
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(GatewayTopic.ORDER_UPDATE), bytesCaptor.capture());
        JsonNode envelope = objectMapper.readTree(bytesCaptor.getValue());
        JsonNode payload = envelope.get("payload");
        assertEquals("OrderAccepted", envelope.get("eventType").asText());
        // The bespoke payload overrode the order's status to 'ACCEPTED'
        // (the consumer of an OrderAccepted event knows it's accepted).
        // The generic envelope keeps the original Order.status field
        // (which is 'OPEN' for the test's ORDER fixture). Consumers
        // derive the lifecycle event from the eventType field.
        assertEquals("ORD-1", payload.get("order").get("orderId").asText());
        assertEquals("OPEN", payload.get("order").get("status").asText());
    }

    @Test
    void orderRejectedPayload_containsReason() throws Exception {
        // P5.1 follow-up: OrderRejected now uses the publishGeneric envelope.
        OrderRejected rejected = new OrderRejected(EventMetadata.root(), ORDER, "Insufficient margin");
        bridge.onDomainEvent(rejected);
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(GatewayTopic.ORDER_UPDATE), bytesCaptor.capture());
        JsonNode envelope = objectMapper.readTree(bytesCaptor.getValue());
        JsonNode payload = envelope.get("payload");
        assertEquals("OrderRejected", envelope.get("eventType").asText());
        assertEquals("ORD-1", payload.get("order").get("orderId").asText());
        // The bespoke payload overrode the order's status to 'REJECTED'.
        // The generic envelope keeps the original Order.status field
        // (which is 'OPEN' for the test's ORDER fixture). Consumers
        // derive the lifecycle event from the eventType field.
        assertEquals("OPEN", payload.get("order").get("status").asText());
        // The bespoke payload put ack=true/false + status=ACCEPTED/REJECTED
        // at the top level. The generic envelope does not — the eventType
        // field tells the consumer what kind of event it is. The reason
        // field is still at the top level of the payload.
        assertEquals("Insufficient margin", payload.get("reason").asText());
    }

    @Test
    void orderFilledPayload_containsFillInfo() throws Exception {
        // P5.1 follow-up: OrderFilled now uses the publishGeneric envelope.
        // The consumer reads the payload.fills subobject.
        var trade = new com.tradej.core.domain.model.Trade(
                "FILL-1", "ORD-1", "RELIANCE", ExchangeSegment.NSE_EQ,
                Side.BUY, 100L, 250000L, System.currentTimeMillis());
        OrderFilled filled = new OrderFilled(EventMetadata.root(), ORDER, List.of(trade));
        bridge.onDomainEvent(filled);
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(GatewayTopic.ORDER_UPDATE), bytesCaptor.capture());
        JsonNode envelope = objectMapper.readTree(bytesCaptor.getValue());
        JsonNode payload = envelope.get("payload");
        assertEquals("OrderFilled", envelope.get("eventType").asText());
        assertEquals("ORD-1", payload.get("order").get("orderId").asText());
        // The bespoke payload put tradeId + pricePaisa at the top level
        // (extracted from the fills list). The generic envelope keeps the
        // full fills list intact; consumers iterate payload.fills.
        JsonNode fills = payload.get("fills");
        assertNotNull(fills, "publishGeneric emits the full fills list");
        assertEquals(1, fills.size());
        assertEquals("FILL-1", fills.get(0).get("tradeId").asText());
        assertEquals(250000L, fills.get(0).get("pricePaisa").asLong());
    }

    @Test
    void tradeOpenedPayload_containsPositionInfo() throws Exception {
        // P5.1 follow-up: TradeOpened now uses the publishGeneric envelope.
        // The consumer reads the payload subobject.
        TradeOpened opened = new TradeOpened(EventMetadata.root(), "T-1", "ORD-1", "SIG-1",
                "RELIANCE", Side.BUY, 100L, 250000L, 240000L, 260000L);
        bridge.onDomainEvent(opened);
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(GatewayTopic.POSITION_UPDATE), bytesCaptor.capture());
        JsonNode envelope = objectMapper.readTree(bytesCaptor.getValue());
        JsonNode payload = envelope.get("payload");
        assertEquals("TradeOpened", envelope.get("eventType").asText());
        assertEquals(100L, payload.get("size").asLong());
        assertEquals(250000L, payload.get("entryPricePaisa").asLong());
        assertEquals("RELIANCE", payload.get("symbol").asText());
        // The bespoke payload put an 'action' field with value 'OPEN';
        // the generic envelope does not. The action can be derived from
        // the eventType (TradeOpened → OPEN, TradeClosed → CLOSED).
    }

    @Test
    void tradeClosedPayload_containsClosedAction() throws Exception {
        // P5.1 follow-up: TradeClosed now uses the publishGeneric envelope.
        TradeClosed closed = new TradeClosed(EventMetadata.root(), "T-1", "RELIANCE",
                260000L, 100000L, 100L, "TARGET_HIT");
        bridge.onDomainEvent(closed);
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(GatewayTopic.POSITION_UPDATE), bytesCaptor.capture());
        JsonNode envelope = objectMapper.readTree(bytesCaptor.getValue());
        JsonNode payload = envelope.get("payload");
        assertEquals("TradeClosed", envelope.get("eventType").asText());
        // The bespoke payload put size=0 and entryPricePaisa=0 for
        // closures; the generic envelope uses the actual values from
        // the record (size=100, exitPricePaisa=260000, realizedPnlPaisa=100000).
        assertEquals(100L, payload.get("size").asLong());
        assertEquals(260000L, payload.get("exitPricePaisa").asLong());
        assertEquals(100000L, payload.get("realizedPnlPaisa").asLong());
    }

    @Test
    void signalPayload_containsSignalInfo() throws Exception {
        // P5.1 follow-up: SignalGenerated now uses the publishGeneric
        // envelope. The consumer reads the payload subobject.
        SignalGenerated signal = new SignalGenerated(EventMetadata.root(), "SIG-1",
                "RELIANCE", "5m", Side.BUY, 250000L, 240000L, 260000L,
                "BREAKOUT", Map.of());
        bridge.onDomainEvent(signal);
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(GatewayTopic.STRATEGY_SIGNAL), bytesCaptor.capture());
        JsonNode envelope = objectMapper.readTree(bytesCaptor.getValue());
        JsonNode payload = envelope.get("payload");
        assertEquals("SignalGenerated", envelope.get("eventType").asText());
        assertEquals("SIG-1", payload.get("signalId").asText());
        assertEquals("BUY", payload.get("side").asText());
        assertEquals("BREAKOUT", payload.get("setup").asText());
        // The generic envelope emits MORE fields than the bespoke
        // serializer did (the bespoke dropped interval + the price
        // fields; the generic envelope keeps them all). The consumer
        // can read them from the payload as needed.
        assertEquals("5m", payload.get("interval").asText());
        assertEquals(250000L, payload.get("entryPricePaisa").asLong());
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

    // ── P5.1 follow-up: ReplayTimeChangedEvent → publishGeneric envelope ──

    /**
     * Worked example: ReplayTimeChangedEvent opts into the publishGeneric
     * envelope format. No pre-existing test for this event — this test
     * pins the new wire format. The consumer of the REPLAY_CONTROL topic
     * now sees the generic envelope.
     */
    @Test
    void replayTimeChangedEvent_usesPublishGenericEnvelope() throws Exception {
        com.tradej.core.domain.event.ReplayTimeChangedEvent replay = new com.tradej.core.domain.event.ReplayTimeChangedEvent(
                EventMetadata.root(), 1700000000000L, 1000L);

        bridge.onDomainEvent(replay);

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(GatewayTopic.REPLAY_CONTROL), bytesCaptor.capture());

        JsonNode envelope = objectMapper.readTree(bytesCaptor.getValue());
        assertEquals(GatewayTopic.REPLAY_CONTROL.wireId(), envelope.get("topicId").asInt());
        assertEquals(GatewayTopic.REPLAY_CONTROL.version(), envelope.get("topicVersion").asInt());
        assertEquals("ReplayTimeChangedEvent", envelope.get("eventType").asText());

        JsonNode payload = envelope.get("payload");
        assertNotNull(payload, "publishGeneric envelope must wrap the event in a payload field");
        assertEquals(1700000000000L, payload.get("currentTimeMs").asLong());
        // replaySpeedNanos is also present (bespoke did not emit it).
        assertEquals(1000L, payload.get("replaySpeedNanos").asLong());
        assertNotNull(payload.get("metadata"), "publishGeneric emits metadata in the payload");
    }
}
