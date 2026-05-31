package com.tradej.feature.store.node;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.FeatureStore;
import com.tradej.pipeline.runtime.BasePipelineNode;

/**
 * Pipeline Node wrapper for any FeatureStore implementation (e.g. DuckDbFeatureStore).
 * Feeds ticks and candles into the feature store for persistent time-series storage.
 */
public final class FeatureNode extends BasePipelineNode {

    private final FeatureStore featureStore;

    public FeatureNode(FeatureStore featureStore) {
        this.featureStore = featureStore;
    }

    @Override
    protected void onInit() {
        // No additional init needed
    }

    @Override
    protected void processEvent(DomainEvent event) throws Exception {
        // Hot-path feature sync only — no pass-through publish (matches FeatureSyncDisruptorHandler).
        featureStore.feed(event);
    }

    @Override
    protected void onDestroy() {
        if (featureStore instanceof AutoCloseable autoCloseable) {
            try {
                autoCloseable.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
