package com.tradej.core.domain.event;

/**
 * Visitor for {@link DomainEvent} types to avoid instanceof branching on hot execution paths.
 */
public interface DomainEventVisitor {
    default void visit(MarketTickEvent event) {}
    default void visit(CandleClosed event) {}
    default void visit(CandleDeveloping event) {}
    default void visit(TradeOpened event) {}
    default void visit(TradeClosed event) {}
    default void visit(TradeUpdated event) {}
    default void visit(OrderAccepted event) {}
    default void visit(OrderCancelled event) {}
    default void visit(OrderFilled event) {}
    default void visit(OrderFullyFilled event) {}
    default void visit(OrderModified event) {}
    default void visit(OrderPartiallyFilled event) {}
    default void visit(OrderRejected event) {}
    default void visit(SignalGenerated event) {}
    default void visit(SignalPendingExecution event) {}
    default void visit(SignalSuppressed event) {}
    default void visit(ReconciliationHaltRequired event) {}
    default void visit(KillSwitchEngaged event) {}
    default void visit(UnifiedKillSwitchEngaged event) {}
    default void visit(UnifiedKillSwitchDisengaged event) {}
    default void visit(UnrealizedPnLUpdated event) {}
    default void visit(PnlUpdatedEvent event) {}
    default void visit(PositionUpdateEvent event) {}
    default void visit(PositionMismatch event) {}
    default void visit(DepthUpdateEvent event) {}
    default void visit(OptionChainUpdated event) {}
    default void visit(GreeksComputed event) {}
    default void visit(GammaExposureComputed event) {}
    default void visit(MaxPainComputed event) {}
    default void visit(ScanHitProduced event) {}
    default void visit(ScanResultsPublished event) {}
    default void visit(BrokerAdapterError event) {}
    default void visit(StrategyError event) {}
    default void visit(StreamHealthChanged event) {}
    default void visit(ReplayTimeChangedEvent event) {}
    default void visit(TickReceived event) {}
    default void visit(EventBusBackpressure event) {}
    default void visit(TradeExecutionEvent event) {}
    default void visit(OrderUpdateEvent event) {}
}
