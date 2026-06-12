package com.tradej.pipeline.spi;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * ServiceLoader-based registry of all available {@link PipelineNodeProvider}s.
 * <p>
 * Discovered implementations are keyed by {@link PipelineNodeProvider#typeId()}.
 * If two providers register the same typeId the constructor throws
 * {@link IllegalStateException} so the failure is surfaced at startup rather
 * than silently overwriting.
 */
public final class PipelineNodeRegistry {

    private final Map<String, PipelineNodeProvider> providers = new HashMap<>();

    public PipelineNodeRegistry() {
        for (PipelineNodeProvider p : ServiceLoader.load(PipelineNodeProvider.class)) {
            String typeId = p.typeId();
            if (typeId == null || typeId.isBlank()) {
                throw new IllegalStateException(
                        "PipelineNodeProvider " + p.getClass().getName() + " has blank typeId()");
            }
            PipelineNodeProvider existing = providers.putIfAbsent(typeId, p);
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate PipelineNodeProvider typeId '" + typeId +
                        "': " + existing.getClass().getName() +
                        " vs " + p.getClass().getName());
            }
        }
    }

    public boolean has(String typeId) { return providers.containsKey(typeId); }

    public PipelineNodeProvider get(String typeId) {
        PipelineNodeProvider p = providers.get(typeId);
        if (p == null) {
            throw new IllegalArgumentException(
                    "Unknown pipeline node typeId '" + typeId + "'. Available: " + providers.keySet());
        }
        return p;
    }

    public Collection<PipelineNodeProvider> all() { return providers.values(); }
}
