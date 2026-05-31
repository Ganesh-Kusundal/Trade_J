package com.tradej.core.domain.instrument;

import java.util.ArrayList;
import java.util.List;

public record StrikeOffset(int value) {
    private static final int MIN = -10;
    private static final int MAX = 10;

    public StrikeOffset {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException("strike offset must be " + MIN + ".." + MAX + ", got " + value);
        }
    }

    public static StrikeOffset atm() {
        return new StrikeOffset(0);
    }

    public static List<StrikeOffset> atmPlusMinus(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be >= 0");
        }
        if (n > MAX) {
            throw new IllegalArgumentException("n must be <= " + MAX);
        }
        List<StrikeOffset> offsets = new ArrayList<>(2 * n + 1);
        for (int offset = -n; offset <= n; offset++) {
            offsets.add(new StrikeOffset(offset));
        }
        return List.copyOf(offsets);
    }

    public static StrikeOffset parseSpec(String spec) {
        if (spec == null || spec.isBlank()) {
            return atm();
        }
        String trimmed = spec.trim().toUpperCase();
        if ("ATM".equals(trimmed)) {
            return atm();
        }
        if (trimmed.startsWith("ATM+")) {
            return new StrikeOffset(Integer.parseInt(trimmed.substring(4)));
        }
        if (trimmed.startsWith("ATM-")) {
            return new StrikeOffset(-Integer.parseInt(trimmed.substring(4)));
        }
        return new StrikeOffset(Integer.parseInt(trimmed));
    }
}
