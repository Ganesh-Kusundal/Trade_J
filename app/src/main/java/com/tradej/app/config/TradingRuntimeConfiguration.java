package com.tradej.app.config;

/**
 * Legacy configuration class — all beans have been extracted to domain-specific
 * {@link Configuration} classes as part of Phase A.2 (config monolith split).
 *
 * <p>See the following classes for individual bean definitions:
 * <ul>
 *   <li>{@link BrokerConfiguration} — rate limiter, idempotency cache, broker connection, capabilities</li>
 *   <li>{@link RiskConfiguration} — risk limits, position risk handler</li>
 *   <li>{@link StrategyConfiguration} — candle aggregation service, strategy engine</li>
 *   <li>{@link ExecutionConfiguration} — circuit breaker, OMS, execution handler, order reconciler</li>
 *   <li>{@link EventBusConfiguration} — Disruptor-based event bus</li>
 *   <li>{@link PersistenceConfiguration} — Chronicle audit log, DuckDB event store</li>
 *   <li>{@link StartupConfiguration} — health state, startup runner, preflight orchestration</li>
 * </ul>
 *
 * @deprecated All beans have been extracted to the individual configuration classes listed above.
 *             This class is kept only as documentation pointing to the new home of each bean.
 *             Remove this class once all external references are updated.
 */
@Deprecated
public final class TradingRuntimeConfiguration {
    private TradingRuntimeConfiguration() {
    }
}
