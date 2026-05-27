package com.tradej.broker.dhan.options;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Short-lived in-memory cache for Dhan option expiry lists to avoid exhausting the
 * OPTION_CHAIN rate bucket on repeated {@code getExpiries} calls.
 */
public final class OptionExpiryCache {
    private final long ttlMillis;
    private final ConcurrentHashMap<String, CacheEntry> entries = new ConcurrentHashMap<>();

    public OptionExpiryCache(long ttlMinutes) {
        if (ttlMinutes <= 0) {
            throw new IllegalArgumentException("option expiry cache TTL must be positive");
        }
        this.ttlMillis = ttlMinutes * 60_000L;
    }

    public List<LocalDate> getOrLoad(String cacheKey, Supplier<List<LocalDate>> loader) {
        long now = System.currentTimeMillis();
        CacheEntry cached = entries.get(cacheKey);
        if (cached != null && cached.expiresAtEpochMs > now) {
            return cached.expiries;
        }
        List<LocalDate> loaded = List.copyOf(loader.get());
        entries.put(cacheKey, new CacheEntry(loaded, now + ttlMillis));
        return loaded;
    }

    public void invalidate(String cacheKey) {
        entries.remove(cacheKey);
    }

    public void clear() {
        entries.clear();
    }

    private record CacheEntry(List<LocalDate> expiries, long expiresAtEpochMs) {
    }
}
