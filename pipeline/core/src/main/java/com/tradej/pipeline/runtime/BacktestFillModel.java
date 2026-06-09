package com.tradej.pipeline.runtime;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.TradeExecutionEvent;
import com.tradej.core.domain.model.OrderRequest;

import java.util.List;

/**
 * Pluggable fill model for BACKTEST and REPLAY modes.
 * <p>
 * When a {@code SignalPendingExecution} flows through the graph in backtest mode,
 * the fill model is consulted to produce simulated {@link TradeExecutionEvent fills}
 * instead of routing through a live broker.
 */
@FunctionalInterface
public interface BacktestFillModel {

    /**
     * Produce simulated fills for an order request triggered by the given event.
     *
     * @param request the order to fill
     * @param trigger the event that triggered the order (e.g. CandleClosed, SignalGenerated)
     * @return list of simulated fills (typically 1 for market orders, possibly partial for limit)
     */
    List<TradeExecutionEvent> fillOrder(OrderRequest request, DomainEvent trigger);
}
