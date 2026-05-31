package com.tradej.core.domain.event;

/**
 * Published by {@code StrategySandbox} when a strategy plugin throws an exception
 * or times out during candle evaluation. Allows the runtime to react to strategy
 * failures rather than silently swallowing them.
 *
 * @param metadata     Standard event metadata
 * @param strategyName Name of the failing strategy plugin (class simple name)
 * @param symbol       The symbol being evaluated when the error occurred
 * @param detail       Error message or "Timed out after Nms"
 */
public record StrategyError(
        EventMetadata metadata,
        String strategyName,
        String symbol,
        String detail
) implements DomainEvent {
}
