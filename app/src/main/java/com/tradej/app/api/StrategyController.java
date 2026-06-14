package com.tradej.app.api;

import com.tradej.app.api.dto.StrategyMetricsResponse;
import com.tradej.app.api.dto.StrategySignalResponse;
import com.tradej.persistence.duckdb.DuckDbEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * REST surface for the strategy visualizer panel.
 *
 * <p>Two endpoints are exposed under {@code /api/v1/strategy}:
 * <ul>
 *   <li>{@code GET /signals} — historical list of signal rows, projected
 *       from the persisted {@code trade_lifecycle} table.
 *   <li>{@code GET /metrics} — aggregated performance metrics grouped by
 *       strategy name.
 * </ul>
 *
 * <p>The original empty state in
 * {@code trade_j_frontend/.../StrategyVisualization.tsx} referenced
 * {@code POST /api/v1/strategy/signals}; the implementation here uses
 * {@code GET} because both endpoints are read-only queries. The frontend
 * placeholder text needs to be updated in a follow-up.
 */
@RestController
@RequestMapping("/api/v1/strategy")
public class StrategyController {

    private static final Logger log = LoggerFactory.getLogger(StrategyController.class);

    private final DuckDbEventStore eventStore;

    public StrategyController(DuckDbEventStore eventStore) {
        this.eventStore = eventStore;
    }

