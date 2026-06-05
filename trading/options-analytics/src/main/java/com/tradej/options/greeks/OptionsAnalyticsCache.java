package com.tradej.options.greeks;

import com.tradej.core.domain.model.OptionGreeks;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class OptionsAnalyticsCache {
    private static final int MAX_GREEKS_ENTRIES = 50000;
    private static final int MAX_AGGREGATE_ENTRIES = 5000;

    private final com.github.benmanes.caffeine.cache.Cache<GreeksKey, OptionGreeks> greeksCache;
    private final com.github.benmanes.caffeine.cache.Cache<AggregateKey, Double> gammaExposureCache;
    private final com.github.benmanes.caffeine.cache.Cache<AggregateKey, Map<Long, Double>> ivSurfaceCache;

    public OptionsAnalyticsCache() {
        this.greeksCache = Caffeine.newBuilder()
            .maximumSize(MAX_GREEKS_ENTRIES)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build();
        this.gammaExposureCache = Caffeine.newBuilder()
            .maximumSize(MAX_AGGREGATE_ENTRIES)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build();
        this.ivSurfaceCache = Caffeine.newBuilder()
            .maximumSize(MAX_AGGREGATE_ENTRIES)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build();
    }

    public void putGreeks(GreeksKey key, OptionGreeks greeks) {
        greeksCache.put(key, greeks);
    }

    public OptionGreeks getGreeks(GreeksKey key) {
        return greeksCache.getIfPresent(key);
    }

    public void putGammaExposure(AggregateKey key, Double gamma) {
        gammaExposureCache.put(key, gamma);
    }

    public Double getGammaExposure(AggregateKey key) {
        return gammaExposureCache.getIfPresent(key);
    }

    public void putIvSurface(AggregateKey key, Map<Long, Double> ivSurface) {
        ivSurfaceCache.put(key, ivSurface);
    }

    public Map<Long, Double> getIvSurface(AggregateKey key) {
        return ivSurfaceCache.getIfPresent(key);
    }

    public record GreeksKey(String symbol, long expiryMs, long strikePaisa) {}
    public record AggregateKey(String symbol, String expiry) {}
}
