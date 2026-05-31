package com.tradej.app.config;

/**
 * Execution-layer services are now discovered via {@code @Service} component
 * scanning (Phase A.3). This configuration class is retained as a documentation
 * marker pointing to the original home of execution beans.
 *
 * <p>See the following {@code @Service}-annotated classes:
 * <ul>
 *   <li>{@link com.tradej.execution.service.TradingCircuitBreaker}</li>
 *   <li>{@link com.tradej.execution.service.OrderManagementService}</li>
 *   <li>{@link com.tradej.execution.service.ExecutionHandler}</li>
 *   <li>{@link com.tradej.execution.reconcile.OrderReconciler}</li>
 * </ul>
 *
 * <p>Originally extracted from {@link TradingRuntimeConfiguration} during
 * Phase A.2 (config monolith split).
 */
@Deprecated
public final class ExecutionConfiguration {
    private ExecutionConfiguration() {
    }
}
