package com.tradej.research.api;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.model.Candle;
import com.tradej.replay.engine.ReplayController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST API to control historical candle replays (play, pause, step, speed) from the React UI.
 */
@RestController
@RequestMapping("/api/research/replay")
public class ReplayApiController {
    private static final Logger log = LoggerFactory.getLogger(ReplayApiController.class);

    private final ReplayController replayController;
    private final DuckDbAnalyticsEngine analyticsEngine;

    public ReplayApiController(ReplayController replayController, DuckDbAnalyticsEngine analyticsEngine) {
        this.replayController = replayController;
        this.analyticsEngine = analyticsEngine;
    }

    @PostMapping("/start")
    public ResponseEntity<ReplayStatusResponse> start(
        @RequestParam String symbol,
        @RequestParam String exchange,
        @RequestParam long fromMs,
        @RequestParam long toMs
    ) {
        try {
            log.info("Request to initialize replay: symbol={} range={} to {}", symbol, fromMs, toMs);
            
            // 1. Fetch candles from analytics DuckDB
            List<Map<String, Object>> rows = analyticsEngine.queryEquityCandles(symbol, fromMs, toMs, 20000);
            List<Candle> candles = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                candles.add(new Candle(
                    (String) row.get("symbol"),
                    (String) row.get("interval"),
                    (Long) row.get("barTimeMs"),
                    (Long) row.get("barTimeMs") + 59999, // 1m closing limit
                    (Long) row.get("openPaisa"),
                    (Long) row.get("highPaisa"),
                    (Long) row.get("lowPaisa"),
                    (Long) row.get("closePaisa"),
                    (Long) row.get("volume"),
                    true
                ));
            }

            if (candles.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // 2. Start replay controller session
            replayController.start(candles);
            
            return ResponseEntity.ok(getStatusResponse());
        } catch (SQLException e) {
            log.error("Failed to start replay due to DuckDB error", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/play")
    public ResponseEntity<ReplayStatusResponse> play() {
        replayController.play();
        return ResponseEntity.ok(getStatusResponse());
    }

    @PostMapping("/pause")
    public ResponseEntity<ReplayStatusResponse> pause() {
        replayController.pause();
        return ResponseEntity.ok(getStatusResponse());
    }

    @PostMapping("/step")
    public ResponseEntity<ReplayStatusResponse> step() {
        replayController.step();
        return ResponseEntity.ok(getStatusResponse());
    }

    @PostMapping("/speed")
    public ResponseEntity<ReplayStatusResponse> setSpeed(@RequestParam double multiplier) {
        replayController.setSpeed(multiplier);
        return ResponseEntity.ok(getStatusResponse());
    }

    @PostMapping("/stop")
    public ResponseEntity<ReplayStatusResponse> stop() {
        replayController.stop();
        return ResponseEntity.ok(getStatusResponse());
    }

    @GetMapping("/status")
    public ResponseEntity<ReplayStatusResponse> status() {
        return ResponseEntity.ok(getStatusResponse());
    }

    private ReplayStatusResponse getStatusResponse() {
        return new ReplayStatusResponse(
            replayController.getState().name(),
            replayController.getCurrentIndex(),
            replayController.getTotalCandles(),
            replayController.getSpeedMultiplier(),
            replayController.getClock() != null ? replayController.getClock().millis() : 0L
        );
    }

    public record ReplayStatusResponse(
        String state,
        int currentIndex,
        int totalCandles,
        double speedMultiplier,
        long currentTimeMs
    ) {}
}
