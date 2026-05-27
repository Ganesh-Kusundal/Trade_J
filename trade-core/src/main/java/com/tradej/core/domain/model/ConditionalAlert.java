package com.tradej.core.domain.model;

public record ConditionalAlert(
        String alertId,
        String status,
        String message
) {
}
