package com.tradej.broker.upstox.websocket;

import com.tradej.broker.upstox.config.UpstoxV3SubscriptionLimits;

import java.util.Objects;

/**
 * Broker-local handle for an Upstox V3 market-data subscription.
 * <p>
 * This is intentionally distinct from the cross-broker
 * {@code com.tradej.broker.api.model.MarketSubscriptionRequest} so we can
 * attach a V3 mode that the core {@code FeedMode} enum does not model
 * (notably {@code option_greeks}, which is a first-class V3 mode but
 * currently absent from {@code core/.../value/FeedMode.java}).
 * <p>
 * The multiplexer exposes a separate {@code subscribeV3(...)} entry point
 * that takes this type, so no cross-module change is required.
 */
public record UpstoxMarketSubscription(
        String symbol,
        String exchangeSegment,
        String instrumentToken,
        UpstoxV3SubscriptionLimits.Mode mode
) {

    public UpstoxMarketSubscription {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(exchangeSegment, "exchangeSegment");
        Objects.requireNonNull(instrumentToken, "instrumentToken");
        Objects.requireNonNull(mode, "mode");
    }
}
