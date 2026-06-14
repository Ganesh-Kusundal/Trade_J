package com.tradej.replay.engine;

import com.tradej.core.domain.port.Replayable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the set of stateful services that the replay orchestrator must
 * snapshot and restore.
 * <p>
 * Constructed once at composition time with the full list of {@link Replayable}
 * services. {@link ReplayOrchestrator#withReplayMode} uses this to call
 * {@link Replayable#snapshot()} / {@link Replayable#restore(Object)} on each
 * registered service.
 */
public final class ReplayableRegistry {
    private final Map<String, Replayable> services = new LinkedHashMap<>();

    public ReplayableRegistry(Collection<Replayable> initial) {
        for (Replayable r : initial) {
            Replayable prev = this.services.put(r.replayableId(), r);
            if (prev != null) {
                throw new IllegalStateException(
                    "Duplicate Replayable id: " + r.replayableId());
            }
        }
    }

    public Map<String, Object> snapshotAll() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Replayable> e : services.entrySet()) {
            out.put(e.getKey(), e.getValue().snapshot());
        }
        return out;
    }

    public void restoreAll(Map<String, Object> snapshots) {
        for (Map.Entry<String, Object> e : snapshots.entrySet()) {
            Replayable r = services.get(e.getKey());
            if (r == null) {
                throw new IllegalStateException(
                    "No Replayable registered with id " + e.getKey() +
                    "; cannot restore state captured from an earlier run");
            }
            r.restore(e.getValue());
        }
    }
}
