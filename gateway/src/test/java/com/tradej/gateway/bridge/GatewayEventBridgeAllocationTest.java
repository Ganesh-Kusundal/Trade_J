package com.tradej.gateway.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.PnlUpdatedEvent;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests that verify the GatewayEventBridge produces valid JSON payloads
 * using ObjectNode (zero-allocation pattern) instead of LinkedHashMap.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class GatewayEventBridgeAllocationTest {

    @Mock
    private GatewayTopicRouter router;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GatewayEventBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new GatewayEventBridge(router, objectMapper);
    }

    @AfterEach
    void tearDown() {
        bridge.close();
    }

    // ── marketTickPayload produces valid JSON ───────────────────────────

    @Test
    void marketTickPayloadProducesValidJson() throws Exception {
        MarketTickEvent tick = new MarketTickEvent(
                EventMetadata.root(), 42L, "RELIANCE",
                ExchangeSegment.NSE_EQ, FeedMode.FULL,
                250_000L, 100L, 50_000L,
                System.currentTimeMillis(), Optional.empty(), 0L, 0L);

        bridge.onDomainEvent(tick);

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(GatewayTopic.MARKET_TICK), payloadCaptor.capture());

        byte[] json = payloadCaptor.getValue();
        assertNotNull(json);
        assertTrue(json.length > 0);

        JsonNode node = objectMapper.readTree(json);
        assertTrue(node.isObject(), "Payload should be a JSON object");

        // Verify all expected fields are present
        assertTrue(node.has("symbol"), "Should have symbol field");
        assertTrue(node.has("canonicalSymbol"), "Should have canonicalSymbol field");
        assertTrue(node.has("ltpPaisa"), "Should have ltpPaisa field");
        assertTrue(node.has("lastTradeQuantity"), "Should have lastTradeQuantity field");
        assertTrue(node.has("cumulativeVolume"), "Should have cumulativeVolume field");
        assertTrue(node.has("exchangeTimestampMs"), "Should have exchangeTimestampMs field");
        assertTrue(node.has("segment"), "Should have segment field");
        assertTrue(node.has("feedMode"), "Should have feedMode field");
        assertTrue(node.has("sequence"), "Should have sequence field");

        // Verify field values
        assertEquals("RELIANCE", node.get("symbol").asText());
        assertEquals(250_000L, node.get("ltpPaisa").asLong());
        assertEquals(100L, node.get("lastTradeQuantity").asLong());
        assertEquals(50_000L, node.get("cumulativeVolume").asLong());
        assertEquals("NSE_EQ", node.get("segment").asText());
        assertEquals("FULL", node.get("feedMode").asText());
        assertEquals(42L, node.get("sequence").asLong());
    }

    // ── depthPayload produces valid JSON with all fields ────────────────

    @Test
    void depthPayloadProducesValidJsonWithAllFields() throws Exception {
        List<DepthLevel> bids = List.of(
                new DepthLevel(100_00L, 500L, 10),
                new DepthLevel(99_50L, 300L, 5)
        );
        List<DepthLevel> asks = List.of(
                new DepthLevel(100_50L, 400L, 8),
                new DepthLevel(101_00L, 200L, 3)
        );

        DepthUpdateEvent depth = new DepthUpdateEvent(
                EventMetadata.root(), "INFY",
                ExchangeSegment.NSE_EQ, bids, asks, 5,
                System.currentTimeMillis());

        bridge.onDomainEvent(depth);

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(GatewayTopic.MARKET_DEPTH), payloadCaptor.capture());

        byte[] json = payloadCaptor.getValue();
        assertNotNull(json);

        JsonNode node = objectMapper.readTree(json);
        assertTrue(node.isObject(), "Payload should be a JSON object");

        // Verify top-level fields
        assertTrue(node.has("symbol"), "Should have symbol field");
        assertTrue(node.has("canonicalSymbol"), "Should have canonicalSymbol field");
        assertTrue(node.has("segment"), "Should have segment field");
        assertTrue(node.has("levels"), "Should have levels field");
        assertTrue(node.has("exchangeTimestampMs"), "Should have exchangeTimestampMs field");
        assertTrue(node.has("bids"), "Should have bids field");
        assertTrue(node.has("asks"), "Should have asks field");
        assertTrue(node.has("sequence"), "Should have sequence field");

        assertEquals("INFY", node.get("symbol").asText());
        assertEquals("NSE_EQ", node.get("segment").asText());
        assertEquals(5, node.get("levels").asInt());

        // Verify bids array
        JsonNode bidsNode = node.get("bids");
        assertTrue(bidsNode.isArray(), "bids should be an array");
        assertEquals(2, bidsNode.size(), "bids should have 2 levels");

        JsonNode firstBid = bidsNode.get(0);
        assertEquals(100_00L, firstBid.get("pricePaisa").asLong());
        assertEquals(500L, firstBid.get("quantity").asLong());
        assertEquals(10, firstBid.get("orders").asInt());

        // Verify asks array
        JsonNode asksNode = node.get("asks");
        assertTrue(asksNode.isArray(), "asks should be an array");
        assertEquals(2, asksNode.size(), "asks should have 2 levels");

        JsonNode firstAsk = asksNode.get(0);
        assertEquals(100_50L, firstAsk.get("pricePaisa").asLong());
        assertEquals(400L, firstAsk.get("quantity").asLong());
        assertEquals(8, firstAsk.get("orders").asInt());
    }

    // ── pnlPayload produces valid JSON ──────────────────────────────────

    @Test
    void pnlPayloadProducesValidJson() throws Exception {
        // P5.1 follow-up: PnlUpdatedEvent uses the publishGeneric envelope
        // format (the wire shape is now {topicId, topicVersion, eventType,
        // payload: {...}}). The consumer reads the payload subobject.
        PnlUpdatedEvent pnl = new PnlUpdatedEvent(
                EventMetadata.root(), 1000L, 500L, 2000L);

        bridge.onDomainEvent(pnl);

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(GatewayTopic.PNL_UPDATE), payloadCaptor.capture());

        byte[] json = payloadCaptor.getValue();
        JsonNode envelope = objectMapper.readTree(json);
        JsonNode payload = envelope.get("payload");

        assertEquals("PnlUpdatedEvent", envelope.get("eventType").asText());
        assertEquals(1000L, payload.get("realizedPnlPaisa").asLong());
        assertEquals(500L, payload.get("unrealizedPnlPaisa").asLong());
        assertEquals(2000L, payload.get("netExposurePaisa").asLong());
    }

    // ── Verify no LinkedHashMap in hot path ─────────────────────────────

    @Test
    void sourceCodeDoesNotContainLinkedHashMapInPayloadBuilders() throws IOException {
        Path sourceFile = Path.of("src/main/java/com/tradej/gateway/bridge/GatewayEventBridge.java");
        if (!Files.exists(sourceFile)) {
            // Try from project root
            sourceFile = Path.of("gateway/src/main/java/com/tradej/gateway/bridge/GatewayEventBridge.java");
        }
        assertTrue(Files.exists(sourceFile), "Source file should exist: " + sourceFile.toAbsolutePath());

        String source = Files.readString(sourceFile);

        // Verify no LinkedHashMap usage in payload builder methods
        assertFalse(source.contains("new LinkedHashMap"),
                "Source should not contain 'new LinkedHashMap' — use ObjectNode instead");
        assertFalse(source.contains("new HashMap"),
                "Source should not contain 'new HashMap' — use ObjectNode instead");

        // Verify ObjectNode is used instead
        assertTrue(source.contains("objectMapper.createObjectNode()"),
                "Source should use objectMapper.createObjectNode() for payload building");
    }

    // ── Multiple events produce independent JSON ────────────────────────

    @Test
    void multipleEventsProduceIndependentJson() throws Exception {
        MarketTickEvent tick1 = new MarketTickEvent(
                EventMetadata.root(), 1L, "AAPL",
                ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                150_00L, 10L, 100L,
                1000L, Optional.empty(), 0L, 0L);

        MarketTickEvent tick2 = new MarketTickEvent(
                EventMetadata.root(), 2L, "GOOG",
                ExchangeSegment.NSE_EQ, FeedMode.QUOTE,
                300_00L, 20L, 200L,
                2000L, Optional.empty(), 0L, 0L);

        bridge.onDomainEvent(tick1);
        bridge.onDomainEvent(tick2);

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(router, times(2)).publish(eq(GatewayTopic.MARKET_TICK), payloadCaptor.capture());

        List<byte[]> payloads = payloadCaptor.getAllValues();
        assertEquals(2, payloads.size());

        JsonNode json1 = objectMapper.readTree(payloads.get(0));
        JsonNode json2 = objectMapper.readTree(payloads.get(1));

        assertEquals("AAPL", json1.get("symbol").asText());
        assertEquals(150_00L, json1.get("ltpPaisa").asLong());

        assertEquals("GOOG", json2.get("symbol").asText());
        assertEquals(300_00L, json2.get("ltpPaisa").asLong());

        // Verify they are independent (not the same object)
        assertNotSame(json1, json2);
    }
}
