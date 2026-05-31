package com.tradej.scanner.criterion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ScanCriterionFactory {
    private ScanCriterionFactory() {
    }

    public static ScanCriterion fromConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return new CriterionGroup(List.of());
        }
        Object typeObj = config.get("type");
        if (typeObj == null) {
            throw new IllegalArgumentException("Criterion config missing 'type': " + config);
        }
        String type = typeObj.toString().trim().toLowerCase();
        return switch (type) {
            case "pct-change-from-open" -> new PctChangeFromOpenCriterion(
                    doubleValue(config, "min", 0.0),
                    optionalDouble(config, "max")
            );
            case "pct-change-from-prev-close" -> new PctChangeFromPrevCloseCriterion(
                    doubleValue(config, "min", 0.0),
                    optionalDouble(config, "max")
            );
            case "volume-spike" -> new VolumeSpikeCriterion(
                    doubleValue(config, "multiplier", 2.0),
                    longValue(config, "min-volume", 0L)
            );
            case "max-oi-strike" -> new MaxOiStrikeCriterion(
                    longValue(config, "min-total-oi", 0L)
            );
            case "pcr-range" -> new PcrRangeCriterion(
                    doubleValue(config, "min", 0.0),
                    doubleValue(config, "max", Double.MAX_VALUE)
            );
            case "group-and" -> {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> nested = (List<Map<String, Object>>) config.get("criteria");
                List<ScanCriterion> children = new ArrayList<>();
                if (nested != null) {
                    for (Map<String, Object> child : nested) {
                        children.add(fromConfig(child));
                    }
                }
                yield new CriterionGroup(children);
            }
            default -> throw new IllegalArgumentException("Unknown scan criterion type: " + type);
        };
    }

    public static List<ScanCriterion> fromConfigList(List<Map<String, Object>> configs) {
        if (configs == null || configs.isEmpty()) {
            return List.of();
        }
        List<ScanCriterion> criteria = new ArrayList<>();
        for (Map<String, Object> config : configs) {
            criteria.add(fromConfig(config));
        }
        return List.copyOf(criteria);
    }

    public static ScanCriterion composeAll(List<ScanCriterion> criteria) {
        return new CriterionGroup(criteria);
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
