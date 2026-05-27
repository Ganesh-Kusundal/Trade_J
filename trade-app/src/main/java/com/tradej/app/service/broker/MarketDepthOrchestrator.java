package com.tradej.app.service.broker;

import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.core.domain.value.FeedMode;

import java.util.Collection;

public final class MarketDepthOrchestrator {
    private final WebSocketMultiplexer multiplexer;

    public MarketDepthOrchestrator(WebSocketMultiplexer multiplexer) {
        this.multiplexer = multiplexer;
    }

    public void startDepth(Collection<MarketSubscriptionRequest> subscriptions) {
        multiplexer.subscribe(subscriptions, FeedMode.DEPTH_20);
    }

    public void stopDepth(Collection<MarketSubscriptionRequest> subscriptions) {
        multiplexer.unsubscribe(subscriptions);
    }
}
