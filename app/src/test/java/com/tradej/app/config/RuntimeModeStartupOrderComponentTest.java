package com.tradej.app.config;

import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NR-02 regression: {@link RuntimeConfiguration#applyConfiguredMode()} must run
 * (today via {@code @PostConstruct}) before any {@link ApplicationRunner} reads
 * {@link RuntimeModeHolder#mode()}.
 */
@Tag("component")
class RuntimeModeStartupOrderComponentTest {

    @Test
    void replayModeAppliedBeforeApplicationRunnerReadsHolder() {
        assertModeVisibleToRunner(RuntimeMode.REPLAY);
    }

    @Test
    void backtestModeAppliedBeforeApplicationRunnerReadsHolder() {
        assertModeVisibleToRunner(RuntimeMode.BACKTEST);
    }

    private static void assertModeVisibleToRunner(RuntimeMode configured) {
        RuntimeModeHolder holder = new RuntimeModeHolder();
        TradingProperties properties = new TradingProperties(
                null, null, null, null, null, null, null, null,
                new TradingProperties.RuntimeProperties(configured),
                null, null, null, null, null, null, null, null
        );
        new RuntimeConfiguration(properties, holder).applyConfiguredMode();

        ApplicationRunner startupRunner = args -> { /* no-op: simulates BrokerStartupOrchestrator entry */ };
        try {
            startupRunner.run(new DefaultApplicationArguments());
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }

        assertEquals(configured, holder.mode(),
                "ApplicationRunner phase must observe configured runtime mode, not default LIVE");
    }
}
