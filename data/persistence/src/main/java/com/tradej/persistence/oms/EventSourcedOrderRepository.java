package com.tradej.persistence.oms;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.tradej.core.domain.oms.CancelRequested;
import com.tradej.core.domain.oms.OrderAcknowledged;
import com.tradej.core.domain.oms.OrderCancelled;
import com.tradej.core.domain.oms.OrderEvent;
import com.tradej.core.domain.oms.OrderExpired;
import com.tradej.core.domain.oms.OrderFullyFilled;
import com.tradej.core.domain.oms.OrderPartiallyFilled;
import com.tradej.core.domain.oms.OrderProjection;
import com.tradej.core.domain.oms.OrderRejected;
import com.tradej.core.domain.oms.OrderStateMachine;
import com.tradej.core.domain.oms.OrderSubmitted;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Event-sourced repository that persists {@link OrderEvent}s to Chronicle Queue
 * and maintains an in-memory cache for fast state rebuild.
 */
public final class EventSourcedOrderRepository implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EventSourcedOrderRepository.class);

    private final ChronicleQueue queue;
    private final ObjectMapper mapper;
    private final Map<String, List<OrderEvent>> pending = new ConcurrentHashMap<>();
    private final AtomicLong corruptEntryCount = new AtomicLong();

    public EventSourcedOrderRepository(Path queuePath) {
        this.queue = ChronicleQueue.singleBuilder(queuePath.toFile()).build();
        this.mapper = createMapper();
        loadFromQueue();
    }

    /**
     * Append a single event — persists to Chronicle Queue and caches in memory.
     * Creates a fresh appender per call to safely support multithreaded access
     * (e.g., ExecutionHandler background thread).
     */
    public void append(OrderEvent event) {
        try {
            queue.createAppender().writeText(mapper.writeValueAsString(event));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize order event", e);
        }
        pending.computeIfAbsent(event.orderId(), k -> new CopyOnWriteArrayList<>()).add(event);
    }

    /**
     * Append multiple events atomically.
     */
    public void appendAll(List<? extends OrderEvent> events) {
        for (OrderEvent event : events) {
            append(event);
        }
    }

    /**
     * Rebuild the {@link OrderProjection} for a given order by replaying its events.
     * Returns {@code null} if no events exist for the order.
     */
    public OrderProjection rebuild(String orderId) {
        OrderStateMachine sm = rebuildStateMachine(orderId);
        return sm == null ? null : sm.toProjection();
    }

    /**
     * Rebuild the full {@link OrderStateMachine} for a given order.
     * Returns {@code null} if no events exist for the order.
     */
    public OrderStateMachine rebuildStateMachine(String orderId) {
        List<OrderEvent> events = pending.get(orderId);
        if (events == null || events.isEmpty()) {
            return null;
        }

        OrderEvent first = events.getFirst();
        if (!(first instanceof OrderSubmitted submitted)) {
            throw new IllegalStateException("First event for order " + orderId + " must be OrderSubmitted");
        }

        OrderStateMachine sm = new OrderStateMachine(orderId, submitted.symbol(), submitted.totalQuantity());
        for (OrderEvent event : events) {
            sm.on(event);
        }
        return sm;
    }

    /**
     * List all order IDs currently tracked in the repository.
     */
    public List<String> knownOrderIds() {
        return List.copyOf(pending.keySet());
    }

    /**
     * Returns a copy of all persisted events for the given internal order ID.
     */
    public List<OrderEvent> orderEvents(String orderId) {
        List<OrderEvent> events = pending.get(orderId);
        return events == null ? List.of() : List.copyOf(events);
    }

    /**
     * Count of active (non-final) orders.
     */
    public long activeCount() {
        return pending.values().stream()
                .filter(events -> {
                    OrderProjection proj = rebuild(events.getFirst().orderId());
                    return proj != null && proj.hasOpenPosition();
                })
                .count();
    }

    /**
     * Count of rejected orders.
     */
    public long rejectedCount() {
        return pending.values().stream()
                .filter(events -> {
                    OrderProjection proj = rebuild(events.getFirst().orderId());
                    return proj != null && proj.status() == com.tradej.core.domain.oms.LifecycleState.REJECTED;
                })
                .count();
    }

    @Override
    public void close() {
        queue.close();
    }

    // --- JSON serialization ---

    private static ObjectMapper createMapper() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(OrderEvent.class, new OrderEventSerializer());
        module.addDeserializer(OrderEvent.class, new OrderEventDeserializer());
        return new ObjectMapper().registerModule(module);
    }

    /**
     * Load existing events from Chronicle Queue into the in-memory cache on startup.
     */
    private void loadFromQueue() {
        long loaded = 0L;
        try (ExcerptTailer tailer = queue.createTailer()) {
            String raw;
            while ((raw = tailer.readText()) != null) {
                try {
                    OrderEvent event = mapper.readValue(raw, OrderEvent.class);
                    pending.computeIfAbsent(event.orderId(), k -> new CopyOnWriteArrayList<>()).add(event);
                    loaded++;
                } catch (java.io.IOException ex) {
                    long count = corruptEntryCount.incrementAndGet();
                    String preview = raw.length() > 80 ? raw.substring(0, 80) + "..." : raw;
                    log.warn("Corrupt Chronicle Queue entry (JSON parse failure) — skipping (total corrupt: {}). Preview: {}",
                            count, preview);
                } catch (RuntimeException ex) {
                    long count = corruptEntryCount.incrementAndGet();
                    String preview = raw.length() > 80 ? raw.substring(0, 80) + "..." : raw;
                    log.warn("Corrupt Chronicle Queue entry (deserialization failure) — skipping (total corrupt: {}). Preview: {}",
                            count, preview);
                }
            }
        }
        log.info("Loaded {} OMS events from Chronicle Queue ({} corrupt entries skipped)",
                loaded, corruptEntryCount.get());
    }

    /** Returns the number of corrupt queue entries encountered during startup. */
    public long corruptEntryCount() {
        return corruptEntryCount.get();
    }

    private static final class OrderEventSerializer extends JsonSerializer<OrderEvent> {
        @Override
        public void serialize(OrderEvent event, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("eventType", event.type().name());
            gen.writeStringField("orderId", event.orderId());
            switch (event) {
                case OrderSubmitted e -> {
                    gen.writeStringField("correlationId", e.correlationId());
                    gen.writeStringField("symbol", e.symbol());
                    gen.writeNumberField("totalQuantity", e.totalQuantity());
                }
                case OrderAcknowledged e -> gen.writeStringField("exchangeOrderId", e.exchangeOrderId());
                case OrderPartiallyFilled e -> {
                    gen.writeNumberField("filledQuantity", e.filledQuantity());
                    gen.writeNumberField("pricePaisa", e.pricePaisa());
                }
                case OrderFullyFilled e -> {
                    gen.writeNumberField("totalQuantity", e.totalQuantity());
                    gen.writeNumberField("pricePaisa", e.pricePaisa());
                }
                case CancelRequested ignored -> {}
                case OrderCancelled ignored -> {}
                case OrderRejected e -> gen.writeStringField("reason", e.reason());
                case OrderExpired ignored -> {}
            }
            gen.writeEndObject();
        }
    }

    private static final class OrderEventDeserializer extends JsonDeserializer<OrderEvent> {
        @Override
        public OrderEvent deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.readValueAsTree();
            String type = node.get("eventType").asText();
            String orderId = node.get("orderId").asText();

            return switch (OrderEvent.EventType.valueOf(type)) {
                case SUBMITTED -> new OrderSubmitted(
                        orderId,
                        node.path("correlationId").asText(""),
                        node.path("symbol").asText(""),
                        node.path("totalQuantity").asLong(0L)
                );
                case ACKNOWLEDGED -> new OrderAcknowledged(
                        orderId,
                        node.path("exchangeOrderId").asText("")
                );
                case PARTIALLY_FILLED -> new OrderPartiallyFilled(
                        orderId,
                        node.path("filledQuantity").asLong(0L),
                        node.path("pricePaisa").asLong(0L)
                );
                case FULLY_FILLED -> new OrderFullyFilled(
                        orderId,
                        node.path("totalQuantity").asLong(0L),
                        node.path("pricePaisa").asLong(0L)
                );
                case CANCELLED -> new OrderCancelled(orderId);
                case REJECTED -> new OrderRejected(
                        orderId,
                        node.path("reason").asText("")
                );
                case EXPIRED -> new OrderExpired(orderId);
                case CANCEL_REQUESTED -> new CancelRequested(orderId);
            };
        }
    }
}
