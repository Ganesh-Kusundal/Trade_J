package com.tradej.gateway.router;

import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.transport.WebSocketTransport;

import java.util.function.Predicate;

public final class NoOpGatewayTopicRouter extends GatewayTopicRouter {

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public void subscribe(WebSocketTransport transport, GatewayTopic topic) {}

    @Override
    public void unsubscribeAll(WebSocketTransport transport) {}

    @Override
    public void publish(GatewayTopic topic, byte[] payload) {}

    @Override
    public void publishFiltered(GatewayTopic topic, byte[] payload, Predicate<String> sessionFilter) {}

    @Override
    public int subscriberCount(GatewayTopic topic) {
        return 0;
    }

    @Override
    public long droppedEventCount() {
        return 0;
    }

    @Override
    public long sentEventCount() {
        return 0;
    }

    @Override
    public int queueDepth() {
        return 0;
    }

    @Override
    public long dropCount(WebSocketTransport transport) {
        return 0;
    }
}
