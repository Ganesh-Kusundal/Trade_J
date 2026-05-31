package com.tradej.core.domain.instrument;

import java.util.List;

public record RollingExpiryRoll(RollingExpiryKind kind, int code) {
    public RollingExpiryRoll {
        if (kind == null) {
            throw new IllegalArgumentException("expiry kind is required");
        }
        if (code < 1 || code > 3) {
            throw new IllegalArgumentException("expiry code must be 1..3, got " + code);
        }
    }

    public static RollingExpiryRoll parse(String token) {
        String[] parts = token.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid expiry spec `" + token + "` — expected WEEK:1");
        }
        return new RollingExpiryRoll(
                RollingExpiryKind.fromCode(parts[0]),
                Integer.parseInt(parts[1].trim())
        );
    }

    public static List<RollingExpiryRoll> parseList(List<String> tokens) {
        return tokens.stream().map(RollingExpiryRoll::parse).toList();
    }
}
