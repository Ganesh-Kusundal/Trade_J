package com.tradej.gateway.bridge;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.GammaExposureComputed;
import com.tradej.core.domain.event.GreeksComputed;
import com.tradej.core.domain.event.MaxPainComputed;
import com.tradej.core.domain.event.OptionChainUpdated;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.PnlUpdatedEvent;
import com.tradej.core.domain.event.ReplayTimeChangedEvent;
import com.tradej.core.domain.event.ScanResultsPublished;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.StrategyMetricsSnapshot;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Bridges domain events from the event bus to the gateway WebSocket topic router.
 *
 * <p>Each domain event type is mapped to a {@link GatewayTopic} and serialized to JSON.
 *
 * <p>Dedup is handled at the bus level ({@code DisruptorEventBus}). This bridge is a
 * pure pass-through serializer — no duplicate filtering here.
 */
public final class GatewayEventBridge implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GatewayEventBridge.class);

    private final GatewayTopicRouter router;
    private final ObjectMapper objectMapper;
    private final InstrumentResolver instrumentResolver;

    private final AtomicLong eventCount = new AtomicLong();
    private final Map<Class<? extends DomainEvent>, SerializerEntry> serializers;

    public GatewayEventBridge(GatewayTopicRouter router, ObjectMapper objectMapper) {
        this(router, objectMapper, null);
    }

    public GatewayEventBridge(GatewayTopicRouter router, ObjectMapper objectMapper, InstrumentResolver instrumentResolver) {
        this.router = router;
        this.objectMapper = objectMapper;
        this.instrumentResolver = instrumentResolver;
        this.serializers = buildSerializerMap();
    }

    /** Serializer entry linking a domain event type to its topic, payload builder, and envelope mode. */
    private record SerializerEntry(
            GatewayTopic topic,
            Function<DomainEvent, ObjectNode> serializer,
            boolean useGenericEnvelope
    ) {
        /** Bespoke envelope (legacy): the per-event serializer produces a flat JSON payload. */
        static SerializerEntry bespoke(GatewayTopic topic, Function<DomainEvent, ObjectNode> serializer) {
            return new SerializerEntry(topic, serializer, false);
        }
        /** Generic envelope: the per-event serializer is ignored; the publishGeneric path is used. */
        static SerializerEntry generic(GatewayTopic topic) {
            return new SerializerEntry(topic, null, true);
        }
    }

    private Map<Class<? extends DomainEvent>, SerializerEntry> buildSerializerMap() {
        // Topic mapping is owned by BridgeTopics (single source of truth).
        // This map is keyed by the same class, but each entry is built
        // here because the per-event serializers are bespoke (they know
        // the JSON shape). Asserting the two stay in sync is the job of
        // BridgeTopicsTest.
        //
        // P5.1 follow-up: events that opt into the generic envelope format
        // use SerializerEntry.generic(topic). Their consumer must read the
        // {topicId, topicVersion, eventType, payload} envelope. The first
        // event migrated is PnlUpdatedEvent (a simple record with no
        // nested Optional / custom shaping). New events should use the
        // generic envelope by default; bespoke is for events that need
        // custom JSON shape (depth, candle, scan results, etc.).
        Map<Class<? extends DomainEvent>, SerializerEntry> m = new java.util.LinkedHashMap<>();
        m.put(MarketTickEvent.class,        entry(BridgeTopics.MAP.get(MarketTickEvent.class),        e -> marketTickPayload((MarketTickEvent) e)));
        m.put(DepthUpdateEvent.class,       entry(BridgeTopics.MAP.get(DepthUpdateEvent.class),       e -> depthPayload((DepthUpdateEvent) e)));
        m.put(CandleDeveloping.class,       entry(BridgeTopics.MAP.get(CandleDeveloping.class),       e -> candlePayload(((CandleDeveloping) e).candle())));
        m.put(CandleClosed.class,           entry(BridgeTopics.MAP.get(CandleClosed.class),           e -> candlePayload(((CandleClosed) e).candle())));
        m.put(OrderAccepted.class,          entry(BridgeTopics.MAP.get(OrderAccepted.class),          e -> orderAckPayload((OrderAccepted) e)));
        m.put(OrderRejected.class,          entry(BridgeTopics.MAP.get(OrderRejected.class),          e -> orderRejectPayload((OrderRejected) e)));
        m.put(OrderFilled.class,            entry(BridgeTopics.MAP.get(OrderFilled.class),            e -> orderPayload(e)));
        m.put(TradeOpened.class,            SerializerEntry.generic(BridgeTopics.MAP.get(TradeOpened.class)));
        m.put(TradeClosed.class,            SerializerEntry.generic(BridgeTopics.MAP.get(TradeClosed.class)));
        m.put(SignalGenerated.class,        SerializerEntry.generic(BridgeTopics.MAP.get(SignalGenerated.class)));
        m.put(ReplayTimeChangedEvent.class, SerializerEntry.generic(BridgeTopics.MAP.get(ReplayTimeChangedEvent.class)));
        // P5.1 follow-up worked example: PnlUpdatedEvent opts into the
        // generic envelope. The bespoke serializer (pnlPayload) is no
        // longer used; consumers read {topicId, topicVersion, eventType,
        // payload: {metadata, realizedPnlPaisa, ...}} instead. Note:
        // publishGeneric emits the metadata field in the payload
        // (bespoke did not), so the consumer schema gains a `metadata`
        // field. See PnlUpdatedEventEnvelopeMigrationTest.
        m.put(PnlUpdatedEvent.class,        SerializerEntry.generic(BridgeTopics.MAP.get(PnlUpdatedEvent.class)));
        m.put(ScanResultsPublished.class,   entry(BridgeTopics.MAP.get(ScanResultsPublished.class),   e -> scanPayload((ScanResultsPublished) e)));
        m.put(OptionChainUpdated.class,     entry(BridgeTopics.MAP.get(OptionChainUpdated.class),     e -> optionChainPayload((OptionChainUpdated) e)));
        m.put(GreeksComputed.class,         entry(BridgeTopics.MAP.get(GreeksComputed.class),         e -> greeksPayload((GreeksComputed) e)));
        m.put(MaxPainComputed.class,        entry(BridgeTopics.MAP.get(MaxPainComputed.class),        e -> maxPainPayload((MaxPainComputed) e)));
        m.put(GammaExposureComputed.class,  entry(BridgeTopics.MAP.get(GammaExposureComputed.class),  e -> gammaPayload((GammaExposureComputed) e)));
        m.put(StrategyMetricsSnapshot.class, entry(BridgeTopics.MAP.get(StrategyMetricsSnapshot.class), e -> strategyMetricsPayload((StrategyMetricsSnapshot) e)));
        return Map.copyOf(m);
    }

    private static SerializerEntry entry(GatewayTopic topic, Function<DomainEvent, ObjectNode> serializer) {
        return SerializerEntry.bespoke(topic, serializer);
    }

    /**
     * Register this bridge as a subscriber to specific domain events on the event bus.
     * Narrows subscriptions from {@code DomainEvent.class} to only the types the bridge
     * actually handles, reducing unnecessary dispatch overhead (ST-02).
     */
    public void register(EventBus eventBus) {
        eventBus.subscribe(MarketTickEvent.class, this::onDomainEvent);
        eventBus.subscribe(DepthUpdateEvent.class, this::onDomainEvent);
        eventBus.subscribe(CandleDeveloping.class, this::onDomainEvent);
        eventBus.subscribe(CandleClosed.class, this::onDomainEvent);
        eventBus.subscribe(OrderAccepted.class, this::onDomainEvent);
        eventBus.subscribe(OrderRejected.class, this::onDomainEvent);
        eventBus.subscribe(OrderFilled.class, this::onDomainEvent);
        eventBus.subscribe(TradeOpened.class, this::onDomainEvent);
        eventBus.subscribe(TradeClosed.class, this::onDomainEvent);
        eventBus.subscribe(SignalGenerated.class, this::onDomainEvent);
        eventBus.subscribe(ReplayTimeChangedEvent.class, this::onDomainEvent);
        eventBus.subscribe(PnlUpdatedEvent.class, this::onDomainEvent);
        eventBus.subscribe(ScanResultsPublished.class, this::onDomainEvent);
        eventBus.subscribe(OptionChainUpdated.class, this::onDomainEvent);
        eventBus.subscribe(GreeksComputed.class, this::onDomainEvent);
        eventBus.subscribe(MaxPainComputed.class, this::onDomainEvent);
        eventBus.subscribe(GammaExposureComputed.class, this::onDomainEvent);
    }

    void onDomainEvent(DomainEvent event) {
        if (event == null) {
            log.warn("Null domain event received — skipping");
            return;
        }

        try {
            SerializerEntry entry = serializers.get(event.getClass());
            if (entry != null) {
                if (entry.useGenericEnvelope()) {
                    // P5.1 follow-up: events with useGenericEnvelope=true
                    // bypass the bespoke serializer. The publishGeneric
                    // path emits the {topicId, topicVersion, eventType,
                    // payload} envelope. Note: publishGeneric does NOT
                    // inject the correlationId field (it lives in the
                    // metadata sub-object inside the payload instead).
                    publishGeneric(event);
                } else {
                    ObjectNode payload = entry.serializer().apply(event);
                    // Inject correlation ID for end-to-end tracing
                    String correlationId = event.correlationId();
                    if (correlationId != null && !correlationId.isEmpty()) {
                        payload.put("correlationId", correlationId);
                    }
                    router.publish(entry.topic(), writeJson(payload));
                }
            }
            eventCount.incrementAndGet();
        } catch (Exception e) {
            log.warn("Gateway bridge failed for {}: {}", event.getClass().getSimpleName(), e.getMessage());
        }
    }

    @Override
    public void close() {
        // No resources to release — dedup is handled at the bus level
    }

    // ── Metrics ──

    /** Number of events successfully bridged to the router. */
    public long eventCount() {
        return eventCount.get();
    }

    // ── Private helpers ──

    private byte[] writeJson(ObjectNode node) throws com.fasterxml.jackson.core.JsonProcessingException {
        return objectMapper.writeValueAsBytes(node);
    }

    private byte[] writeJsonMap(Map<String, Object> payload) throws com.fasterxml.jackson.core.JsonProcessingException {
        return objectMapper.writeValueAsBytes(payload);
    }

    // ── Payload builders ──

    private ObjectNode marketTickPayload(MarketTickEvent tick) {
        ObjectNode node = objectMapper.createObjectNode();
        putSymbolFields(node, tick.symbol(), tick.segment());
        node.put("ltpPaisa", tick.ltpPaisa());
        node.put("lastTradeQuantity", tick.lastTradeQuantity());
        node.put("cumulativeVolume", tick.cumulativeVolume());
        node.put("exchangeTimestampMs", tick.exchangeTimestampEpochMs());
        node.put("segment", tick.segment().name());
        node.put("feedMode", tick.feedMode().name());
        node.put("sequence", tick.sequenceId());
        return node;
    }


    private ObjectNode depthPayload(DepthUpdateEvent depth) {
        ObjectNode node = objectMapper.createObjectNode();
        putSymbolFields(node, depth.symbol(), depth.segment());
        node.put("segment", depth.segment().name());
        node.put("levels", depth.levels());
        node.put("exchangeTimestampMs", depth.exchangeTimestampMs());
        ArrayNode bidsArray = node.putArray("bids");
        for (DepthLevel level : depth.bids()) {
            bidsArray.add(depthLevelToNode(level));
        }
        ArrayNode asksArray = node.putArray("asks");
        for (DepthLevel level : depth.asks()) {
            asksArray.add(depthLevelToNode(level));
        }
        node.put("sequence", depth.sequenceId());
        return node;
    }

    private ObjectNode depthLevelToNode(DepthLevel level) {
        ObjectNode m = objectMapper.createObjectNode();
        m.put("pricePaisa", level.pricePaisa());
        m.put("quantity", level.quantity());
        m.put("orders", level.orderCount());
        return m;
    }

    private ObjectNode candlePayload(Candle candle) {
        ObjectNode node = objectMapper.createObjectNode();
        ExchangeSegment segment = resolveSegment(candle.symbol());
        String canonical = canonicalSymbol(candle.symbol(), segment);
        node.put("symbol", canonical);
        node.put("canonicalSymbol", canonical);
        node.put("segment", segment.name());
        node.put("interval", candle.interval());
        node.put("startTimeMs", candle.startTimeMs());
        node.put("endTimeMs", candle.endTimeMs());
        node.put("openPaisa", candle.openPaisa());
        node.put("highPaisa", candle.highPaisa());
        node.put("lowPaisa", candle.lowPaisa());
        node.put("closePaisa", candle.closePaisa());
        node.put("volume", candle.volume());
        return node;
    }

    private ObjectNode orderAckPayload(OrderAccepted accepted) {
        ObjectNode node = orderPayload(accepted);
        node.put("ack", true);
        node.put("status", "ACCEPTED");
        return node;
    }

    private ObjectNode orderRejectPayload(OrderRejected rejected) {
        ObjectNode node = orderPayload(rejected);
        node.put("ack", false);
        node.put("status", "REJECTED");
        return node;
    }

    private ObjectNode orderPayload(DomainEvent event) {
        ObjectNode node = objectMapper.createObjectNode();
        String type = event.getClass().getSimpleName();
        node.put("type", type);
        if (event instanceof OrderAccepted a) {
            node.put("orderId", a.order().orderId());
            putSymbolFields(node, a.order().symbol(), a.order().exchangeSegment());
            node.put("status", a.order().status().name());
            node.put("quantity", a.order().quantity());
            node.put("filledQuantity", a.order().filledQuantity());
            node.put("pricePaisa", a.order().pricePaisa());
            node.put("side", a.order().side().name());
        } else if (event instanceof OrderRejected r) {
            node.put("orderId", r.order().orderId());
            putSymbolFields(node, r.order().symbol(), r.order().exchangeSegment());
            node.put("status", r.order().status().name());
            node.put("reason", r.reason());
        } else if (event instanceof OrderFilled f) {
            node.put("orderId", f.order().orderId());
            putSymbolFields(node, f.order().symbol(), f.order().exchangeSegment());
            node.put("status", f.order().status().name());
            node.put("filledQuantity", f.order().filledQuantity());
            node.put("fillCount", f.fills().size());
            if (!f.fills().isEmpty()) {
                var firstFill = f.fills().getFirst();
                node.put("pricePaisa", firstFill.pricePaisa());
                node.put("tradeId", firstFill.tradeId());
            }
        }
        return node;
    }

    private ObjectNode positionPayload(String symbol, ExchangeSegment segment, long size, long entryPricePaisa, String action) {
        ObjectNode node = objectMapper.createObjectNode();
        putSymbolFields(node, symbol, segment);
        node.put("size", size);
        node.put("entryPricePaisa", entryPricePaisa);
        node.put("action", action);
        return node;
    }

    private ObjectNode signalPayload(SignalGenerated signal) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("signalId", signal.signalId());
        putSymbolFields(node, signal.symbol(), resolveSegment(signal.symbol()));
        node.put("side", signal.side().name());
        node.put("setup", signal.setup());
        return node;
    }

    private ObjectNode replayPayload(ReplayTimeChangedEvent replay) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "REPLAY_TIME_CHANGED");
        node.put("currentTimeMs", replay.currentTimeMs());
        node.put("replaySpeedNanos", replay.replaySpeedNanos());
        return node;
    }

    private ObjectNode scanPayload(ScanResultsPublished scan) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "SCAN_COMPLETED");
        node.put("profileId", scan.profileId());
        node.put("hitCount", scan.hits().size());
        ArrayNode hitsArray = node.putArray("hits");
        for (var h : scan.hits()) {
            ObjectNode hitNode = objectMapper.createObjectNode();
            hitNode.put("symbol", h.symbol());
            hitNode.put("score", h.score());
            ArrayNode reasonsArray = hitNode.putArray("reasons");
            for (String reason : h.reasons()) {
                reasonsArray.add(reason);
            }
            hitsArray.add(hitNode);
        }
        return node;
    }

    private ObjectNode optionChainPayload(OptionChainUpdated event) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "OPTION_CHAIN_UPDATED");
        node.put("underlying", event.chain().underlying().symbol());
        node.put("expiry", event.chain().expiry().toString());
        node.put("spotPricePaisa", event.chain().spotPricePaisa());
        node.put("entryCount", event.chain().strikes().size());
        return node;
    }

    private ObjectNode greeksPayload(GreeksComputed event) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "GREEKS_COMPUTED");
        node.put("symbol", event.instrumentKey().symbol());
        if (event.greeks().delta() != null) node.put("delta", event.greeks().delta());
        if (event.greeks().gamma() != null) node.put("gamma", event.greeks().gamma());
        if (event.greeks().theta() != null) node.put("theta", event.greeks().theta());
        if (event.greeks().vega() != null) node.put("vega", event.greeks().vega());
        if (event.greeks().impliedVolatility() != null) node.put("iv", event.greeks().impliedVolatility());
        return node;
    }

    private ObjectNode maxPainPayload(MaxPainComputed event) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "MAX_PAIN_COMPUTED");
        node.put("underlying", event.underlying());
        node.put("maxPainStrikePaisa", event.maxPainStrikePaisa());
        node.put("totalPainPaisa", event.totalPainPaisa());
        return node;
    }

    private ObjectNode gammaPayload(GammaExposureComputed event) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "GAMMA_EXPOSURE_COMPUTED");
        node.put("underlying", event.underlying());
        node.put("netGamma", event.netGamma());
        return node;
    }

    /**
     * Publishes a pipeline health snapshot to subscribed gateway clients.
     */
    public void publishPipelineHealth(Map<String, Object> health) {
        try {
            router.publish(GatewayTopic.PIPELINE_HEALTH, writeJsonMap(health));
        } catch (Exception e) {
            log.warn("Failed to publish pipeline health: {}", e.getMessage());
        }
    }

    // ── P5.1: Generic event publisher ────────────────────────────────
    //
    // The bespoke per-event serializers above (marketTickPayload,
    // depthPayload, etc.) are kept for cases that need a custom JSON shape.
    // For new domain metrics that don't need a custom shape, the generic
    // publisher below uses Jackson reflection to serialize the event and
    // embeds the topic metadata (wireId + version) as JSON tags. Adding a
    // new event type to the gateway no longer requires a per-event
    // serializer method — just call publishGeneric(event) and the topic
    // is resolved from the event class via the BridgeTopics.MAP table.

    /**
     * Generic publisher: serialize any {@link DomainEvent} to JSON via
     * Jackson reflection and publish to the resolved {@link GatewayTopic}.
     * Topic is determined by the event class via {@code BridgeTopics.MAP}
     * (the single source of truth for topic mapping). If the event class
     * has no entry in the map, a WARN is logged and the event is dropped.
     *
     * <p>The JSON envelope includes a {@code topicId} and {@code topicVersion}
     * metadata tag pair so consumers can route/decode without knowing the
     * exact class name on the wire.
     */
    public void publishGeneric(DomainEvent event) {
        if (event == null) {
            log.warn("publishGeneric called with null event — dropping");
            return;
        }
        GatewayTopic topic = BridgeTopics.MAP.get(event.getClass());
        if (topic == null) {
            log.warn("publishGeneric: no BridgeTopics.MAP entry for {} — dropping. " +
                    "Add the event type to BridgeTopics.MAP to enable generic publishing.",
                    event.getClass().getSimpleName());
            return;
        }
        try {
            // Register the Jdk8Module on first use so Optional / Stream /
            // other Java 8 types in event payloads serialize correctly.
            // This is idempotent — ObjectMapper.registerModules is a no-op
            // for already-registered modules.
            if (!jdk8ModuleRegistered) {
                objectMapper.registerModule(new com.fasterxml.jackson.datatype.jdk8.Jdk8Module());
                jdk8ModuleRegistered = true;
            }
            ObjectNode payload = objectMapper.valueToTree(event);
            // Wrap in a metadata envelope so consumers can decode without
            // knowing the class name on the wire.
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("topicId", topic.wireId());
            envelope.put("topicVersion", topic.version());
            envelope.put("eventType", event.getClass().getSimpleName());
            envelope.set("payload", payload);
            router.publish(topic, objectMapper.writeValueAsBytes(envelope));
        } catch (Exception e) {
            log.warn("publishGeneric failed for {}: {}", event.getClass().getSimpleName(), e.getMessage());
        }
    }

    private volatile boolean jdk8ModuleRegistered = false;

    private ObjectNode pnlPayload(PnlUpdatedEvent pnl) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("realizedPnlPaisa", pnl.realizedPnlPaisa());
        node.put("unrealizedPnlPaisa", pnl.unrealizedPnlPaisa());
        node.put("netExposurePaisa", pnl.netExposurePaisa());
        return node;
    }

    private ObjectNode strategyMetricsPayload(StrategyMetricsSnapshot snap) {
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode counters = node.putArray("counters");
        for (var e : snap.counters().entrySet()) {
            ObjectNode entry = counters.addObject();
            entry.put("key", e.getKey());
            entry.put("count", e.getValue());
        }
        return node;
    }

    private void putSymbolFields(ObjectNode node, String symbol, ExchangeSegment segment) {
        String canonical = canonicalSymbol(symbol, segment);
        node.put("symbol", canonical);
        node.put("canonicalSymbol", canonical);
    }

    private String canonicalSymbol(String symbol, ExchangeSegment segment) {
        if (instrumentResolver == null || symbol == null || symbol.isBlank()) {
            return symbol;
        }
        try {
            return instrumentResolver.toCanonicalSymbol(symbol, segment);
        } catch (Exception ex) {
            return symbol;
        }
    }

    private ExchangeSegment resolveSegment(String symbol) {
        if (instrumentResolver == null || symbol == null || symbol.isBlank()) {
            return ExchangeSegment.NSE_EQ;
        }
        try {
            var instrument = instrumentResolver.resolveNormalized(symbol, ExchangeSegment.NSE_EQ);
            return instrument != null ? instrument.exchangeSegment() : ExchangeSegment.NSE_EQ;
        } catch (Exception ex) {
            return ExchangeSegment.NSE_EQ;
        }
    }

    // ── Depth Analytics Publishing ──

    /**
     * Publish depth analytics events to WebSocket clients.
     * Called by DepthAnalyticsPipeline consumers.
     */
    public void publishDepthAnalytics(Object analyticsEvent) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(analyticsEvent);
            GatewayTopic topic = resolveAnalyticsTopic(analyticsEvent);
            router.publish(topic, payload);
        } catch (Exception ex) {
            log.warn("Failed to publish depth analytics event: {}", ex.getMessage());
        }
    }

    /**
     * Publish an OrderBook snapshot directly to ORDER_BOOK_SNAPSHOT topic.
     * Used by REST controllers and the live depth publisher to push
     * per-symbol book state on demand (REST hydrates via this on first
     * subscribe; subsequent updates flow through the event bus).
     */
    public void publishOrderBookSnapshot(Object orderBookSnapshot) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(orderBookSnapshot);
            router.publish(GatewayTopic.ORDER_BOOK_SNAPSHOT, payload);
        } catch (Exception ex) {
            log.warn("Failed to publish order book snapshot: {}", ex.getMessage());
        }
    }

    private GatewayTopic resolveAnalyticsTopic(Object event) {
        String className = event.getClass().getSimpleName();
        return switch (className) {
            case "DepthImbalanceSnapshot" -> GatewayTopic.DEPTH_IMBALANCE;
            case "HeatmapChunk" -> GatewayTopic.HEATMAP_CHUNK;
            case "IcebergSignal" -> GatewayTopic.ICEBERG_ALERT;
            case "AbsorptionSignal" -> GatewayTopic.ABSORPTION_ALERT;
            case "SRLevelsUpdate" -> GatewayTopic.SR_LEVELS_UPDATE;
            case "OrderBookSnapshot" -> GatewayTopic.ORDER_BOOK_SNAPSHOT;
            default -> GatewayTopic.MARKET_DEPTH;
        };
    }

}
