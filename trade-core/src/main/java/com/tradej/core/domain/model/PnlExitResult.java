package com.tradej.core.domain.model;

public record PnlExitResult(
        boolean enabled,
        String status,
        String message
) {
}
