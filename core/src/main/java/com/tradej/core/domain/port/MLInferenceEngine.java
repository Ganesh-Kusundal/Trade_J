package com.tradej.core.domain.port;

import com.tradej.core.domain.model.FeatureVector;
import com.tradej.core.domain.model.InferenceResult;

import java.util.Optional;

/**
 * Port interface for ML model inference on feature vectors.
 *
 * <p>Converts a {@link FeatureVector} into an {@link InferenceResult} by
 * running it through a loaded ML model. Implementations may use ONNX Runtime,
 * TorchScript, or rule-based simulation.
 *
 * @see ModelRegistry
 * @see FeatureVector
 * @see InferenceResult
 */
public interface MLInferenceEngine {

    /**
     * Evaluate the given feature vector against the loaded model.
     *
     * @param features computed feature vector for a symbol/interval point-in-time
     * @return an {@link InferenceResult} if confidence exceeds the engine's
     *         internal threshold, or {@link Optional#empty()} if no trade signal
     */
    Optional<InferenceResult> evaluate(FeatureVector features);
}
