package com.tradej.pipeline.spi.builtin;

import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.registry.NodeTypeDescriptor;
import com.tradej.pipeline.runtime.PipelineNode;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Internal helpers used by built-in {@link com.tradej.pipeline.spi.PipelineNodeProvider}
 * implementations.
 */
final class NodeProviderSupport {

    private NodeProviderSupport() {}

    /**
     * Produces a metadata-only descriptor with a stub factory that throws
     * {@link UnsupportedOperationException} if invoked. Matches the
     * pre-existing behaviour of {@code PipelineConfiguration.nodeRegistry()}.
     */
    static NodeTypeDescriptor descriptorWith(
            String typeId,
            String displayName,
            String category,
            String description,
            List<NodeTypeDescriptor.EventType> inputEvents,
            List<NodeTypeDescriptor.EventType> outputEvents,
            Map<String, NodeTypeDescriptor.ConfigField> configFields) {
        return new NodeTypeDescriptor(
                typeId, displayName, category, description,
                inputEvents, outputEvents, configFields,
                stubFactory(typeId));
    }

    /**
     * Produces a stub factory that throws
     * {@link UnsupportedOperationException} if invoked. Matches the
     * pre-existing behaviour of {@code PipelineConfiguration.nodeRegistry()}.
     */
    static Function<PipelineNodeDef, PipelineNode> stubFactory(String typeId) {
        return def -> {
            throw new UnsupportedOperationException(
                    "Node type '" + typeId + "' has no factory registered. "
                            + "Ensure PipelineNodeFactory is properly initialized.");
        };
    }

    /**
     * Looks up {@code key} in the wiring config map and casts the value
     * to {@code type}. Returns {@code null} if the key is absent. This is
     * the standard way built-in providers read collaborators from the
     * config map supplied to {@code registerFactory}.
     */
    @SuppressWarnings("unchecked")
    static <T> T typed(Map<String, Object> config, String key, Class<T> type) {
        if (config == null) return null;
        Object value = config.get(key);
        if (value == null) return null;
        if (!type.isInstance(value)) {
            throw new IllegalStateException(
                    "PipelineNodeProvider config key '" + key + "' has wrong type. "
                            + "Expected " + type.getName() + " but got "
                            + value.getClass().getName());
        }
        return (T) value;
    }
}