    /**
     * Returns the signal lifecycle entries persisted for the given window.
     *
     * @param symbol   optional symbol filter (case-insensitive, blank ⇒ all)
     * @param from     inclusive lower bound in epoch milliseconds
     * @param to       inclusive upper bound in epoch milliseconds
     * @param strategy optional strategy-name filter; the persisted
     *                 {@code trade_lifecycle} table does not currently
     *                 carry a strategy name, so this is reserved for
     *                 future use and is accepted without filtering today
     */
    @GetMapping("/signals")
    public ResponseEntity<List<StrategySignalResponse>> signals(
            @RequestParam(name = "symbol", required = false) String symbol,
            @RequestParam(name = "from") long from,
            @RequestParam(name = "to") long to,
            @RequestParam(name = "strategy", required = false) String strategy
    ) {
        validateWindow(from, to);

        List<DuckDbEventStore.TradeLifecycleRow> rows;
        try {
            rows = eventStore.queryTradeLifecycle(from, to, normalize(symbol));
        } catch (RuntimeException ex) {
            log.warn("Signal query failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        List<StrategySignalResponse> response = new ArrayList<>(rows.size());
        for (DuckDbEventStore.TradeLifecycleRow row : rows) {
            response.add(toResponse(row));
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Returns per-strategy performance metrics over the window.
     * Today, the only key we have on the persisted table is the trade
     * {@code side}, so the result set is grouped by side. The shape
     * matches the contract the {@code StrategyVisualization} panel
     * expects.
     */
    @GetMapping("/metrics")
    public ResponseEntity<List<StrategyMetricsResponse>> metrics(
            @RequestParam(name = "symbol", required = false) String symbol,
            @RequestParam(name = "from") long from,
            @RequestParam(name = "to") long to
    ) {
        validateWindow(from, to);

        List<DuckDbEventStore.TradeLifecycleRow> rows;
        try {
            rows = eventStore.queryTradeLifecycle(from, to, normalize(symbol));
        } catch (RuntimeException ex) {
            log.warn("Strategy metrics query failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        return ResponseEntity.ok(aggregateMetrics(rows));
    }

    // ── Private helpers ──

    private static void validateWindow(long from, long to) {
        if (from <= 0L || to <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "from and to must be positive epoch-ms values");
        }
        if (to < from) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "to must be greater than or equal to from");
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private static StrategySignalResponse toResponse(DuckDbEventStore.TradeLifecycleRow row) {
        String type = classify(row);
        long price = "TRADE_OPENED".equals(row.eventType())
                ? row.entryPricePaisa()
                : row.exitPricePaisa();
        Long pnl = "TRADE_CLOSED".equals(row.eventType())
                ? row.realizedPnlPaisa()
                : null;
        return new StrategySignalResponse(
                row.eventTimeMs(),
                type,
                price,
                row.size(),
                "", // trade_lifecycle has no strategy_name column today
                pnl,
                row.symbol(),
                row.symbol() == null ? "" : ""
        );
    }

    /**
     * Map a {@code trade_lifecycle} row to one of the four signal
     * directions the visualizer cares about.
     */
    private static String classify(DuckDbEventStore.TradeLifecycleRow row) {
        if (row == null || row.eventType() == null) {
            return null;
        }
        boolean opening = "TRADE_OPENED".equals(row.eventType());
        boolean isBuy = "BUY".equalsIgnoreCase(row.side());
        if (opening && isBuy) return "ENTRY_LONG";
        if (opening) return "ENTRY_SHORT";
        if (isBuy) return "EXIT_SHORT";
        return "EXIT_LONG";
    }

    private static List<StrategyMetricsResponse> aggregateMetrics(
            List<DuckDbEventStore.TradeLifecycleRow> rows
    ) {
        // Group closed trades by side (proxy for strategy until a
        // strategyName column is added to trade_lifecycle).
        Map<String, List<Long>> pnlByGroup = new HashMap<>();
        Map<String, Long> tradeCountByGroup = new HashMap<>();
        Map<String, Long> winsByGroup = new HashMap<>();
        for (DuckDbEventStore.TradeLifecycleRow row : rows) {
            if (!"TRADE_CLOSED".equals(row.eventType())) {
                continue;
            }
            String key = row.side() == null || row.side().isBlank() ? "UNKNOWN" : row.side();
            pnlByGroup.computeIfAbsent(key, k -> new ArrayList<>()).add(row.realizedPnlPaisa());
            tradeCountByGroup.merge(key, 1L, Long::sum);
            if (row.realizedPnlPaisa() > 0L) {
                winsByGroup.merge(key, 1L, Long::sum);
            }
        }

        List<StrategyMetricsResponse> out = new ArrayList<>();
        for (Map.Entry<String, List<Long>> e : pnlByGroup.entrySet()) {
            String key = e.getKey();
            List<Long> pnls = e.getValue();
            long total = pnls.stream().mapToLong(Long::longValue).sum();
            long totalTrades = tradeCountByGroup.getOrDefault(key, 0L);
            long wins = winsByGroup.getOrDefault(key, 0L);
            double winRate = totalTrades == 0L ? 0.0 : ((double) wins) / (double) totalTrades;
            double maxDrawdown = computeMaxDrawdown(pnls);
            double sharpe = computeSharpe(pnls);
            out.add(new StrategyMetricsResponse(
                    key,
                    totalTrades,
                    round4(winRate),
                    total,
                    round4(maxDrawdown),
                    round4(sharpe)
            ));
        }
        return out;
    }

    private static double computeMaxDrawdown(List<Long> pnls) {
        long peak = 0L;
        long trough = 0L;
        long cum = 0L;
        for (long pnl : pnls) {
            cum += pnl;
            if (cum > peak) {
                peak = cum;
                trough = cum;
            } else if (cum < trough) {
                trough = cum;
            }
        }
        return (peak - trough) / 100.0; // convert paisa → INR
    }

    private static double computeSharpe(List<Long> pnls) {
        if (pnls.size() < 2) {
            return 0.0;
        }
        double mean = pnls.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double variance = pnls.stream()
                .mapToDouble(p -> Math.pow(p - mean, 2.0))
                .sum() / (double) (pnls.size() - 1);
        double stddev = Math.sqrt(variance);
        if (stddev == 0.0) {
            return 0.0;
        }
        return (mean / stddev) * Math.sqrt(252.0);
    }

    private static double round4(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
