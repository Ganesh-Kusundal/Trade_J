package com.tradej.scanner.spi;

import com.tradej.scanner.criterion.PcrRangeCriterion;
import com.tradej.scanner.criterion.ScanCriterion;

import java.util.Map;

public final class PcrRangeCriterionProvider implements ScanCriterionProvider {

    @Override
    public String type() {
        return "pcr-range";
    }

    @Override
    public String displayName() {
        return "PCR Range";
    }

    @Override
    public ScanCriterion create(Map<String, Object> config) {
        return new PcrRangeCriterion(
                doubleValue(config, "min", 0.0),
                doubleValue(config, "max", Double.MAX_VALUE)
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
}
