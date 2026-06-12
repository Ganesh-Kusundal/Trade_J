package com.tradej.scanner.spi;

import com.tradej.scanner.criterion.ScanCriterion;
import com.tradej.scanner.criterion.VolumeSpikeCriterion;

import java.util.Map;

public final class VolumeSpikeCriterionProvider implements ScanCriterionProvider {

    @Override
    public String type() {
        return "volume-spike";
    }

    @Override
    public String displayName() {
        return "Volume Spike";
    }

    @Override
    public ScanCriterion create(Map<String, Object> config) {
        return new VolumeSpikeCriterion(
                doubleValue(config, "multiplier", 2.0),
                longValue(config, "min-volume", 0L)
        );
    }

    private static double doubleValue(Map<String, Object> config, String key, double defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private static long longValue(Map<String, Object> config, String key, long defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
