package com.tradej.analytics;

/**
 * Analytics engine package.
 *
 * <p>PHASE 5 of the Trade-J platform transformation.
 * Provides interfaces for:
 * <ul>
 *     <li>{@code PerformanceAnalytics} — Sharpe, Sortino, Calmar, Win rate</li>
 *     <li>{@code DrawdownAnalytics} — Max drawdown, underwater equity curve</li>
 *     <li>{@code EquityCurveAnalytics} — Returns series, rolling metrics</li>
 * </ul>
 *
 * <p>Implementation classes are intentionally absent; add concrete engines
 * once the DuckDB-backed metrics store is available.
 */
class PackageInfo {
    private PackageInfo() {}
}
