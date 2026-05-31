package com.tradej.node;

import com.tradej.pipeline.platform.model.PropertyDescriptor;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Self-describing contract for every node type in the platform. */
public record NodeDescriptor(
        String nodeType,
        String displayName,
        String description,
        NodeCategory category,
        List<PropertyDescriptor> properties,
        List<PortDescriptor> inputPorts,
        List<PortDescriptor> outputPorts,
        Map<String, Object> metadata
) {
    public NodeDescriptor {
        Objects.requireNonNull(nodeType, "nodeType must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(category, "category must not be null");
        properties = List.copyOf(properties == null ? List.of() : properties);
        inputPorts = List.copyOf(inputPorts == null ? List.of() : inputPorts);
        outputPorts = List.copyOf(outputPorts == null ? List.of() : outputPorts);
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }

    public PropertyDescriptor property(String name) {
        return properties.stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElse(null);
    }
}
