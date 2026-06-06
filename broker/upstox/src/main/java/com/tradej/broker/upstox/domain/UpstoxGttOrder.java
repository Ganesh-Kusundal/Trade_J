package com.tradej.broker.upstox.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.core.domain.value.ExchangeSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * A complete GTT order as returned by the Upstox API.
 */
public record UpstoxGttOrder(
        String gttOrderId,
        String type,                 // SINGLE or MULTIPLE
        String exchange,             // e.g. NSE_EQ
        String tradingSymbol,
        String instrumentToken,
        long quantity,
        String product,              // I, D, or MTF
        List<UpstoxGttRule> rules,
        long expiresAt,              // epoch millis
        long createdAt               // epoch millis
) {
    /**
     * Parses a GTT order from a JSON response node.
     */
    public static UpstoxGttOrder fromJson(JsonNode node) {
        List<UpstoxGttRule> rules = new ArrayList<>();
        JsonNode rulesNode = node.get("rules");
        if (rulesNode != null && rulesNode.isArray()) {
            for (JsonNode r : rulesNode) {
                rules.add(UpstoxGttRule.fromJson(r));
            }
        }
        return new UpstoxGttOrder(
                node.has("gtt_order_id") ? node.get("gtt_order_id").asText() : "",
                node.has("type") ? node.get("type").asText() : "",
                node.has("exchange") ? node.get("exchange").asText() : "",
                node.has("trading_symbol") ? node.get("trading_symbol").asText() : "",
                node.has("instrument_token") ? node.get("instrument_token").asText() : "",
                node.has("quantity") ? node.get("quantity").asLong() : 0L,
                node.has("product") ? node.get("product").asText() : "",
                List.copyOf(rules),
                node.has("expires_at") ? node.get("expires_at").asLong() / 1000L : 0L,
                node.has("created_at") ? node.get("created_at").asLong() / 1000L : 0L
        );
    }

    /**
     * Returns the ExchangeSegment inferred from the exchange string.
     */
    public ExchangeSegment exchangeSegment() {
        if (exchange == null) return ExchangeSegment.NSE_EQ;
        return switch (exchange.toUpperCase()) {
            case "NSE_EQ" -> ExchangeSegment.NSE_EQ;
            case "BSE_EQ" -> ExchangeSegment.BSE_EQ;
            case "NSE_FO", "NFO" -> ExchangeSegment.NSE_FNO;
            case "BSE_FO", "BFO" -> ExchangeSegment.BSE_FNO;
            case "MCX" -> ExchangeSegment.MCX_COMM;
            default -> ExchangeSegment.NSE_EQ;
        };
    }
}
