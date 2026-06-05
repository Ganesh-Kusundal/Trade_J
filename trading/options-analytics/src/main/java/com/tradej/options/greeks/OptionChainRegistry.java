package com.tradej.options.greeks;

import com.tradej.core.domain.model.OptionChainSnapshot;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;

public final class OptionChainRegistry {
    private static final long DEFAULT_MAX_SIZE = 1000;

    private final com.github.benmanes.caffeine.cache.Cache<String, OptionChainSnapshot> chains;

    public OptionChainRegistry() {
        this(DEFAULT_MAX_SIZE, TimeUnit.SECONDS, 30L);
    }

    public OptionChainRegistry(long maxSize, TimeUnit unit, long ttl) {
        this.chains = Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(ttl, unit)
            .build();
    }

    public void register(String key, OptionChainSnapshot snapshot) {
        chains.put(key, snapshot);
    }

    public OptionChainSnapshot get(String key) {
        return chains.getIfPresent(key);
    }

    public long size() {
        return chains.estimatedSize();
    }
}
