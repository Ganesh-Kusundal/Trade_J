package com.tradej.core.domain.value;

public record Symbol(String value) {
    public Symbol {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Symbol must not be null or blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
