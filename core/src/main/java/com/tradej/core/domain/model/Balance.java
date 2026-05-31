package com.tradej.core.domain.model;

public record Balance(
        String clientId,
        long cashPaisa,
        long collateralPaisa,
        long receivablePaisa,
        long utilizedPaisa,
        long withdrawablePaisa
) {
}
