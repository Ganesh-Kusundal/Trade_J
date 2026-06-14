package com.tradej.app.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.Map;

/**
 * Strategy metrics response. Supports two consumption modes:
 *
 * <ol>
 *   <li><b>Legacy flat shape</b> ({@code strategyName}, {@code totalTrades},
 *       {@code winRate}, ...) — used by {@code GET /api/v1/strategy/metrics}
 *       and the {@code StrategyVisualization} widget that depends on the
 *       field-level contract.</li>
 *   <li><b>Catalog counters shape</b> ({@code id}, {@code counters: Map<String,Long>})
 *       — used by {@code GET /api/v1/strategies/catalog} and the
 *       {@code StrategyCatalogPage} which renders a per-strategy table
 *       of OK/ERROR/TIMEOUT counts.</li>
 * </ol>
 *
 * <p>Constructors:
 * <ul>
 *   <li>{@link #StrategyMetricsResponse(String, Map)} — the catalog shape
 *       (flat id + nested counters).</li>
 *   <li>{@link #StrategyMetricsResponse(String, long, double, long, double, double)}
 *       — the legacy shape. Emits {@code strategyName} + flat numeric
 *       fields; the {@code counters} map is also populated so catalog
 *       consumers can still use the row.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StrategyMetricsResponse {

    @JsonProperty("strategyName")
    private String strategyName;

    @JsonProperty("id")
    private String id;

    private long totalTrades;
    private double winRate;
    private long realizedPnlPaisa;
    private double maxDrawdownPct;
    private double sharpe;

    @JsonUnwrapped
    private Map<String, Long> counters;

    public StrategyMetricsResponse(String id, Map<String, Long> counters) {
        this.id = id;
        this.strategyName = id;
        this.counters = counters;
    }

    public StrategyMetricsResponse(
            String strategyName,
            long totalTrades,
            double winRate,
            long realizedPnlPaisa,
            double maxDrawdownPct,
            double sharpe
    ) {
        this.strategyName = strategyName;
        this.id = strategyName;
        this.totalTrades = totalTrades;
        this.winRate = winRate;
        this.realizedPnlPaisa = realizedPnlPaisa;
        this.maxDrawdownPct = maxDrawdownPct;
        this.sharpe = sharpe;
        this.counters = Map.of(
                "totalTrades", totalTrades,
                "winRatePct", (long) Math.round(winRate * 100),
                "realizedPnlPaisa", realizedPnlPaisa,
                "maxDrawdownPct", (long) Math.round(maxDrawdownPct * 100),
                "sharpe", (long) Math.round(sharpe * 100)
        );
    }

    public String strategyName() { return strategyName; }
    public String id() { return id; }
    public long getTotalTrades() { return totalTrades; }
    public long totalTrades() { return totalTrades; }
    public double getWinRate() { return winRate; }
    public double winRate() { return winRate; }
    public long getRealizedPnlPaisa() { return realizedPnlPaisa; }
    public long realizedPnlPaisa() { return realizedPnlPaisa; }
    public double getMaxDrawdownPct() { return maxDrawdownPct; }
    public double maxDrawdownPct() { return maxDrawdownPct; }
    public double getSharpe() { return sharpe; }
    public double sharpe() { return sharpe; }
    public Map<String, Long> getCounters() { return counters; }
    public Map<String, Long> counters() { return counters; }
}
