package com.tradej.research.lab;

import com.tradej.research.core.StrategyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles sweeping strategies across multi-variable grids to find optimal parameter sets.
 */
public final class ParameterSweep {
    private static final Logger log = LoggerFactory.getLogger(ParameterSweep.class);

    public record SweepTask(
        String strategyName,
        String version,
        Map<String, List<Object>> parameterRanges
    ) {}

    /**
     * Generates all combinations (Cartesian product) of parameter sets for grid search.
     */
    public static List<StrategyConfig> generateGridConfigs(SweepTask task) {
        List<Map<String, Object>> combinations = new ArrayList<>();
        List<String> keys = new ArrayList<>(task.parameterRanges().keySet());
        generateCombinationsRecursive(task.parameterRanges(), keys, 0, new HashMap<>(), combinations);

        List<StrategyConfig> configs = new ArrayList<>();
        for (Map<String, Object> params : combinations) {
            configs.add(StrategyConfig.create(task.strategyName(), task.version(), params));
        }
        return configs;
    }

    private static void generateCombinationsRecursive(
        Map<String, List<Object>> ranges,
        List<String> keys,
        int index,
        Map<String, Object> current,
        List<Map<String, Object>> result
    ) {
        if (index == keys.size()) {
            result.add(new HashMap<>(current));
            return;
        }

        String key = keys.get(index);
        List<Object> values = ranges.get(key);
        for (Object val : values) {
            current.put(key, val);
            generateCombinationsRecursive(ranges, keys, index + 1, current, result);
        }
    }
}
