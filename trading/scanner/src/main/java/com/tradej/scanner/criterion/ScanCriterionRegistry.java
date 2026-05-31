package com.tradej.scanner.criterion;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of named scan criteria for pipeline node creation.
 * <p>
 * Populated at startup by scanning the classpath or via explicit registration.
 * Used by {@code PipelineNodeFactory} to resolve criterion type strings
 * to actual {@link ScanCriterion} instances.
 */
public final class ScanCriterionRegistry {

    private final ConcurrentHashMap<String, ScanCriterion> criteria = new ConcurrentHashMap<>();

    /**
     * Register a named criterion. Replaces any existing entry with the same type.
     */
    public void register(ScanCriterion criterion) {
        criteria.put(criterion.type(), criterion);
    }

    /**
     * Lookup a criterion by type string.
     *
     * @return the criterion, or empty if not found
     */
    public Optional<ScanCriterion> get(String type) {
        return Optional.ofNullable(criteria.get(type));
    }

    /**
     * Returns all registered criteria as an unmodifiable map.
     */
    public Map<String, ScanCriterion> all() {
        return Map.copyOf(criteria);
    }

    /**
     * Number of registered criteria.
     */
    public int size() {
        return criteria.size();
    }
}
