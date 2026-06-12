package com.tradej.app.api;

import com.tradej.app.api.dto.StrategyMetricsResponse;
import com.tradej.strategy.observability.StrategyMetrics;
import com.tradej.strategy.observability.StrategyMetricsRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy catalog endpoints. Powers the {@code StrategyCatalogPage}
 * in the frontend.
 *
 * <p>Distinct from {@link StrategyController} which reads from the
 * persisted {@code trade_lifecycle} table. This controller returns
 * the in-memory {@link StrategyMetricsRegistry} snapshot — i.e. the
 * counters the {@code GraphStrategySandbox} has accumulated since
 * the JVM started.
 */
@RestController
@RequestMapping("/api/v1/strategies")
public class StrategyCatalogController {

    private final ObjectProvider<StrategyMetricsRegistry> registry;

    public StrategyCatalogController(ObjectProvider<StrategyMetricsRegistry> registry) {
        this.registry = registry;
    }

    /**
     * Returns one row per strategy, with its per-event-type outcome
     * counters grouped under {@code counters}. The set of strategy
     * ids is derived from the registry keys: a strategy id is the
     * substring of a counter key up to the first {@code |}.
     */
    @GetMapping("/catalog")
    public ResponseEntity<List<StrategyMetricsResponse>> catalog() {
        StrategyMetricsRegistry r = registry.getIfAvailable();
        if (r == null) {
            return ResponseEntity.ok(List.of());
        }
        Map<String, Long> snap = r.snapshot();
        Map<String, Map<String, Long>> grouped = new LinkedHashMap<>();
        for (var e : snap.entrySet()) {
            String key = e.getKey();
            int pipe = key.indexOf('|');
            if (pipe < 0) continue;
            String strategy = key.substring(0, pipe);
            String rest = key.substring(pipe + 1);
            grouped.computeIfAbsent(strategy, k -> new LinkedHashMap<>()).put(rest, e.getValue());
        }
        List<StrategyMetricsResponse> out = new ArrayList<>(grouped.size());
        for (var e : grouped.entrySet()) {
            out.add(new StrategyMetricsResponse(e.getKey(), e.getValue()));
        }
        return ResponseEntity.ok(out);
    }
}
