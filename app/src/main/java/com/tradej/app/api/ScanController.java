package com.tradej.app.api;

import com.tradej.app.scanner.ScanService;
import com.tradej.core.domain.scan.ScanHit;
import com.tradej.core.domain.scan.ScanResult;
import com.tradej.core.domain.scan.ScanRun;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/scans")
@ConditionalOnBean(ScanService.class)
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    @GetMapping(value = "/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> latest(
            @RequestParam String profile
    ) {
        return scanService.latest(profile)
                .map(result -> ResponseEntity.ok(toBody(result)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{runId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> byRunId(@PathVariable String runId) {
        return scanService.byRunId(runId)
                .map(result -> ResponseEntity.ok(toBody(result)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> listRuns(
            @RequestParam String profile,
            @RequestParam(defaultValue = "10") int limit
    ) {
        List<ScanRun> runs = scanService.listRuns(profile, Math.min(limit, 100));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("profileId", profile);
        body.put("runs", runs.stream().map(this::runSummary).toList());
        return ResponseEntity.ok(body);
    }

    @PostMapping(value = "/run", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> runScan(
            @RequestParam(required = false) String profile
    ) {
        ScanResult result = profile == null || profile.isBlank()
                ? scanService.runDefaultProfile()
                : scanService.runProfile(profile);
        return ResponseEntity.ok(toBody(result));
    }

    private Map<String, Object> toBody(ScanResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("run", runSummary(result.run()));
        body.put("hits", result.hits().stream().map(this::hitBody).collect(Collectors.toList()));
        return body;
    }

    private Map<String, Object> runSummary(ScanRun run) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("runId", run.runId());
        map.put("profileId", run.profileId());
        map.put("startedAtMs", run.startedAtMs());
        map.put("finishedAtMs", run.finishedAtMs());
        map.put("status", run.status().name());
        map.put("universeSize", run.universeSize());
        map.put("hitCount", run.hitCount());
        map.put("partialFailureCount", run.partialFailureCount());
        map.put("errorMessage", run.errorMessage());
        return map;
    }

    private Map<String, Object> hitBody(ScanHit hit) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("symbol", hit.symbol());
        map.put("exchangeSegment", hit.exchangeSegment().name());
        map.put("assetClass", hit.assetClass().name());
        map.put("underlying", hit.underlying());
        map.put("score", hit.score());
        map.put("reasons", hit.reasons());
        map.put("snapshot", hit.snapshotFields());
        map.put("promoted", hit.promoted());
        return map;
    }
}
