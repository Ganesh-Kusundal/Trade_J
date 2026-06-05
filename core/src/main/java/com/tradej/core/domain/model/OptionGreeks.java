package com.tradej.core.domain.model;

public record OptionGreeks(
        Double delta,
        Double theta,
        Double gamma,
        Double vega,
        Double impliedVolatility
) {
    public static final OptionGreeks UNKNOWN = new OptionGreeks(null, null, null, null, null);
}
