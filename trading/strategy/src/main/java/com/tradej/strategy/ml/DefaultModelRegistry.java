package com.tradej.strategy.ml;

import com.tradej.core.domain.port.ModelRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ModelRegistry} that pre-loads models eagerly.
 *
 * <p>This implementation loads all registered model names at construction time
 * and reports them as loaded. It is suitable for development and demo use
 * where real ONNX/TorchScript model loading is not required.
 *
 * <p>For production, replace with an implementation that loads ONNX models
 * via the ModelRegistry port interface.
 */
public final class DefaultModelRegistry implements ModelRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultModelRegistry.class);

    private final Set<String> loadedModels = ConcurrentHashMap.newKeySet();
    private final List<String> available;

    /**
     * Creates a registry with the given model names, all pre-loaded.
     *
     * @param modelNames list of model names available for inference
     */
    public DefaultModelRegistry(List<String> modelNames) {
        this.available = List.copyOf(modelNames);
        for (String name : modelNames) {
            loadedModels.add(name);
            log.info("Model '{}' loaded (in-memory simulation)", name);
        }
    }

    @Override
    public void loadModel(String modelName) {
        loadedModels.add(modelName);
        log.info("Model '{}' loaded (in-memory simulation)", modelName);
    }

    @Override
    public boolean isLoaded(String modelName) {
        return loadedModels.contains(modelName);
    }

    @Override
    public List<String> availableModels() {
        return available;
    }
}
