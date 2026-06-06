package com.tradej.core.domain.value;

public record OrderId(String value) {
    public OrderId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OrderId must not be null or blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
