package com.tradej.persistence.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.event.BrokerAdapterError;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.testing.CollectingEventBus;
import com.tradej.persistence.replay.ReplayRunner.ReplayStateManager;
import net.openhft.chronicle.queue.ChronicleQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ReplayRunnerTest {

    private Path tempDir;
    private ObjectMapper mapper;

    @Mock
    private EventBus eventBus;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("replay-test-");
        mapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        try (var paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }

    /**
     * Helper: write raw text entries to a Chronicle Queue at {@link #tempDir}.
     * Each call opens a fresh queue, writes entries, and closes — this ensures
     * the data is visible to subsequent {@link ReplayRunner} instances.
     */
    private void writeEntries(String... entries) {
        try (ChronicleQueue queue = ChronicleQueue.singleBuilder(tempDir.toFile()).build()) {
            for (String entry : entries) {
                queue.createAppender().writeText(entry);
            }
        }
    }

    @Test
    void returnsEmptyResultForEmptyQueue() {
        try (ReplayRunner runner = new ReplayRunner(tempDir, eventBus)) {
            ReplayResult result = runner.replayAll(BrokerAdapterError.class);
            assertTrue(result.isEmpty());
            assertEquals(0L, result.totalRead());
            assertEquals(0L, result.replayed());
            assertEquals(0L, result.failed());
        }
    }

    @Test
    void replaysAllValidEvents() throws Exception {
        BrokerAdapterError event1 = new BrokerAdapterError(EventMetadata.root(), "dhan", "feed", "msg1");
        BrokerAdapterError event2 = new BrokerAdapterError(EventMetadata.root(), "dhan", "feed", "msg2");
        writeEntries(mapper.writeValueAsString(event1), mapper.writeValueAsString(event2));

        try (ReplayRunner runner = new ReplayRunner(tempDir, eventBus)) {
            ReplayResult result = runner.replayAll(BrokerAdapterError.class);
            assertFalse(result.isEmpty());
            assertTrue(result.isComplete());
            assertFalse(result.hasIssues());
            assertEquals(2L, result.totalRead());
            assertEquals(2L, result.replayed());
            assertEquals(0L, result.failed());
        }
    }

    @Test
    void countsFailedEntries() throws Exception {
        BrokerAdapterError valid = new BrokerAdapterError(EventMetadata.root(), "dhan", "feed", "valid");
        writeEntries(mapper.writeValueAsString(valid), "this is not valid json", "{also not: valid}");

        try (ReplayRunner runner = new ReplayRunner(tempDir, eventBus)) {
            ReplayResult result = runner.replayAll(BrokerAdapterError.class);
            assertEquals(3L, result.totalRead());
            assertEquals(1L, result.replayed());
            assertEquals(2L, result.failed());
            assertTrue(result.hasIssues());
        }
    }

    @Test
    void allEntriesCorrupted() {
        writeEntries("garbage", "more garbage with no structure");

        try (ReplayRunner runner = new ReplayRunner(tempDir, eventBus)) {
            ReplayResult result = runner.replayAll(BrokerAdapterError.class);
            assertEquals(2L, result.totalRead());
            assertEquals(0L, result.replayed());
            assertEquals(2L, result.failed());
        }
    }

    @Test
    void failsOnWrongEventType() {
        // A JSON string primitive cannot deserialize as BrokerAdapterError,
        // reliably counting as failed regardless of Jackson configuration.
        writeEntries("\"just a string, not a BrokerAdapterError\"");

        try (ReplayRunner runner = new ReplayRunner(tempDir, eventBus)) {
            ReplayResult result = runner.replayAll(BrokerAdapterError.class);
            assertEquals(1L, result.totalRead());
            assertEquals(0L, result.replayed());
            assertEquals(1L, result.failed());
        }
    }

    @Test
    void closeIsIdempotent() {
        ReplayRunner runner = new ReplayRunner(tempDir, eventBus);
        runner.close();
        runner.close(); // Should not throw
    }

    // ── AD-02: ReplayStateManager hook tests ──

    @Test
    void beforeReplayIsCalledBeforeEventsArePublished() throws Exception {
        BrokerAdapterError event = new BrokerAdapterError(EventMetadata.root(), "dhan", "feed", "test-event");
        writeEntries(mapper.writeValueAsString(event));

        AtomicInteger beforeCount = new AtomicInteger();
        CollectingEventBus publishingBus = new CollectingEventBus();

        ReplayStateManager stateManager = new ReplayStateManager() {
            @Override
            public void beforeReplay() {
                beforeCount.incrementAndGet();
            }
            @Override
            public void afterReplay() {}
        };

        try (ReplayRunner runner = new ReplayRunner(tempDir, publishingBus, null, stateManager)) {
            runner.replayAll(BrokerAdapterError.class);
        }

        assertEquals(1, beforeCount.get(), "beforeReplay must be called exactly once");
        assertEquals(1, publishingBus.totalCount(), "Event must be published after beforeReplay");
    }

    @Test
    void afterReplayIsCalledAfterAllEventsProcessed() throws Exception {
        BrokerAdapterError event1 = new BrokerAdapterError(EventMetadata.root(), "dhan", "feed", "e1");
        BrokerAdapterError event2 = new BrokerAdapterError(EventMetadata.root(), "dhan", "feed", "e2");
        writeEntries(mapper.writeValueAsString(event1), mapper.writeValueAsString(event2));

        AtomicInteger afterCount = new AtomicInteger();
        ReplayStateManager stateManager = new ReplayStateManager() {
            @Override
            public void beforeReplay() {}
            @Override
            public void afterReplay() {
                afterCount.incrementAndGet();
            }
        };

        try (ReplayRunner runner = new ReplayRunner(tempDir, eventBus, null, stateManager)) {
            runner.replayAll(BrokerAdapterError.class);
        }

        assertEquals(1, afterCount.get(), "afterReplay must be called exactly once after all events");
    }

    @Test
    void afterReplayIsCalledEvenWhenQueueIsEmpty() {
        AtomicInteger afterCount = new AtomicInteger();
        ReplayStateManager stateManager = new ReplayStateManager() {
            @Override public void beforeReplay() {}
            @Override
            public void afterReplay() {
                afterCount.incrementAndGet();
            }
        };

        try (ReplayRunner runner = new ReplayRunner(tempDir, eventBus, null, stateManager)) {
            runner.replayAll(BrokerAdapterError.class);
        }

        assertEquals(1, afterCount.get(), "afterReplay must be called even for empty queue");
    }

    @Test
    void afterReplayIsCalledEvenOnDeserializationFailure() throws Exception {
        writeEntries("not valid json at all");

        AtomicInteger afterCount = new AtomicInteger();
        ReplayStateManager stateManager = new ReplayStateManager() {
            @Override public void beforeReplay() {}
            @Override
            public void afterReplay() {
                afterCount.incrementAndGet();
            }
        };

        try (ReplayRunner runner = new ReplayRunner(tempDir, eventBus, null, stateManager)) {
            runner.replayAll(BrokerAdapterError.class);
        }

        assertEquals(1, afterCount.get(), "afterReplay must be called even when events fail to deserialize");
    }

    @Test
    void noopStateManagerIsUsedByDefaultWhenNotProvided() throws Exception {
        BrokerAdapterError event = new BrokerAdapterError(EventMetadata.root(), "dhan", "feed", "noop-test");
        writeEntries(mapper.writeValueAsString(event));

        // 3-arg constructor (no ReplayStateManager) should use NOOP without throwing
        try (ReplayRunner runner = new ReplayRunner(tempDir, eventBus, null)) {
            ReplayResult result = runner.replayAll(BrokerAdapterError.class);
            assertEquals(1L, result.replayed());
        }
    }

    @Test
    void nullStateManagerFallsBackToNoop() throws Exception {
        BrokerAdapterError event = new BrokerAdapterError(EventMetadata.root(), "dhan", "feed", "null-sizer");
        writeEntries(mapper.writeValueAsString(event));

        // Explicit null should be handled gracefully (converted to NOOP)
        try (ReplayRunner runner = new ReplayRunner(tempDir, eventBus, null, null)) {
            ReplayResult result = runner.replayAll(BrokerAdapterError.class);
            assertEquals(1L, result.replayed());
        }
    }

    @Test
    void fourArgConstructorPassesStateManagerToReplayAll() throws Exception {
        BrokerAdapterError event = new BrokerAdapterError(EventMetadata.root(), "dhan", "feed", "4-arg-test");
        writeEntries(mapper.writeValueAsString(event));

        AtomicBoolean beforeCalled = new AtomicBoolean();
        AtomicBoolean afterCalled = new AtomicBoolean();
        ReplayStateManager customManager = new ReplayStateManager() {
            @Override public void beforeReplay() { beforeCalled.set(true); }
            @Override public void afterReplay() { afterCalled.set(true); }
        };

        try (ReplayRunner runner = new ReplayRunner(tempDir, eventBus, null, customManager)) {
            ReplayResult result = runner.replayAll(BrokerAdapterError.class);
            assertTrue(result.isComplete());
            assertTrue(beforeCalled.get(), "beforeReplay should have been called via 4-arg constructor");
            assertTrue(afterCalled.get(), "afterReplay should have been called via 4-arg constructor");
        }
    }
}
