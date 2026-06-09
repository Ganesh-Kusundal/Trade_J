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
                null, null, null
        );
    }

    @Test
    void postConstructAppliesConfiguredModeBeforeApplicationRunnerFires() {
        RuntimeModeHolder holder = new RuntimeModeHolder();
        assertEquals(RuntimeMode.LIVE, holder.mode(), "Default mode is LIVE");

        new RuntimeConfiguration(
                withRuntime(new TradingProperties.RuntimeProperties(RuntimeMode.BACKTEST)),
                holder
        ).applyConfiguredMode();

        assertEquals(RuntimeMode.BACKTEST, holder.mode(),
                "Configured mode must be applied during @PostConstruct, before any ApplicationRunner");
    }

    @Test
    void postConstructLeavesDefaultWhenNoRuntimeProperties() {
        RuntimeModeHolder holder = new RuntimeModeHolder();
        new RuntimeConfiguration(withRuntime(null), holder).applyConfiguredMode();

        assertEquals(RuntimeMode.LIVE, holder.mode(),
                "Without explicit runtime config the holder must remain at its default LIVE");
    }

    @Test
    void postConstructHonorsReplayMode() {
        RuntimeModeHolder holder = new RuntimeModeHolder();
        new RuntimeConfiguration(
                withRuntime(new TradingProperties.RuntimeProperties(RuntimeMode.REPLAY)),
                holder
        ).applyConfiguredMode();

        assertEquals(RuntimeMode.REPLAY, holder.mode());
    }
}
