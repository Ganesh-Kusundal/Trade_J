package com.tradej.scanner.option;

import java.time.LocalDate;

public enum OptionExpiryPolicy {
    NEAREST,
    NEXT,
    EXPLICIT;

    public static OptionExpiryPolicy parse(String value) {
        if (value == null || value.isBlank()) {
            return NEAREST;
        }
        String normalized = value.trim().toUpperCase();
        if ("NEAREST".equals(normalized) || "NEAR".equals(normalized)) {
            return NEAREST;
        }
        if ("NEXT".equals(normalized)) {
            return NEXT;
        }
        return EXPLICIT;
    }

    public LocalDate resolve(java.util.List<LocalDate> expiries, LocalDate explicitExpiry) {
        if (expiries == null || expiries.isEmpty()) {
            throw new IllegalStateException("No option expiries available");
        }
        return switch (this) {
            case NEAREST -> expiries.getFirst();
            case NEXT -> expiries.size() > 1 ? expiries.get(1) : expiries.getFirst();
            case EXPLICIT -> {
                if (explicitExpiry == null) {
                    throw new IllegalArgumentException("explicit expiry date required for EXPLICIT policy");
                }
                if (!expiries.contains(explicitExpiry)) {
                    throw new IllegalArgumentException("Expiry not available: " + explicitExpiry);
                }
                yield explicitExpiry;
            }
        };
    }
}
