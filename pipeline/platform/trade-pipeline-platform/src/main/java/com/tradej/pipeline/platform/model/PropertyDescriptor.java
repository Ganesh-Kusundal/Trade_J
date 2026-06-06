package com.tradej.pipeline.platform.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Self-describing property for a pipeline node configuration field.
 *
 * <p>Replaces the ad-hoc {@link NodeTypeDescriptor.ConfigField}. Every node
 * descriptor exposes a map of property descriptors; the frontend uses this
 * schema to render configuration panels without any hardcoded knowledge of
 * specific node types.
 */
public record PropertyDescriptor(
 String name,
 PropertyType type,
 String displayName,
 String description,
 Object defaultValue,
 boolean required,
 List<ValidationRule> validationRules,
 Map<String, Object> uiHints
) {
 public PropertyDescriptor {
 Objects.requireNonNull(name, "name must not be null");
 Objects.requireNonNull(type, "type must not be null");
 Objects.requireNonNull(displayName, "displayName must not be null");
 validationRules = List.copyOf(validationRules == null ? List.of() : validationRules);
 uiHints = Map.copyOf(uiHints == null ? Map.of() : uiHints);
 }

 public static PropertyDescriptor of(String name, PropertyType type, String displayName) {
 return new PropertyDescriptor(name, type, displayName, "", null, false, List.of(), Map.of());
 }

 public static PropertyDescriptor required(String name, PropertyType type, String displayName) {
 return new PropertyDescriptor(name, type, displayName, "", null, true, List.of(), Map.of());
 }    public <T> T defaultValue(Class<T> expectedType) {
        if (defaultValue == null) return null;
        if (!expectedType.isInstance(defaultValue)) {
            throw new ClassCastException("Default value for '" + name
                    + "' is " + defaultValue.getClass().getName()
                    + ", expected " + expectedType.getName());
        }
        return expectedType.cast(defaultValue);
    }

 public boolean hasDefault() {
 return defaultValue != null;
 }

 public enum PropertyType {
 STRING,
 NUMBER,
 INTEGER,
 BOOLEAN,
 SELECT,
 SYMBOL_LIST,
 INTERVAL_LIST,
 JSON,
 DURATION_MS
 }
}
