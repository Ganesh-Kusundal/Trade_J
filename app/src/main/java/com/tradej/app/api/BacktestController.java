package com.tradej.app.api;

import com.tradej.replay.engine.BacktestExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/backtest")
public class BacktestController {

    private final BacktestExecutionService backtestService;

    public BacktestController(BacktestExecutionService backtestService) {
        this.backtestService = backtestService;
    }

    /** Runs backtest sequence with an empty event list (graph wiring smoke). Supply events via CLI/replay for full runs. */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run(@RequestParam String graphId) {
        return ResponseEntity.ok(backtestService.runBacktest(graphId, List.of()));
    }
}
