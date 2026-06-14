package com.tradej.scanner.spi;

import com.tradej.scanner.criterion.PctChangeFromOpenCriterion;
import com.tradej.scanner.criterion.ScanCriterion;

import java.util.Map;

public final class PctChangeFromOpenCriterionProvider implements ScanCriterionProvider {

    @Override
    public String type() {
        return "pct-change-from-open";
    }

    @Override
    public String displayName() {
        return "Pct Change From Open";
    }

    @Override
    public ScanCriterion create(Map<String, Object> config) {
        return new PctChangeFromOpenCriterion(
                doubleValue(config, "min", 0.0),
                optionalDouble(config, "max")
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

    private static Double optionalDouble(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
}
