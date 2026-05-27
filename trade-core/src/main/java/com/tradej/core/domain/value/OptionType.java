package com.tradej.core.domain.value;

public enum OptionType {
    CALL,
    PUT,
    UNKNOWN;

    public static OptionType fromCode(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        return switch (value.trim().toUpperCase()) {
            case "CE", "CALL", "C" -> CALL;
            case "PE", "PUT", "P" -> PUT;
            default -> UNKNOWN;
        };
    }
}
