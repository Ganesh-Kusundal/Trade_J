package com.tradej.core.domain.value;

public record CorrelationId(String value) {
    public CorrelationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CorrelationId must not be null or blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
