package com.tradej.broker.core.capability;

import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.core.domain.value.ExchangeSegment;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class BrokerCapabilityLimits {

    private final Map<ExchangeSegment, CapabilityEntry> cache = new ConcurrentHashMap<>();
    private final Duration ttl;

    public record CapabilityEntry(BrokerCapabilities capabilities, Instant refreshedAt) {
        public boolean isStale(Duration ttl) {
            return Duration.between(refreshedAt, Instant.now()).compareTo(ttl) > 0;
        }
    }

    public BrokerCapabilityLimits(Duration ttl) {
        this.ttl = ttl;
    }

    public void put(BrokerCapabilities capabilities) {
        capabilities.venues().forEach((segment, venue) ->
                cache.put(segment, new CapabilityEntry(capabilities, Instant.now()))
        );
    }

    public BrokerCapabilities get(ExchangeSegment segment) {
        CapabilityEntry entry = cache.get(segment);
        if (entry == null || entry.isStale(ttl)) {
            return null;
        }
        return entry.capabilities();
    }

    public void invalidate(ExchangeSegment segment) {
        cache.remove(segment);
    }

    public void invalidateAll() {
        cache.clear();
    }

    public Map<ExchangeSegment, BrokerCapabilities> all() {
        return Map.copyOf(
                cache.entrySet().stream()
                        .filter(e -> !e.getValue().isStale(ttl))
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().capabilities()
                        ))
        );
    }
}
