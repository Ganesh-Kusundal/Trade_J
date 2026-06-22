package com.tradej.app.pipeline;

import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.pipeline.clock.VirtualClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("component")
class PipelineRuntimeModeClockTest {

    @Test
    void liveModeUsesLiveVirtualClock() {
        RuntimeModeHolder holder = new RuntimeModeHolder();
        holder.setMode(RuntimeMode.LIVE);

        VirtualClock clock = new PipelineConfiguration().virtualClock(holder);

        assertEquals(VirtualClock.Mode.LIVE, clock.getMode());
    }

    @Test
    void replayModeUsesDeterministicVirtualClock() {
        RuntimeModeHolder holder = new RuntimeModeHolder();
        holder.setMode(RuntimeMode.REPLAY);

        VirtualClock clock = new PipelineConfiguration().virtualClock(holder);

        assertEquals(VirtualClock.Mode.REPLAY, clock.getMode());
    }

    @Test
    void backtestModeUsesDeterministicVirtualClock() {
        RuntimeModeHolder holder = new RuntimeModeHolder();
        holder.setMode(RuntimeMode.BACKTEST);

        VirtualClock clock = new PipelineConfiguration().virtualClock(holder);

        assertEquals(VirtualClock.Mode.REPLAY, clock.getMode());
    }
}
