package com.tradej.gateway.bridge;

import com.tradej.gateway.protocol.GatewayTopic;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation reserved for a future annotation-driven bridge
 * registry. Currently unused — the registry is the
 * {@link BridgeTopics} table, because the {@code core} module is
 * dependency-free and cannot import a {@code gateway}-defined
 * annotation. If a future refactor moves the event classes into a
 * module that depends on the gateway, this annotation is ready to be
 * applied to each event class and consumed by a build-time processor.
 *
 * <p>Example (when used):
 * <pre>{@code
 * @BridgedTopic(GatewayTopic.MARKET_TICK)
 * public record MarketTickEvent(...) implements DomainEvent { … }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BridgedTopic {
    GatewayTopic value();
}
