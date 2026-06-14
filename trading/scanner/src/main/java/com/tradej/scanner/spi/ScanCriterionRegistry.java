package com.tradej.scanner.spi;

import com.tradej.scanner.criterion.ScanCriterion;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/** ServiceLoader-based registry of all available {@link ScanCriterionProvider}s. */
public final class ScanCriterionRegistry {
    private final Map<String, ScanCriterionProvider> providers = new HashMap<>();

    public ScanCriterionRegistry() {
        for (ScanCriterionProvider p : ServiceLoader.load(ScanCriterionProvider.class)) {
            ScanCriterionProvider existing = providers.putIfAbsent(p.type(), p);
            if (existing != null) {
                throw new IllegalStateException(
                    "Duplicate ScanCriterionProvider type '" + p.type() +
                    "': " + existing.getClass().getName() + " vs " + p.getClass().getName());
            }
        }
    }

    public boolean has(String type) { return providers.containsKey(type); }

    public ScanCriterion create(String type, Map<String, Object> config) {
        ScanCriterionProvider p = providers.get(type);
        if (p == null) {
            throw new IllegalArgumentException(
                "Unknown scan criterion type '" + type + "'. Available: " + providers.keySet());
        }
        return p.create(config);
    }
}
