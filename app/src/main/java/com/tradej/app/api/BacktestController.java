package com.tradej.app.api;

import com.tradej.replay.engine.BacktestExecutionService;
import org.springframework.context.annotation.Lazy;
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

    public BacktestController(@Lazy BacktestExecutionService backtestService) {
        this.backtestService = backtestService;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run(@RequestParam String graphId) {
        if (backtestService == null) return ResponseEntity.ok(Map.of("error", "Not configured"));
        return ResponseEntity.ok(backtestService.runBacktest(graphId, List.of()));
    }
}
