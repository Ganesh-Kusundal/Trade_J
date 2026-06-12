package com.tradej.scanner.spi;

import com.tradej.scanner.criterion.MaxOiStrikeCriterion;
import com.tradej.scanner.criterion.ScanCriterion;

import java.util.Map;

public final class MaxOiStrikeCriterionProvider implements ScanCriterionProvider {

    @Override
    public String type() {
        return "max-oi-strike";
    }

    @Override
    public String displayName() {
        return "Max OI Strike";
    }

    @Override
    public ScanCriterion create(Map<String, Object> config) {
        return new MaxOiStrikeCriterion(
                longValue(config, "min-total-oi", 0L)
        );
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
