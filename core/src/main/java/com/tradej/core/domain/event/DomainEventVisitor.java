package com.tradej.core.domain.event;

/**
 * Composite visitor for all domain event types.
 * Composed of focused sub-visitors: {@link MarketDataVisitor}, {@link OrderLifecycleVisitor},
 * {@link TradingVisitor}, and {@link SystemVisitor}.
 *
 * <p>Existing code that implements DomainEventVisitor continues to work unchanged.
 * New code should prefer implementing specific sub-visitors for clarity.
 */
public interface DomainEventVisitor extends MarketDataVisitor, OrderLifecycleVisitor, TradingVisitor, SystemVisitor {
}
