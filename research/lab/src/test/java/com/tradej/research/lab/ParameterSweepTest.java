package com.tradej.research.lab;

import com.tradej.research.core.StrategyConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ParameterSweepTest {

    @Test
    public void testParameterSweepCombinations() {
        ParameterSweep.SweepTask task = new ParameterSweep.SweepTask(
            "RsiTrend",
            "1.0",
            Map.of(
                "rsiPeriod", List.of(14, 21),
                "rsiThreshold", List.of(30.0, 40.0)
            )
        );

        List<StrategyConfig> configs = ParameterSweep.generateGridConfigs(task);
        assertNotNull(configs);
        // 2 x 2 combinations
        assertEquals(4, configs.size());

        for (StrategyConfig config : configs) {
            assertEquals("RsiTrend", config.strategyName());
            assertEquals("1.0", config.version());
            assertNotNull(config.configHash());
            assertEquals(32 * 2, config.configHash().length()); // SHA-256 in hex is 64 chars
        }
    }
}
