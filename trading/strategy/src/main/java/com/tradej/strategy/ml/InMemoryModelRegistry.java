package com.tradej.strategy.ml;

import com.tradej.core.domain.port.ModelRegistry;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * In-memory {@link ModelRegistry} for testing and development use.
 *
 * <p>Models are registered by name and are immediately available after
 * {@link #loadModel(String)} is called. No actual ML model loading occurs.
 */
public final class InMemoryModelRegistry implements ModelRegistry {

    private final Set<String> loaded = new HashSet<>();
    private final Set<String> known;

    public InMemoryModelRegistry(String... modelNames) {
        this.known = new HashSet<>();
        Collections.addAll(this.known, modelNames);
    }

    @Override
    public void loadModel(String modelName) {
        if (!known.contains(modelName)) {
            throw new IllegalArgumentException("Unknown model: " + modelName);
        }
        loaded.add(modelName);
    }

    @Override
    public boolean isLoaded(String modelName) {
        return loaded.contains(modelName);
    }

    @Override
    public List<String> availableModels() {
        return List.copyOf(known);
    }
}
