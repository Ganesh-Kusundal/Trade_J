package com.tradej.pipeline.registry;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central catalog of all registered pipeline node types.
 * <p>
 * Thread-safe. Populated at startup (e.g. via Spring @PostConstruct or @Bean).
 * Serves the frontend palette API and the compiler's type-wiring validation.
 */
public final class NodeRegistry {

    private final ConcurrentHashMap<String, NodeTypeDescriptor> descriptors = new ConcurrentHashMap<>();

    /**
     * Register a node type descriptor. Replaces any existing descriptor with the same typeId.
     */
    public void register(NodeTypeDescriptor descriptor) {
        descriptors.put(descriptor.typeId(), descriptor);
    }

    /**
     * Lookup a descriptor by typeId. Throws if not found.
     */
    public NodeTypeDescriptor get(String typeId) {
        NodeTypeDescriptor d = descriptors.get(typeId);
        if (d == null) {
            throw new IllegalArgumentException("Unknown pipeline node type: '" + typeId
                    + "'. Available: " + descriptors.keySet());
        }
        return d;
    }

    /**
     * All registered descriptors, keyed by typeId.
     */
    public Map<String, NodeTypeDescriptor> all() {
        return Collections.unmodifiableMap(new java.util.LinkedHashMap<>(descriptors));
    }

    /**
     * All descriptors belonging to a given category (e.g. "indicator", "scanner", "signal", "risk", "oms", "io").
     */
    public List<NodeTypeDescriptor> byCategory(String category) {
        return descriptors.values().stream()
                .filter(d -> d.category().equals(category))
                .toList();
    }

    /**
     * All known category names.
     */
    public List<String> categories() {
        return descriptors.values().stream()
                .map(NodeTypeDescriptor::category)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Number of registered types.
     */
    public int size() {
        return descriptors.size();
    }
}
