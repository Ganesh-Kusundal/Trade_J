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
import com.tradej.core.domain.port.EventBus;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
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
        // Register Jdk8Module + JavaTimeModule once at construction time so
        // Optional / Stream / LocalDate / LocalDateTime / Instant fields in
        // event payloads serialize correctly. registerModule is idempotent,
        // so the shared Spring-injected ObjectMapper is safe to register on
        // even if other components have already registered the same modules.
        // The null guard lets BridgeTopicsTest construct a bridge with
        // (null, null) purely to inspect the serializer table via reflection.
        if (objectMapper != null) {
            objectMapper.registerModule(new com.fasterxml.jackson.datatype.jdk8.Jdk8Module());
            objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            // LocalDate / LocalDateTime / Instant: emit ISO-8601 strings
            // (not numeric arrays). Default JSR-310 behaviour is to emit
            // arrays like [2026, 6, 25].
            objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }
    }

    /** Serializer entry linking a domain event type to its topic, payload builder, and envelope mode. */
    private record SerializerEntry(
            GatewayTopic topic,
            Function<DomainEvent, ObjectNode> serializer,
            boolean useGenericEnvelope,
            Consumer<ObjectNode> postProcessor
    ) {
        /** Bespoke envelope (legacy): the per-event serializer produces a flat JSON payload. */
        static SerializerEntry bespoke(GatewayTopic topic, Function<DomainEvent, ObjectNode> serializer) {
            return new SerializerEntry(topic, serializer, false, null);
        }
        /** Generic envelope: the per-event serializer is ignored; the publishGeneric path is used. */
        static SerializerEntry generic(GatewayTopic topic) {
            return new SerializerEntry(topic, null, true, null);
        }
        /** Generic envelope with a payload post-processor (applied to the payload before envelope wrap). */
        static SerializerEntry generic(GatewayTopic topic, Consumer<ObjectNode> postProcessor) {
            return new SerializerEntry(topic, null, true, postProcessor);
        }
    }

    private Map<Class<? extends DomainEvent>, SerializerEntry> buildSerializerMap() {
        // Topic mapping is owned by BridgeTopics (single source of truth).
        // This map is keyed by the same class. Asserting the two stay in
        // sync is the job of BridgeTopicsTest.
        //
        // Every event is routed through publishGeneric (the
        // {topicId, topicVersion, eventType, payload} envelope). New
        // event types should be added to BridgeTopics.MAP and listed
        // here with SerializerEntry.generic(topic). The bespoke
        // SerializerEntry factory is retained for future cases that
        // need a custom JSON shape; none are currently in use.
        Map<Class<? extends DomainEvent>, SerializerEntry> m = new java.util.LinkedHashMap<>();
        // Symbol-bearing events get the CANONICAL_SYMBOL_POST_PROCESSOR so
        // consumers that route on canonicalSymbol (lost when the bespoke
        // putSymbolFields helper was deleted in commit bb9772a and then
        // regressed in commit ecd8342) get the field back. The post-
        // processor copies payload.symbol (or, for nested Candle events,
        // payload.candle.symbol) to a new top-level payload.canonicalSymbol
        // field. For all other events, no post-processor runs.
        m.put(MarketTickEvent.class,        SerializerEntry.generic(BridgeTopics.MAP.get(MarketTickEvent.class),        CANONICAL_SYMBOL_POST_PROCESSOR));
        m.put(DepthUpdateEvent.class,       SerializerEntry.generic(BridgeTopics.MAP.get(DepthUpdateEvent.class),       CANONICAL_SYMBOL_POST_PROCESSOR));
        m.put(CandleDeveloping.class,       SerializerEntry.generic(BridgeTopics.MAP.get(CandleDeveloping.class),       CANONICAL_SYMBOL_POST_PROCESSOR));
        m.put(CandleClosed.class,           SerializerEntry.generic(BridgeTopics.MAP.get(CandleClosed.class),           CANONICAL_SYMBOL_POST_PROCESSOR));
        m.put(OrderAccepted.class,          SerializerEntry.generic(BridgeTopics.MAP.get(OrderAccepted.class)));
        m.put(OrderRejected.class,          SerializerEntry.generic(BridgeTopics.MAP.get(OrderRejected.class)));
        m.put(OrderFilled.class,            SerializerEntry.generic(BridgeTopics.MAP.get(OrderFilled.class)));
        m.put(TradeOpened.class,            SerializerEntry.generic(BridgeTopics.MAP.get(TradeOpened.class)));
        m.put(TradeClosed.class,            SerializerEntry.generic(BridgeTopics.MAP.get(TradeClosed.class)));
        m.put(SignalGenerated.class,        SerializerEntry.generic(BridgeTopics.MAP.get(SignalGenerated.class)));
        m.put(ReplayTimeChangedEvent.class, SerializerEntry.generic(BridgeTopics.MAP.get(ReplayTimeChangedEvent.class)));
        // P5.1 follow-up worked example: PnlUpdatedEvent opts into the
        // generic envelope. Consumers read {topicId, topicVersion,
        // eventType, payload: {metadata, realizedPnlPaisa, ...}}
        // instead. Note: publishGeneric emits the metadata field in
        // the payload (the original bespoke serializer did not), so
        // the consumer schema gains a `metadata` field. See
        // PnlUpdatedEventEnvelopeMigrationTest.
        m.put(PnlUpdatedEvent.class,        SerializerEntry.generic(BridgeTopics.MAP.get(PnlUpdatedEvent.class)));
        m.put(ScanResultsPublished.class,   SerializerEntry.generic(BridgeTopics.MAP.get(ScanResultsPublished.class)));
        m.put(OptionChainUpdated.class,     SerializerEntry.generic(BridgeTopics.MAP.get(OptionChainUpdated.class)));
        m.put(GreeksComputed.class,         SerializerEntry.generic(BridgeTopics.MAP.get(GreeksComputed.class)));
        m.put(MaxPainComputed.class,        SerializerEntry.generic(BridgeTopics.MAP.get(MaxPainComputed.class)));
        m.put(GammaExposureComputed.class,  SerializerEntry.generic(BridgeTopics.MAP.get(GammaExposureComputed.class)));
        m.put(StrategyMetricsSnapshot.class, SerializerEntry.generic(BridgeTopics.MAP.get(StrategyMetricsSnapshot.class)));
        return Map.copyOf(m);
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
        eventBus.subscribe(StrategyMetricsSnapshot.class, this::onDomainEvent);
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
                    // publishGeneric emits the {topicId, topicVersion,
                    // eventType, payload} envelope. Note:
                    // publishGeneric does NOT inject the correlationId
                    // field at the top level (it lives in the
                    // metadata sub-object inside the payload instead).
                    // The per-event postProcessor (when set) is applied
                    // to the payload BEFORE the envelope is built — for
                    // symbol-bearing events this is the hook that
                    // restores the canonicalSymbol field.
                    publishGeneric(event, entry.postProcessor());
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
    // Every event in buildSerializerMap uses publishGeneric. The
    // generic publisher below uses Jackson reflection to serialize
    // the event and embeds the topic metadata (wireId + version) as
    // JSON tags. Adding a new event type to the gateway no longer
    // requires a per-event serializer method — just call
    // publishGeneric(event) and the topic is resolved from the event
    // class via the BridgeTopics.MAP table. The bespoke SerializerEntry
    // factory is retained for future cases that need a custom JSON
    // shape; none are currently in use.

    /**
     * Per-event payload post-processor that restores the {@code canonicalSymbol}
     * field for symbol-bearing events (MarketTickEvent, DepthUpdateEvent,
     * CandleDeveloping, CandleClosed). The bespoke {@code putSymbolFields}
     * helper (deleted in commit bb9772a) emitted a top-level
     * {@code canonicalSymbol} on these events; the migration to the generic
     * envelope in commit ecd8342 dropped the field. Consumers that route on
     * {@code canonicalSymbol} need it back.
     *
     * <p>The canonical symbol is the raw {@code symbol} value — the bespoke
     * resolver mapped them 1:1 when no {@code InstrumentResolver} was wired,
     * which is the default for tests. For MarketTick and Depth, {@code symbol}
     * lives at the top level of the payload. For Candle events the record is
     * nested under {@code payload.candle}, so the post-processor falls back
     * to {@code payload.candle.symbol}. The output is always a top-level
     * {@code payload.canonicalSymbol} field, matching the bespoke wire shape.
     */
    public static final Consumer<ObjectNode> CANONICAL_SYMBOL_POST_PROCESSOR = node -> {
        if (node == null) {
            return;
        }
        // Skip if already present (defensive — bespoke writers may have set it).
        JsonNode existing = node.get("canonicalSymbol");
        if (existing != null && existing.isTextual()) {
            return;
        }
        String symbolText = symbolTextOf(node);
        if (symbolText != null) {
            node.put("canonicalSymbol", symbolText);
        }
    };

    private static String symbolTextOf(ObjectNode node) {
        // Top-level symbol (MarketTickEvent, DepthUpdateEvent, …).
        JsonNode top = node.get("symbol");
        if (top != null && top.isTextual()) {
            return top.asText();
        }
        // Nested Candle record (CandleClosed, CandleDeveloping).
        JsonNode candle = node.get("candle");
        if (candle instanceof ObjectNode candleObj) {
            JsonNode c = candleObj.get("symbol");
            if (c != null && c.isTextual()) {
                return c.asText();
            }
        }
        return null;
    }

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
        publishGeneric(event, null);
    }

    /**
     * Generic publisher with an optional payload post-processor. The
     * {@code postProcessor} (when non-null) is invoked on the payload
     * {@code ObjectNode} BEFORE the envelope is built and published.
     * Used by symbol-bearing events to restore the {@code canonicalSymbol}
     * field that the bespoke putSymbolFields helper used to emit.
     */
    public void publishGeneric(DomainEvent event, Consumer<ObjectNode> postProcessor) {
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
            // Jdk8Module + JavaTimeModule are registered once in the
            // constructor — by the time publishGeneric runs, the mapper
            // is already configured to handle Optional / Stream /
            // LocalDate / LocalDateTime / Instant fields.
            ObjectNode payload = objectMapper.valueToTree(event);
            // Apply the per-event post-processor BEFORE wrapping the
            // payload in the envelope. This is the hook that restores
            // the canonicalSymbol field for symbol-bearing events.
            if (postProcessor != null) {
                postProcessor.accept(payload);
            }
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
