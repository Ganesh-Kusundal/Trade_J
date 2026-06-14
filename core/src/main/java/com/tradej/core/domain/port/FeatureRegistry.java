package com.tradej.core.domain.port;

import java.util.Set;

/**
 * Registry for runtime feature flags. Controls capability exposure
 * to the frontend and CLI.
 */
public interface FeatureRegistry {

    boolean isEnabled(String featureName);

    Set<String> enabledFeatures();

    Set<String> allFeatures();

    default boolean isDisabled(String featureName) {
        return !isEnabled(featureName);
    }
}
