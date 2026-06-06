package com.tradej.broker.upstox.domain;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A single GTT order rule (leg) defining trigger conditions for entry, target, or stop-loss.
 */
public record UpstoxGttRule(
        String strategy,        // ENTRY, TARGET, STOPLOSS
        String triggerType,     // ABOVE, BELOW, IMMEDIATE
        double triggerPrice,    // Trigger price in decimal
        Double trailingGap,     // Optional trailing gap for TSL
        Integer marketProtection // Optional market protection percentage (-1 = auto, 0 = none, 1-25 = custom)
) {
    public static UpstoxGttRule entryAbove(double triggerPrice) {
        return new UpstoxGttRule("ENTRY", "ABOVE", triggerPrice, null, null);
    }

    public static UpstoxGttRule entryBelow(double triggerPrice) {
        return new UpstoxGttRule("ENTRY", "BELOW", triggerPrice, null, null);
    }

    public static UpstoxGttRule entryImmediate(double triggerPrice) {
        return new UpstoxGttRule("ENTRY", "IMMEDIATE", triggerPrice, null, null);
    }

    public static UpstoxGttRule target(double triggerPrice) {
        return new UpstoxGttRule("TARGET", "IMMEDIATE", triggerPrice, null, null);
    }

    public static UpstoxGttRule stopLoss(double triggerPrice) {
        return new UpstoxGttRule("STOPLOSS", "IMMEDIATE", triggerPrice, null, null);
    }

    public static UpstoxGttRule trailingStopLoss(double triggerPrice, double trailingGap) {
        return new UpstoxGttRule("STOPLOSS", "IMMEDIATE", triggerPrice, trailingGap, null);
    }

    /**
     * Parses a rule from a JSON response node.
     */
    public static UpstoxGttRule fromJson(JsonNode node) {
        String strategy = node.has("strategy") ? node.get("strategy").asText() : "";
        String triggerType = node.has("trigger_type") ? node.get("trigger_type").asText() : "";
        double triggerPrice = node.has("trigger_price") ? node.get("trigger_price").asDouble() : 0.0;
        Double trailingGap = node.has("trailing_gap") ? node.get("trailing_gap").asDouble() : null;
        Integer marketProtection = node.has("market_protection") ? node.get("market_protection").asInt() : null;
        return new UpstoxGttRule(strategy, triggerType, triggerPrice, trailingGap, marketProtection);
    }
}
