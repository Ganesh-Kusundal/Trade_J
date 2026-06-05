package com.tradej.core.domain.model;

import java.time.LocalDate;
import java.util.Map;

public record VolatilitySurface(
        String underlying,
        LocalDate expiry,
        long spotPricePaisa,
        Map<Long, Double> ivByStrikePaisa
) {
    public double getIv(long strikePaisa) {
        return ivByStrikePaisa.getOrDefault(strikePaisa, Double.NaN);
    }
}
