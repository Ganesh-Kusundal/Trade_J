package com.tradej.broker.upstox.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.core.domain.value.PriceMath;

/**
 * Normalizes Upstox market JSON price fields to paisa.
 */
public final class UpstoxPriceParser {

    private UpstoxPriceParser() {
    }

    public static long pricePaisaFromNode(JsonNode node) {
        if (node == null) {
            throw new IllegalStateException("Upstox price node is missing");
        }
        if (node.has("last_price")) {
            return PriceMath.toPaisa(String.valueOf(node.get("last_price").asDouble()));
        }
        if (node.has("ltp")) {
            return PriceMath.toPaisa(String.valueOf(node.get("ltp").asDouble()));
        }
        if (node.has("open")) {
            return PriceMath.toPaisa(String.valueOf(node.get("open").asDouble()));
        }
        if (node.has("close")) {
            return PriceMath.toPaisa(String.valueOf(node.get("close").asDouble()));
        }
        throw new IllegalStateException("Upstox price node has no last_price/ltp/open/close: " + node);
    }

    public static long optionalPricePaisa(JsonNode node, String field) {
        if (node == null || !node.has(field)) {
            return 0L;
        }
        return PriceMath.toPaisa(String.valueOf(node.get(field).asDouble()));
    }
}
