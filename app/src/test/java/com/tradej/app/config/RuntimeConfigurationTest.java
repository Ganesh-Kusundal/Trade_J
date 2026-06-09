package com.tradej.app.config;

import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the configured {@link RuntimeMode} is applied to the
 * {@link RuntimeModeHolder} during bean initialisation — not after
 * {@code ApplicationRunner#run} or {@code ApplicationReadyEvent}.
 */
class RuntimeConfigurationTest {

    private static TradingProperties withRuntime(TradingProperties.RuntimeProperties runtime) {
        return new TradingProperties(
                null, null, null, null, null, null, null, null,
                runtime,
                null, null, null, null, null, null,
                null, null, null, null
        );
    }

    @Test
    void postConstructAppliesConfiguredModeBeforeApplicationRunnerFires() {
        RuntimeModeHolder holder = new RuntimeAndStartupConfiguration().runtimeModeHolder(
                withRuntime(new TradingProperties.RuntimeProperties(RuntimeMode.BACKTEST))
        );

        assertEquals(RuntimeMode.BACKTEST, holder.mode(),
                "Configured mode must be applied during bean initialization");
    }

    @Test
    void postConstructLeavesDefaultWhenNoRuntimeProperties() {
        RuntimeModeHolder holder = new RuntimeAndStartupConfiguration().runtimeModeHolder(withRuntime(null));

        assertEquals(RuntimeMode.LIVE, holder.mode(),
                "Without explicit runtime config the holder must remain at its default LIVE");
    }

    @Test
    void postConstructHonorsReplayMode() {
        RuntimeModeHolder holder = new RuntimeAndStartupConfiguration().runtimeModeHolder(
                withRuntime(new TradingProperties.RuntimeProperties(RuntimeMode.REPLAY))
        );

        assertEquals(RuntimeMode.REPLAY, holder.mode());
    }
}
