package com.tradej.broker.core.capability;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Type-safe capability registry backed by an immutable map.
 * Replaces if/else chains of isInstance checks in getCapability().
 */
public final class CapabilityMap {

    private final Map<Class<?>, Object> capabilities;

    private CapabilityMap(Map<Class<?>, Object> capabilities) {
        this.capabilities = Map.copyOf(capabilities);
    }

    /**
     * Look up a capability by its exact type, with fallback to isInstance scan.
     */
    public <T> Optional<T> get(Class<T> type) {
        if (type == null) return Optional.empty();
        Object exact = capabilities.get(type);
        if (exact != null) {
            return Optional.of(type.cast(exact));
        }
        for (Object value : capabilities.values()) {
            if (type.isInstance(value)) {
                return Optional.of(type.cast(value));
            }
        }
        return Optional.empty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<Class<?>, Object> map = new LinkedHashMap<>();

        public Builder register(Class<?> type, Object impl) {
            if (impl != null) {
                map.put(type, impl);
            }
            return this;
        }

        public Builder registerIfNotNull(Class<?> type, Object impl) {
            if (impl != null) {
                map.put(type, impl);
            }
            return this;
        }

        public CapabilityMap build() {
            return new CapabilityMap(map);
        }
    }
}
