package com.tradej.app.scanner;

import com.tradej.core.domain.event.ScanResultsPublished;
import com.tradej.scanner.model.ScanRun;
import com.tradej.scanner.model.ScanHit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public final class ScanResultStore {

    public record ScanResultEntry(
            String runId,
            String profileId,
            long timestampMs,
            int hitCount,
            List<ScanResultsPublished.ScanHitSummary> hits
    ) {}

    private final Map<String, List<ScanResultEntry>> resultsByProfile = new ConcurrentHashMap<>();
    private final Map<String, ScanResultEntry> latestByProfile = new ConcurrentHashMap<>();

    public void onScanResults(ScanResultsPublished event) {
        var entry = new ScanResultEntry(
                event.runId(),
                event.profileId(),
                event.metadata().timestampMs(),
                event.hitCount(),
                event.hits()
        );
        resultsByProfile
                .computeIfAbsent(event.profileId(), k -> new CopyOnWriteArrayList<>())
                .add(0, entry);
        latestByProfile.put(event.profileId(), entry);
    }

    public void onScanRunCompleted(ScanRun run) {
        // Store completed scan run summary if no hits were published via event
        latestByProfile.computeIfAbsent(run.profileId(), k -> new ScanResultEntry(
                run.runId(),
                run.profileId(),
                run.finishedAtMs(),
                run.hitCount(),
                List.of()
        ));
    }

    public List<ScanResultEntry> getResults(String profileId, int limit) {
        return resultsByProfile
                .getOrDefault(profileId, List.of())
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    public ScanResultEntry getLatest(String profileId) {
        return latestByProfile.get(profileId);
    }

    public List<String> getProfilesWithResults() {
        return List.copyOf(resultsByProfile.keySet());
    }
}