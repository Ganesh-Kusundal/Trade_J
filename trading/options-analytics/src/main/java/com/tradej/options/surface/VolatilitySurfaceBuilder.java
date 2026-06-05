package com.tradej.options.surface;

import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.VolatilitySurface;
import com.tradej.core.domain.value.OptionType;
import com.tradej.options.calculator.IVSolver;
import com.tradej.options.greeks.OptionsAnalyticsCache;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VolatilitySurfaceBuilder {

    private static final double RISK_FREE_RATE = 0.065;

    private final OptionsAnalyticsCache cache;

    public VolatilitySurfaceBuilder(OptionsAnalyticsCache cache) {
        this.cache = cache;
    }

    public VolatilitySurface build(OptionChainSnapshot chain) {
        if (chain == null || chain.strikes() == null) {
            return null;
        }
        double spot = chain.spotPricePaisa() / 100.0;
        LocalDate expiry = chain.expiry();
        double tte = Math.max(1.0 / 365.0, ChronoUnit.DAYS.between(LocalDate.now(), expiry) / 365.0);
        Map<Long, Double> ivMap = new LinkedHashMap<>();
        for (OptionChainEntry entry : chain.strikes()) {
            double strike = entry.strikePricePaisa() / 100.0;
            double callPrice = entry.call() != null ? entry.call().ltpPaisa() / 100.0 : 0;
            double putPrice = entry.put() != null ? entry.put().ltpPaisa() / 100.0 : 0;
            double iv = Double.NaN;
            if (callPrice > 0) {
                iv = IVSolver.solve(callPrice, spot, strike, tte, RISK_FREE_RATE, OptionType.CALL);
            } else if (putPrice > 0) {
                iv = IVSolver.solve(putPrice, spot, strike, tte, RISK_FREE_RATE, OptionType.PUT);
            }
            if (!Double.isNaN(iv)) {
                ivMap.put(entry.strikePricePaisa(), iv);
            }
        }
        String underlying = chain.underlying() != null ? chain.underlying().symbol() : "UNKNOWN";
        VolatilitySurface surface = new VolatilitySurface(underlying, expiry, chain.spotPricePaisa(), Map.copyOf(ivMap));
        cache.putIvSurface(
                new OptionsAnalyticsCache.AggregateKey(underlying, expiry.toString()),
                ivMap);
        return surface;
    }
}
