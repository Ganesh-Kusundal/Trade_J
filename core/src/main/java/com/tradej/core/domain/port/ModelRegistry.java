package com.tradej.core.domain.port;

import java.util.List;

/**
 * Registry of ML models available for inference.
 *
 * <p>Models are identified by name and can be loaded or verified before use.
 * The concrete implementation may load ONNX, TorchScript, or simulated models
 * from the classpath, filesystem, or a remote store.
 */
public interface ModelRegistry {

    /**
     * Load a model by name. After loading, {@link #isLoaded(String)} returns true.
     *
     * @param modelName unique model identifier
     * @throws IllegalArgumentException if the model cannot be found or loaded
     */
    void loadModel(String modelName);

    /**
     * Returns true if the named model has been loaded and is ready for inference.
     */
    boolean isLoaded(String modelName);

    /**
     * Returns the list of all known model names (loaded or not).
     */
    List<String> availableModels();
}
