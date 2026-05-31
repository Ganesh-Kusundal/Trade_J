package com.tradej.persistence.replay;

import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.testing.CollectingEventBus;
import com.tradej.pipeline.clock.VirtualClock;
import net.openhft.chronicle.queue.ChronicleQueue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class ReplayRunnerVirtualClockTest {

    @TempDir
    Path tempDir;

    @Test
    void advancesVirtualClockDuringReplay() throws Exception {
        Path queuePath = tempDir.resolve("audit");
        try (ChronicleQueue queue = ChronicleQueue.singleBuilder(queuePath.toFile()).build()) {
            var appender = queue.createAppender();
            appender.writeText("""
                    {"metadata":{"eventId":"t1","timestampMs":1000,"timestampMonotonic":1,"sequenceId":1,"correlationId":""},"symbol":"NIFTY","interval":"1m","ltpPaisa":100,"lastTradeQuantity":1,"cumulativeVolume":1,"exchangeTimestampMs":5000,"marketDepth":null}
                    """);
        }

        EventBus bus = new CollectingEventBus();
        VirtualClock clock = new VirtualClock(VirtualClock.Mode.REPLAY);
        clock.enterReplayMode();

        try (ReplayRunner runner = new ReplayRunner(queuePath, bus, clock)) {
            runner.replayAll(TickReceived.class);
        }

        assertTrue(clock.currentTimeMillis() >= 5000L);
    }
}
