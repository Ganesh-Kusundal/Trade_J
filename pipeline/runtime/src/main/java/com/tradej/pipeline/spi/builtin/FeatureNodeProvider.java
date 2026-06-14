package com.tradej.pipeline.spi.builtin;

import com.tradej.core.domain.port.FeatureStore;
import com.tradej.feature.store.node.FeatureNode;
import com.tradej.pipeline.registry.NodeRegistry;
import com.tradej.pipeline.registry.NodeTypeDescriptor;
import com.tradej.pipeline.runtime.PipelineNode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import com.tradej.pipeline.spi.NoopPipelineNode;
import com.tradej.pipeline.spi.PipelineNodeProvider;

import java.util.List;
import java.util.Map;

/**
 * Built-in provider for the FEATURE node type.
 * <p>
 * The legacy code path produced a no-op node when the hot-path feature
 * store was unavailable; this provider preserves that behaviour.
 */
public final class FeatureNodeProvider implements PipelineNodeProvider {

    @Override
    public String typeId() {
        return PipelineNodeTypes.FEATURE;
    }

    @Override
    public String displayName() {
        return "Hot-Path Feature Sync";
    }

    @Override
    public void registerMetadata(NodeRegistry registry) {
        // FEATURE was registered in registerNodeFactories but not in the
        // metadata-only nodeRegistry(). Register a stub descriptor here for
        // completeness so the catalog is symmetric.
        registry.register(NodeProviderSupport.descriptorWith(
                typeId(),
                displayName(),
                "feature",
                "Hot-path feature store synchronisation",
                List.of(),
                List.of(),
                Map.of()));
    }

    @Override
    public void registerFactory(NodeRegistry registry, Map<String, Object> config) {
        FeatureStore featureStore = NodeProviderSupport.typed(
                config, PipelineNodeProvider.CONFIG_KEY_FEATURE_STORE, FeatureStore.class);
        registry.register(new NodeTypeDescriptor(
                typeId(),
                displayName(),
                "feature",
                "Hot-path feature store synchronisation",
                List.of(),
                List.of(),
                Map.of(),
                def -> featureStore != null
                        ? new FeatureNode(featureStore)
                        : NoopPipelineNode.create("Feature store unavailable")));
    }
}
