package com.tradej.app.api;

import com.tradej.strategy.certification.StrategyReplayParityReporter;
import com.tradej.strategy.certification.StrategyReplayParityReporter.ParityReport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Per-strategy replay-parity certification endpoint. Powers the
 * {@code tradej strategies parity <plugin-id> …} CLI sub-command
 * and the per-strategy parity tab in the dashboard.
 */
@RestController
@RequestMapping("/api/v1/strategies/parity")
public class StrategyParityController {

    private final ObjectProvider<StrategyReplayParityReporter> reporter;

    public StrategyParityController(ObjectProvider<StrategyReplayParityReporter> reporter) {
        this.reporter = reporter;
    }

    @GetMapping("/{pluginId}")
    public ResponseEntity<Map<String, Object>> parity(
            @PathVariable String pluginId,
            @RequestParam String symbol,
            @RequestParam long fromMs,
            @RequestParam long toMs
    ) {
        StrategyReplayParityReporter r = reporter.getIfAvailable();
        if (r == null) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "StrategyReplayParityReporter not available on this profile"
            ));
        }
        ParityReport report = r.report(pluginId, symbol, fromMs, toMs);
        return ResponseEntity.ok(Map.of(
                "reportId", report.reportId(),
                "plugin", report.plugin(),
                "symbol", report.symbol(),
                "from", report.from().toString(),
                "to", report.to().toString(),
                "signals", report.signals(),
                "trades", report.trades(),
                "maxDrawdownPaisa", report.maxDrawdownPaisa()
        ));
    }
}
