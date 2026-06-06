package com.tradej.broker.upstox.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.upstox.mapper.UpstoxDomainMapper;
import com.tradej.core.domain.event.*;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;


import java.util.ArrayList;
import java.util.List;

/**
 * Parses JSON text messages from the Upstox portfolio stream WebSocket
 * and converts them to canonical {@link OrderUpdateEvent} domain events.
 * <p>
 * The Upstox portfolio stream sends JSON messages containing order, position,
 * and holding updates whenever a change occurs in the user's portfolio.
 */
public final class UpstoxPortfolioStreamParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EventMetadataFactory metadataFactory;

    public UpstoxPortfolioStreamParser(EventMetadataFactory metadataFactory) {
        this.metadataFactory = metadataFactory;
    }

    /**
     * Parses a JSON text message from the portfolio stream WebSocket.
     *
     * @param textMessage the raw JSON text from the WebSocket
     * @return a list of domain events parsed from the message, or empty if the message was a heartbeat or unrecognized
     */
    public List<DomainEvent> parseMessage(String textMessage) {
        if (textMessage == null || textMessage.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(textMessage);
            // Check for heartbeat or empty messages
            if (root.isMissingNode() || root.isNull()) {
                return List.of();
            }
            // The portfolio stream wraps updates in a structure
            // Different message types: order_update, position_update, holding_update
            String type = root.has("type") ? root.get("type").asText() : "";
            JsonNode data = root.has("data") ? root.get("data") : root;
            if (data == null || data.isNull()) {
                return List.of();
            }
            return switch (type) {
                case "order_update" -> List.of(parseOrderUpdate(data));
                case "position_update" -> List.of(parsePositionUpdate(data));
                case "holding_update" -> List.of(parseHoldingUpdate(data));
                default -> {
                    // Try to infer type from the data structure
                    if (data.has("order_id")) {
                        yield List.of(parseOrderUpdate(data));
                    }
                    yield List.of();
                }
            };
        } catch (Exception e) {
            // Malformed message — skip
            return List.of();
        }
    }

    private OrderUpdateEvent parseOrderUpdate(JsonNode node) {
        EventMetadata metadata = metadataFactory.root();
        Order order = parseOrder(node);
        String reason = node.has("status_message") ? node.get("status_message").asText() : "";
        List<Trade> fills = parseTrades(node);

        // Determine the order status and emit the appropriate event
        String status = node.has("status") ? node.get("status").asText().toUpperCase() : "";
        return switch (status) {
            case "OPEN", "PENDING" -> new OrderAccepted(metadata, order);
            case "COMPLETE", "FILLED" -> new OrderFilled(metadata, order, fills);
            case "PARTIALLY_FILLED" -> new OrderPartiallyFilled(metadata, order, fills);
            case "CANCELLED", "CANCELED" -> new OrderCancelled(metadata, order, reason);
            case "REJECTED" -> new OrderRejected(metadata, order, reason);
            case "MODIFIED" -> new OrderModified(metadata, order);
            default -> new OrderAccepted(metadata, order);
        };
    }

    private PositionUpdateEvent parsePositionUpdate(JsonNode node) {
        EventMetadata metadata = metadataFactory.root();
        long quantity = node.has("quantity") ? node.get("quantity").asLong() : 0L;
        long avgPrice = node.has("average_price") ? (long) (node.get("average_price").asDouble() * 100) : 0L;
        long ltp = node.has("last_price") ? (long) (node.get("last_price").asDouble() * 100) : 0L;
        long unrealizedPnl = node.has("unrealised_pnl") ? (long) (node.get("unrealised_pnl").asDouble() * 100) : 0L;
        long realizedPnl = node.has("realised_pnl") ? (long) (node.get("realised_pnl").asDouble() * 100) : 0L;
        return new PositionUpdateEvent(metadata,
                node.has("trading_symbol") ? node.get("trading_symbol").asText() : "",
                UpstoxDomainMapper.parseSegment(node),
                quantity, avgPrice, ltp, unrealizedPnl, realizedPnl);
    }

    private DomainEvent parseHoldingUpdate(JsonNode node) {
        EventMetadata metadata = metadataFactory.root();
        long quantity = node.has("quantity") ? node.get("quantity").asLong() : 0L;
        long avgPrice = node.has("average_price") ? (long) (node.get("average_price").asDouble() * 100) : 0L;
        long ltp = node.has("last_price") ? (long) (node.get("last_price").asDouble() * 100) : 0L;
        return new PnlUpdatedEvent(metadata, 0L, (avgPrice - ltp) * quantity, Math.abs(quantity * ltp));
    }

    private Order parseOrder(JsonNode node) {
        return new Order(
                node.has("order_id") ? node.get("order_id").asText() : "",
                node.has("tag") ? node.get("tag").asText() : "",
                node.has("trading_symbol") ? node.get("trading_symbol").asText() : "",
                UpstoxDomainMapper.parseSegment(node),
                UpstoxDomainMapper.parseSide(node),
                UpstoxDomainMapper.parseProductType(node),
                UpstoxDomainMapper.parseOrderType(node),
                UpstoxDomainMapper.parseStatus(node),
                node.has("quantity") ? node.get("quantity").asLong() : 0L,
                node.has("filled_quantity") ? node.get("filled_quantity").asLong() : 0L,
                node.has("price") ? (long) (node.get("price").asDouble() * 100) : 0L,
                node.has("trigger_price") ? (long) (node.get("trigger_price").asDouble() * 100) : 0L,
                node.has("exchange_timestamp") ? parseTimestamp(node.get("exchange_timestamp")) : 0L,
                node.has("status_message") ? node.get("status_message").asText() : ""
        );
    }

    private List<Trade> parseTrades(JsonNode node) {
        List<Trade> trades = new ArrayList<>();
        JsonNode tradesNode = node.get("fills");
        if (tradesNode != null && tradesNode.isArray()) {
            for (JsonNode fill : tradesNode) {
                trades.add(new Trade(
                        fill.has("trade_id") ? fill.get("trade_id").asText() : "",
                        fill.has("order_id") ? fill.get("order_id").asText() : "",
                        fill.has("trading_symbol") ? fill.get("trading_symbol").asText() : "",
                        UpstoxDomainMapper.parseSegment(fill),
                        UpstoxDomainMapper.parseSide(fill),
                        fill.has("quantity") ? fill.get("quantity").asLong() : 0L,
                        fill.has("price") ? (long) (fill.get("price").asDouble() * 100) : 0L,
                        fill.has("exchange_timestamp") ? parseTimestamp(fill.get("exchange_timestamp")) : 0L
                ));
            }
        }
        return trades;
    }

    private static long parseTimestamp(JsonNode node) {
        if (node == null) return System.currentTimeMillis();
        if (node.isNumber()) {
            long val = node.asLong();
            return val > 1_000_000_000_000L ? val : val * 1000L;
        }
        return System.currentTimeMillis();
    }
}
