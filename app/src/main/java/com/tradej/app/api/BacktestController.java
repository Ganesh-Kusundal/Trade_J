package com.tradej.app.api;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.replay.engine.BacktestExecutionService;
import com.tradej.replay.engine.BacktestExecutionService.BacktestRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/backtest")
public class BacktestController {

    private final BacktestExecutionService backtestService;

    public BacktestController(@Lazy BacktestExecutionService backtestService) {
        this.backtestService = backtestService;
    }

    /** Legacy minimal API: just graphId. */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run(@RequestParam String graphId) {
        if (backtestService == null) return ResponseEntity.ok(Map.of("error", "Not configured"));
        return ResponseEntity.ok(backtestService.runBacktest(graphId, List.of()));
    }

    /**
     * Full P2 #18 backtest API. Body:
     * <pre>
     * {
     *   "graphId": "...",
     *   "symbol": "RELIANCE",
     *   "exchangeSegment": "NSE_EQ",
     *   "from": "2025-01-01",
     *   "to":   "2025-06-30",
     *   "interval": "1d",
     *   "feeBps": 10.0,
     *   "slippageBps": 5.0,
     *   "seed": 42
     * }
     * </pre>
     * Response: full backtest result with trades, pnlSeries, metrics,
     * and a reproducibilityHash.
     */
    @PostMapping("/v2/run")
    public ResponseEntity<BacktestExecutionService.BacktestResult> runV2(@RequestBody BacktestRequestBody body) {
        if (backtestService == null) return ResponseEntity.ok(emptyResult(body));
        if (body.graphId == null || body.graphId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        ExchangeSegment segment = body.exchangeSegment != null
                ? ExchangeSegment.valueOf(body.exchangeSegment)
                : ExchangeSegment.NSE_EQ;
        LocalDate from = body.from != null ? body.from : LocalDate.now().minusDays(30);
        LocalDate to = body.to != null ? body.to : LocalDate.now();
        String interval = body.interval != null ? body.interval : "1d";
        double feeBps = body.feeBps != null ? body.feeBps : 10.0;
        double slippageBps = body.slippageBps != null ? body.slippageBps : 5.0;
        long seed = body.seed != null ? body.seed : 42L;
        String symbol = body.symbol != null ? body.symbol : "RELIANCE";

        BacktestRequest req = new BacktestRequest(
                body.graphId, symbol, segment, from, to,
                interval, feeBps, slippageBps, seed,
                List.of(), body.fills
        );
        return ResponseEntity.ok(backtestService.runBacktest(req));
    }

    private BacktestExecutionService.BacktestResult emptyResult(BacktestRequestBody body) {
        return new BacktestExecutionService.BacktestResult(
                body.graphId == null ? "" : body.graphId,
                body.symbol == null ? "RELIANCE" : body.symbol,
                body.from == null ? LocalDate.now() : body.from,
                body.to == null ? LocalDate.now() : body.to,
                0, "not-configured", Map.of("error", "Backtest service not configured")
        );
    }

    public static class BacktestRequestBody {
        public String graphId;
        public String symbol;
        public String exchangeSegment;
        public LocalDate from;
        public LocalDate to;
        public String interval;
        public Double feeBps;
        public Double slippageBps;
        public Long seed;
        public List<com.tradej.core.domain.event.TradeExecutionEvent> fills;
    }
}
