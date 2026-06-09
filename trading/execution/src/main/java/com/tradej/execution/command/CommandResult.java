package com.tradej.execution.command;

import com.tradej.core.domain.model.Order;

import java.util.List;

/**
 * Result of executing a {@link TradingCommand}.
 *
 * <p>Sealed hierarchy ensures callers handle all outcomes explicitly:
 * <ul>
 *   <li>{@link Success} — command executed, order returned</li>
 *   <li>{@link Rejected} — command rejected by risk/validation (not an error)</li>
 *   <li>{@link Error} — unexpected failure (broker down, network timeout)</li>
 *   <li>{@link CatalogRefreshed} — instrument catalog refreshed</li>
 *   <li>{@link ImportStarted} — historical data import job started</li>
 *   <li>{@link ReconciliationResult} — position reconciliation completed</li>
 * </ul>
 */
public sealed interface CommandResult {

    record Success(Order order) implements CommandResult {}

    record Rejected(String reason) implements CommandResult {}

    record Error(String message, Throwable cause) implements CommandResult {
        public Error(String message) {
            this(message, null);
        }
    }

    record BulkSuccess(List<String> orderIds) implements CommandResult {}

    record KillSwitchResult(boolean enabled) implements CommandResult {}

    record CatalogRefreshed(int instrumentCount) implements CommandResult {}

    record ImportStarted(String jobId) implements CommandResult {}

    record ReconciliationResult(int mismatches) implements CommandResult {}

    default boolean isSuccess() {
        return this instanceof Success
                || this instanceof BulkSuccess
                || this instanceof KillSwitchResult
                || this instanceof CatalogRefreshed
                || this instanceof ImportStarted
                || this instanceof ReconciliationResult;
    }
}
