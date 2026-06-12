package com.tradej.replay.engine;

import com.tradej.core.domain.port.Replayable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ReplayableRegistryTest {

    /** Simple test double that records what was captured/restored. */
    static final class FakeReplayable implements Replayable {
        private final String id;
        private final Object snapshotValue;
        private final AtomicReference<Object> restoredTo = new AtomicReference<>();

        FakeReplayable(String id, Object snapshotValue) {
            this.id = id;
            this.snapshotValue = snapshotValue;
        }

        @Override
        public Object snapshot() {
            return snapshotValue;
        }

        @Override
        public void restore(Object snapshot) {
            restoredTo.set(snapshot);
        }

        @Override
        public String replayableId() {
            return id;
        }

        Object restoredTo() {
            return restoredTo.get();
        }
    }

    @Test
    void snapshotAllCapturesStateFromAllRegisteredServices() {
        FakeReplayable a = new FakeReplayable("A", "snapA");
        FakeReplayable b = new FakeReplayable("B", "snapB");
        ReplayableRegistry registry = new ReplayableRegistry(List.of(a, b));

        Map<String, Object> snapshots = registry.snapshotAll();

        assertEquals(2, snapshots.size());
        assertEquals("snapA", snapshots.get("A"));
        assertEquals("snapB", snapshots.get("B"));
    }

    @Test
    void restoreAllCallsRestoreOnEach() {
        FakeReplayable a = new FakeReplayable("A", "snapA");
        FakeReplayable b = new FakeReplayable("B", "snapB");
        ReplayableRegistry registry = new ReplayableRegistry(List.of(a, b));

        Map<String, Object> snapshots = new LinkedHashMap<>();
        snapshots.put("A", "restoredA");
        snapshots.put("B", "restoredB");
        registry.restoreAll(snapshots);

        assertEquals("restoredA", a.restoredTo());
        assertEquals("restoredB", b.restoredTo());
    }

    @Test
    void restoreAllThrowsWhenReplayableIdMissing() {
        FakeReplayable a = new FakeReplayable("A", "snapA");
        ReplayableRegistry registry = new ReplayableRegistry(List.of(a));

        Map<String, Object> snapshots = new LinkedHashMap<>();
        snapshots.put("GHOST", "value");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> registry.restoreAll(snapshots));
        assertTrue(ex.getMessage().contains("GHOST"));
    }

    @Test
    void constructorThrowsOnDuplicateIds() {
        FakeReplayable first = new FakeReplayable("DUP", "v1");
        FakeReplayable second = new FakeReplayable("DUP", "v2");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new ReplayableRegistry(List.of(first, second)));
        assertTrue(ex.getMessage().contains("DUP"));
    }

    @Test
    void emptyRegistrySnapshotAndRestoreIsNoOp() {
        ReplayableRegistry registry = new ReplayableRegistry(new ArrayList<>());
        assertTrue(registry.snapshotAll().isEmpty());

        // restoreAll with empty input should not throw
        registry.restoreAll(new LinkedHashMap<>());
    }
}
