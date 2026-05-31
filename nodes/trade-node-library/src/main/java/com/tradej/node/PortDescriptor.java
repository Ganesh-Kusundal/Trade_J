package com.tradej.node;

import com.tradej.pipeline.platform.model.PropertyDescriptor;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Declares a typed input or output port on a node descriptor. */
public record PortDescriptor(
        String name,
        String description,
        PortType type,
        boolean required,
        List<String> acceptedDataTypes,
        Map<String, Object> metadata
) {
    public PortDescriptor {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        acceptedDataTypes = List.copyOf(acceptedDataTypes == null ? List.of() : acceptedDataTypes);
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }

    public enum PortType {
        SINGLE,
        MULTI,
        ANY
    }
}
