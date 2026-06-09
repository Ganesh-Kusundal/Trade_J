package com.tradej.core.domain.event;

/**
 * Visitor for system and infrastructure events: health, reconciliation, scanning, and replay.
 */
public interface SystemVisitor {
    default void visit(ReconciliationHaltRequired event) {}
    default void visit(StreamHealthChanged event) {}
    default void visit(EventBusBackpressure event) {}
    default void visit(BrokerAdapterError event) {}
    default void visit(StrategyError event) {}
    default void visit(ScanHitProduced event) {}
    default void visit(ScanResultsPublished event) {}
    default void visit(ReplayTimeChangedEvent event) {}
}
