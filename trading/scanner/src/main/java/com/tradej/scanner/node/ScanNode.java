package com.tradej.scanner.node;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.pipeline.runtime.BasePipelineNode;
import com.tradej.scanner.engine.ScanEngine;
import com.tradej.scanner.model.ScanProfile;
import com.tradej.core.domain.scan.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pipeline node that triggers batch scans on configured candle-close intervals.
 * Scan results are persisted by existing scan services; this node only orchestrates execution.
 */
public final class ScanNode extends BasePipelineNode {

    private static final Logger log = LoggerFactory.getLogger(ScanNode.class);
    private static final String CFG_PROFILE_ID = "profileId";
    private static final String CFG_TRIGGER_INTERVAL = "triggerInterval";
    private static final String CFG_MIN_INTERVAL_MS = "minIntervalMs";

    private final ScanEngine scanEngine;
    private final Map<String, ScanProfile> profilesById;
    private final AtomicLong lastRunMs = new AtomicLong();

    public ScanNode(ScanEngine scanEngine, Map<String, ScanProfile> profilesById) {
        this.scanEngine = scanEngine;
        this.profilesById = new ConcurrentHashMap<>(profilesById);
    }

    @Override
    protected void onInit() {
        // no-op
    }

    @Override
    protected void processEvent(DomainEvent event) {
        if (!(event instanceof CandleClosed closed)) {
            return;
        }
        String profileId = stringConfig(CFG_PROFILE_ID, "");
        if (profileId.isBlank()) {
            return;
        }
        String triggerInterval = stringConfig(CFG_TRIGGER_INTERVAL, "5m");
        if (!triggerInterval.equalsIgnoreCase(closed.candle().interval())) {
            return;
        }
        long minIntervalMs = longConfig(CFG_MIN_INTERVAL_MS, 60_000L);
        long now = context.getClockTimeMs();
        long previous = lastRunMs.get();
        if (now - previous < minIntervalMs) {
            return;
        }
        if (!lastRunMs.compareAndSet(previous, now)) {
            return;
        }

        ScanProfile profile = profilesById.get(profileId);
        if (profile == null) {
            log.warn("Scan node profileId={} not registered — skipping scan", profileId);
            return;
        }
        ScanResult result = scanEngine.run(profile);
        log.info("Scan node completed profile={} hits={}", profileId, result.hits().size());
    }

    private String stringConfig(String key, String defaultValue) {
        if (definition == null || definition.config() == null) {
            return defaultValue;
        }
        Object value = definition.config().get(key);
        return value instanceof String s && !s.isBlank() ? s : defaultValue;
    }

    private long longConfig(String key, long defaultValue) {
        if (definition == null || definition.config() == null) {
            return defaultValue;
        }
        Object value = definition.config().get(key);
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        return defaultValue;
    }
}
