package com.tradej.execution.command;

import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.OrderRequest;

/**
 * Immutable trading commands representing intent to mutate system state.
 *
 * <p>Commands decouple the caller (CLI, REST, Event Bus) from the implementation.
 * Each command carries all data needed to execute the operation — no ambient context required.
 *
 * <p>Usage:
 * <pre>
 *   CommandResult result = commandHandler.execute(
 *       new TradingCommand.PlaceOrder(orderRequest));
 * </pre>
 */
public sealed interface TradingCommand {

    record PlaceOrder(OrderRequest request) implements TradingCommand {}

    record CancelOrder(String orderId) implements TradingCommand {}

    record ModifyOrder(ModifyOrderRequest request) implements TradingCommand {}

    record CancelAllOpenOrders() implements TradingCommand {}

    record SetKillSwitch(boolean enabled) implements TradingCommand {}

    record RefreshInstrumentCatalog(boolean force) implements TradingCommand {}

    record ImportHistoricalData(
            String symbol,
            String segment,
            String interval,
            long fromMs,
            long toMs
    ) implements TradingCommand {}

    record ReconcilePositions(String jsonPayload) implements TradingCommand {}
}
