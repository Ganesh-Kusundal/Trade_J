package com.tradej.core.domain.runtime;

import com.tradej.core.domain.config.TradeDefaults;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class ExecutionModePolicyTest {

    @Test
    void liveModePermitsBrokerSideEffectsAndLowLatencyHotPath() {
        ExecutionModePolicy policy = ExecutionModePolicy.forMode(RuntimeMode.LIVE);

        assertTrue(policy.permitsBrokerSideEffects());
        assertFalse(policy.usesSimulatedExecution());
        assertFalse(policy.usesDeterministicClock());
        assertFalse(policy.permitsHistoricalReplayEndpoints());
        assertFalse(policy.permitsFallbackInfrastructure());
        assertEquals(ExecutionModePolicy.HotPathWaitProfile.LOW_LATENCY, policy.hotPathWaitProfile());
    }

    @Test
    void replayModeUsesDeterministicSimulatedExecution() {
        ExecutionModePolicy policy = ExecutionModePolicy.forMode(RuntimeMode.REPLAY);

        assertFalse(policy.permitsBrokerSideEffects());
        assertTrue(policy.usesSimulatedExecution());
        assertTrue(policy.usesDeterministicClock());
        assertTrue(policy.permitsHistoricalReplayEndpoints());
        assertTrue(policy.permitsFallbackInfrastructure());
        assertEquals(ExecutionModePolicy.HotPathWaitProfile.DETERMINISTIC, policy.hotPathWaitProfile());
    }

    @Test
    void backtestModeUsesBacktestEngineAndDeterministicHotPath() {
        ExecutionModePolicy policy = ExecutionModePolicy.forMode(RuntimeMode.BACKTEST);

        assertFalse(policy.permitsBrokerSideEffects());
        assertTrue(policy.usesSimulatedExecution());
        assertTrue(policy.usesBacktestEngine());
        assertTrue(policy.usesDeterministicClock());
        assertTrue(policy.permitsHistoricalReplayEndpoints());
        assertTrue(policy.permitsFallbackInfrastructure());
        assertEquals(ExecutionModePolicy.HotPathWaitProfile.DETERMINISTIC, policy.hotPathWaitProfile());
    }

    @Test
    void nullModeFallsBackToPlatformDefault() {
        ExecutionModePolicy policy = ExecutionModePolicy.forMode(null);

        assertEquals(TradeDefaults.RUNTIME_MODE, policy.mode());
        assertEquals(ExecutionModePolicy.live(), policy);
    }
}
