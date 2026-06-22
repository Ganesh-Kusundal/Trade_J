package com.tradej.pipeline.registry;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.runtime.PipelineNode;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Declarative descriptor for a pipeline node type.
 * <p>
 * Carries metadata about the node's input/output event types, config schema,
 * category, and factory function. Used by the frontend to render the node palette
 * and config panels, and by the compiler for type-safe wiring validation.
 */
public record NodeTypeDescriptor(
        String typeId,
        String displayName,
        String category,
        String description,
        List<EventType> inputEvents,
        List<EventType> outputEvents,
        Map<String, ConfigField> configFields,
        Function<PipelineNodeDef, PipelineNode> factory
) {

    public PipelineNode create(PipelineNodeDef definition) {
        if (factory == null) {
            throw new IllegalStateException("Node type '" + typeId + "' has no factory");
        }
        return factory.apply(definition);
    }

    public record EventType(Class<? extends DomainEvent> type, String description) {
    }

    public record ConfigField(String key, FieldType type, String label, Object defaultValue, String... options) {

        public enum FieldType {
            STRING,
            NUMBER,
            BOOLEAN,
            SELECT,
            JSON,
            SYMBOL_LIST,
            INTERVAL_LIST
        }

        /**
         * Creates a text/number/boolean config field without options.
         */
        public static ConfigField of(String key, FieldType type, String label, Object defaultValue) {
            return new ConfigField(key, type, label, defaultValue);
        }

        /**
         * Creates a select config field with enumerated options.
         */
        public static ConfigField select(String key, String label, Object defaultValue, String... options) {
            return new ConfigField(key, FieldType.SELECT, label, defaultValue, options);
        }
    }
}
