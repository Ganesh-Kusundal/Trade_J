package com.tradej.broker.dhan.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.dhan.adapter.DhanInstrumentResolver;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.mapper.DhanPayloadNormalizer;
import com.tradej.broker.dhan.websocket.feed.DhanMarketFeedPacket;
import com.tradej.broker.core.dedup.MarketTickDedupFilter;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.OrderStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Normalizes raw Dhan WebSocket feed packets and order/trade payloads
 * into typed domain events.
 *
 * <p>Encapsulates the dedup filter, order status tracking, and metric recording
 * so that {@link DhanWebSocketMultiplexer} only needs to delegate and publish.
 *
 * <p>Thread-safe: uses {@link ConcurrentHashMap} for order status tracking.
 */
public final class DhanMarketEventNormalizer {

    private final DhanInstrumentResolver resolver;
    private final DhanPayloadNormalizer normalizer;
    private final EventMetadataFactory metadataFactory;
    private final MarketTickDedupFilter tickDedupFilter = new MarketTickDedupFilter();
    private final Map<String, OrderStatus> latestOrderStatuses = new ConcurrentHashMap<>();

    /**
     * @param resolver        instrument resolver for security ID lookups
     * @param normalizer      payload-to-domain mapper
     * @param metadataFactory event metadata factory
     */
    public DhanMarketEventNormalizer(
            DhanInstrumentResolver resolver,
            DhanPayloadNormalizer normalizer,
            EventMetadataFactory metadataFactory
    ) {
        this.resolver = resolver;
        this.normalizer = normalizer;
        this.metadataFactory = metadataFactory;
    }

    // ── Feed packet normalization ─────────────────────────────────────

    /**
     * Normalizes a raw Dhan market feed packet into a {@link MarketTickEvent}.
     *
     * @param packet   the raw feed packet
     * @param feedMode the active feed mode for this subscription
     * @return the normalized tick event, or empty if the packet is a heartbeat,
     *         market status, prev-close signal, or a duplicate tick
     */
    public Optional<MarketTickEvent> normalizeFeedPacket(DhanMarketFeedPacket packet, FeedMode feedMode) {
        if (packet instanceof DhanMarketFeedPacket.Heartbeat
                || packet instanceof DhanMarketFeedPacket.MarketStatus
                || packet instanceof DhanMarketFeedPacket.PrevClose) {
            return Optional.empty();
        }
        DhanInstrumentDefinition definition = resolver.requireSecurityId(packet.securityId());
        MarketTickEvent tick = normalizer.normalizeFeedPacket(packet, definition, feedMode);
        if (tick == null) {
            return Optional.empty();
        }
        // R7: Drop duplicate ticks from broker retransmission
        if (tickDedupFilter.isDuplicate(tick.symbol(), tick.segment().name(), tick.exchangeTimestampEpochMs())) {
            return Optional.empty();
        }
        return Optional.of(tick);
    }

    // ── Order payload normalization ───────────────────────────────────

    /**
     * Normalizes a raw Dhan order update payload into the appropriate domain event(s).
     * <p>
     * Returns one of:
     * <ul>
     *   <li>{@link OrderRejected} — if the order was rejected (emitted once)</li>
     *   <li>{@link OrderAccepted} — if the order was just placed (first occurrence)</li>
     *   <li>{@code empty} — for fill status transitions (handled by {@link #normalizeTradePayload})</li>
     * </ul>
     *
     * @param update the raw JSON order update from the order stream
     * @return the domain event, or empty if the update should be suppressed
     */
    public Optional<DomainEvent> normalizeOrderPayload(JsonNode update) {
        try {
            DhanJsonResponse response = new DhanJsonResponse(update);
            DhanInstrumentDefinition definition = resolver.resolveDhanPayload(update);
            Order order = normalizer.normalizeOrder(response, definition);
            OrderStatus previousStatus = latestOrderStatuses.put(order.orderId(), order.status());

            if (order.status().isRejected() && previousStatus != OrderStatus.REJECTED) {
                return Optional.of(new OrderRejected(
                        metadataFactory.correlated(order.correlationId(), 0),
                        order, order.rejectionReason()));
            }
            if (previousStatus == null) {
                return Optional.of(new OrderAccepted(
                        metadataFactory.correlated(order.correlationId(), 0), order));
            }
            // PART_TRADED/TRADED status transitions are handled by normalizeTradePayload
            // which emits OrderFilled with actual trade data — suppress empty-fill status
            // events to avoid duplicate processing in ExecutionHandler.
            return Optional.empty();
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    // ── Trade payload normalization ───────────────────────────────────

    /**
     * Normalizes a raw Dhan trade update payload into an {@link OrderFilled} event.
     *
     * @param update the raw JSON trade update from the order stream
     * @return the OrderFilled event, or empty on normalization failure
     */
    public Optional<OrderFilled> normalizeTradePayload(JsonNode update) {
        try {
            DhanJsonResponse response = new DhanJsonResponse(update);
            DhanInstrumentDefinition definition = resolver.resolveDhanPayload(update);
            Trade trade = normalizer.normalizeTrade(response, definition);
            Order order = normalizer.normalizeOrder(response, definition);
            latestOrderStatuses.put(order.orderId(), order.status());
            return Optional.of(new OrderFilled(
                    metadataFactory.correlated(order.correlationId(), 0),
                    order, List.of(trade)));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────

    /** Resets the tick dedup filter (call on reconnect). */
    public void resetDedup() {
        tickDedupFilter.reset();
    }
}
