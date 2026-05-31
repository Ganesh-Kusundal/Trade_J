package com.tradej.core.domain.instrument;

public enum RollingExpiryKind {
    WEEK,
    MONTH;

    public static RollingExpiryKind fromCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("expiry kind is required");
        }
        return valueOf(value.trim().toUpperCase());
    }
}
