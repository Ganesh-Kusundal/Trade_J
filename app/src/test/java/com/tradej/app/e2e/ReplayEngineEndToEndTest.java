package com.tradej.app.e2e;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.ReplayTimeChangedEvent;
import com.tradej.core.domain.event.SimpleEventBus;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.persistence.replay.ReplayClock;
import com.tradej.persistence.replay.ReplayMetrics;
import com.tradej.persistence.replay.ReplayResult;
import com.tradej.persistence.replay.ReplayRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("runtime-e2e")
class ReplayEngineEndToEndTest {

    @TempDir
    Path tempDir;

    private EventBus eventBus;
    private ReplayClock replayClock;
    private final List<DomainEvent> capturedEvents = new CopyOnWriteArrayList<>();
    private final List<ReplayTimeChangedEvent> clockEvents = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new SimpleEventBus();
        replayClock = new ReplayClock(eventBus);

        eventBus.subscribe(MarketTickEvent.class, capturedEvents::add);
        eventBus.subscribe(CandleClosed.class, capturedEvents::add);
        eventBus.subscribe(ReplayTimeChangedEvent.class, clockEvents::add);
        eventBus.subscribe(DomainEvent.class, capturedEvents::add);

        eventBus.start();
    }

    @AfterEach
    void tearDown() {
        eventBus.stop();
        replayClock.close();
    }

    @Test
    void replayResultSummaryFormat() {
        ReplayResult result = new ReplayResult(5L, 4L, 1L, 0L);

        assertThat(result.totalRead()).isEqualTo(5L);
        assertThat(result.replayed()).isEqualTo(4L);
        assertThat(result.skipped()).isEqualTo(1L);
        assertThat(result.failed()).isZero();
        assertThat(result.isComplete()).isFalse();
        assertThat(result.hasIssues()).isTrue();

        String summary = result.summary();
        assertThat(summary).contains("total=5");
        assertThat(summary).contains("replayed=4");
        assertThat(summary).contains("skipped=1");
        assertThat(summary).contains("failed=0");
    }

    @Test
    void emptyReplayResultIsEmpty() {
        ReplayResult result = new ReplayResult(0L, 0L, 0L, 0L);

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.isComplete()).isFalse();
        assertThat(result.hasIssues()).isFalse();
        assertThat(result.summary()).contains("empty");
    }

    @Test
    void completeReplayResultReportsSuccess() {
        ReplayResult result = new ReplayResult(10L, 10L, 0L, 0L);

        assertThat(result.isComplete()).isTrue();
        assertThat(result.hasIssues()).isFalse();
    }

    @Test
    void replayMetricsTrackEventsReplayed() {
        ReplayMetrics metrics = new ReplayMetrics();

        metrics.recordReplay(100L, 5L, 50L);
        metrics.recordReplay(200L, 2L, 80L);

        assertThat(metrics.eventsReplayed()).isEqualTo(300L);
        assertThat(metrics.eventsFailed()).isEqualTo(7L);
        assertThat(metrics.replayCount()).isEqualTo(2L);
        assertThat(metrics.totalDurationMs()).isEqualTo(130L);
    }

    @Test
    void replayMetricsSuccessRate() {
        ReplayMetrics metrics = new ReplayMetrics();
        metrics.recordReplay(95L, 5L, 0L);

        assertThat(metrics.successRate()).isEqualTo(0.95);
    }

    @Test
    void replayMetricsAverageDuration() {
        ReplayMetrics metrics = new ReplayMetrics();
        metrics.recordReplay(100L, 0L, 100L);
        metrics.recordReplay(100L, 0L, 200L);

        assertThat(metrics.averageDurationMs()).isEqualTo(150L);
    }

    @Test
    void replayMetricsReset() {
        ReplayMetrics metrics = new ReplayMetrics();
        metrics.recordReplay(100L, 5L, 50L);

        metrics.reset();

        assertThat(metrics.eventsReplayed()).isZero();
        assertThat(metrics.eventsFailed()).isZero();
        assertThat(metrics.replayCount()).isZero();
        assertThat(metrics.totalDurationMs()).isZero();
    }

    @Test
    void replayClockAdvancesToNewTimestamp() {
        long start = System.currentTimeMillis() + 1_000_000L;
        replayClock.advanceTo(start);

        assertThat(replayClock.currentTimeMs()).isEqualTo(start);
        assertThat(clockEvents).hasSize(1);
        assertThat(clockEvents.get(0).currentTimeMs()).isEqualTo(start);
    }

    @Test
    void replayClockAdvancesToHigherTimestamp() {
        long start = System.currentTimeMillis() + 1_000_000L;
        replayClock.advanceTo(start);
        replayClock.advanceTo(start + 5_000L);

        assertThat(replayClock.currentTimeMs()).isEqualTo(start + 5_000L);
        assertThat(clockEvents).hasSize(2);
        assertThat(clockEvents.get(1).currentTimeMs()).isEqualTo(start + 5_000L);
    }

    @Test
    void replayClockDoesNotAdvanceBackward() {
        long start = System.currentTimeMillis() + 1_000_000L;
        replayClock.advanceTo(start);
        replayClock.advanceTo(start - 1_000L);

        assertThat(replayClock.currentTimeMs()).isEqualTo(start);
        assertThat(clockEvents).hasSize(1);
        assertThat(clockEvents.get(0).currentTimeMs()).isEqualTo(start);
    }

    @Test
    void replayClockSameTimestampDoesNotPublishDuplicate() {
        long start = System.currentTimeMillis() + 1_000_000L;
        replayClock.advanceTo(start);
        replayClock.advanceTo(start);

        assertThat(clockEvents).hasSize(1);
    }

    @Test
    void replayClockSpeedNanosIsConfigurable() {
        replayClock.setReplaySpeedNanos(2_000_000L);
        assertThat(replayClock.replaySpeedNanos()).isEqualTo(2_000_000L);

        replayClock.setReplaySpeedNanos(500_000L);
        assertThat(replayClock.replaySpeedNanos()).isEqualTo(500_000L);
    }

    @Test
    void replayClockGetCurrentTimeReturnsReasonableValue() {
        long before = replayClock.currentTimeMs();
        long now = System.currentTimeMillis();
        assertThat(before).isBetween(now - 1000, now + 1000);
    }

    @Test
    void replayClockTimeChangedEventContainsSpeedNanos() {
        long start = System.currentTimeMillis() + 2_000_000L;
        replayClock.setReplaySpeedNanos(3_000_000L);
        replayClock.advanceTo(start);

        assertThat(clockEvents).hasSize(1);
        assertThat(clockEvents.get(0).currentTimeMs()).isEqualTo(start);
        assertThat(clockEvents.get(0).replaySpeedNanos()).isEqualTo(3_000_000L);
    }

    @Test
    void replayMetricsEventsPerSecond() {
        ReplayMetrics metrics = new ReplayMetrics();
        metrics.recordReplay(1000L, 0L, 1000L);

        assertThat(metrics.eventsPerSecond()).isEqualTo(1000L);
    }

    @Test
    void replayMetricsMaxDuration() {
        ReplayMetrics metrics = new ReplayMetrics();
        metrics.recordReplay(100L, 0L, 50L);
        metrics.recordReplay(100L, 0L, 200L);
        metrics.recordReplay(100L, 0L, 100L);

        assertThat(metrics.maxDurationMs()).isEqualTo(200L);
    }

    @Test
    void replayMetricsToString() {
        ReplayMetrics metrics = new ReplayMetrics();
        metrics.recordReplay(100L, 5L, 50L);

        String str = metrics.toString();
        assertThat(str).contains("replays=1");
        assertThat(str).contains("100 replayed");
        assertThat(str).contains("5 failed");
    }

    @Test
    void replayMetricsZeroEventsHas100PercentSuccess() {
        ReplayMetrics metrics = new ReplayMetrics();

        assertThat(metrics.successRate()).isEqualTo(1.0);
    }

    @Test
    void replayMetricsZeroDurationHasZeroThroughput() {
        ReplayMetrics metrics = new ReplayMetrics();

        assertThat(metrics.eventsPerSecond()).isZero();
    }

    @Test
    void replayMetricsZeroReplayCountHasZeroAverage() {
        ReplayMetrics metrics = new ReplayMetrics();

        assertThat(metrics.averageDurationMs()).isZero();
    }

    @Test
    void replayResultLegacyConstructorDefaultsSkippedToZero() {
        ReplayResult result = new ReplayResult(10L, 8L, 2L);

        assertThat(result.totalRead()).isEqualTo(10L);
        assertThat(result.replayed()).isEqualTo(8L);
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isEqualTo(2L);
    }

    @Test
    void replayClockMultipleAdvancesAreMonotonic() {
        long start = System.currentTimeMillis() + 1_000_000L;
        List<Long> timestamps = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            replayClock.advanceTo(start + i * 1_000L);
            timestamps.add(replayClock.currentTimeMs());
        }

        for (int i = 1; i < timestamps.size(); i++) {
            assertThat(timestamps.get(i)).isGreaterThan(timestamps.get(i - 1));
        }
    }
}
