package com.tradej.core.domain.model;

public record OptionGreeks(
        Double delta,
        Double theta,
        Double gamma,
        Double vega,
        Double impliedVolatility
) {
}
