package com.tradej.core.domain.event;

/**
 * Visitor for trading and signal events: signals, trades, positions, PnL, and kill switches.
 */
public interface TradingVisitor {
    default void visit(SignalGenerated event) {}
    default void visit(SignalPendingExecution event) {}
    default void visit(SignalSuppressed event) {}
    default void visit(TradeOpened event) {}
    default void visit(TradeClosed event) {}
    default void visit(TradeUpdated event) {}
    default void visit(KillSwitchEngaged event) {}
    default void visit(UnifiedKillSwitchEngaged event) {}
    default void visit(UnifiedKillSwitchDisengaged event) {}
    default void visit(UnrealizedPnLUpdated event) {}
    default void visit(PnlUpdatedEvent event) {}
    default void visit(PositionUpdateEvent event) {}
    default void visit(PositionMismatch event) {}
    default void visit(TradeExecutionEvent event) {}
}
