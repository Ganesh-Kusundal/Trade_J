package com.tradej.broker.core.routing;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Quote;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin LTP lookups across broker nodes; failover for heavier REST calls.
 */
public final class LoadBalancedMarketDataProvider implements MarketDataProvider {

    private final List<MarketDataProvider> providers;
    private final AtomicInteger ltpIndex = new AtomicInteger();

    public LoadBalancedMarketDataProvider(List<MarketDataProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("At least one market data provider is required");
        }
        this.providers = List.copyOf(providers);
    }

    @Override
    public long getLtpPaisa(InstrumentKey instrumentKey) {
        int start = Math.floorMod(ltpIndex.getAndIncrement(), providers.size());
        RuntimeException last = null;
        for (int i = 0; i < providers.size(); i++) {
            MarketDataProvider provider = providers.get(Math.floorMod(start + i, providers.size()));
            try {
                return provider.getLtpPaisa(instrumentKey);
            } catch (RuntimeException ex) {
                last = ex;
            }
        }
        throw new IllegalStateException("All broker market-data nodes failed for " + instrumentKey, last);
    }

    @Override
    public Quote getQuote(InstrumentKey instrumentKey) {
        return withFailover(provider -> provider.getQuote(instrumentKey));
    }

    @Override
    public MarketDepth getDepth(InstrumentKey instrumentKey) {
        return withFailover(provider -> provider.getDepth(instrumentKey));
    }

    @Override
    public Quote getOhlcSnapshot(InstrumentKey instrumentKey) {
        return withFailover(provider -> provider.getOhlcSnapshot(instrumentKey));
    }

    @Override
    public List<Candle> getCandles(CandleHistoryRequest request) {
        return withFailover(provider -> provider.getCandles(request));
    }

    @Override
    public Map<InstrumentKey, Long> getLtpBatch(Collection<InstrumentKey> instrumentKeys) {
        return withFailover(provider -> provider.getLtpBatch(instrumentKeys));
    }

    @Override
    public Map<InstrumentKey, Quote> getQuoteBatch(Collection<InstrumentKey> instrumentKeys) {
        return withFailover(provider -> provider.getQuoteBatch(instrumentKeys));
    }

    @Override
    public Map<InstrumentKey, Quote> getOhlcBatch(Collection<InstrumentKey> instrumentKeys) {
        return withFailover(provider -> provider.getOhlcBatch(instrumentKeys));
    }

    private <T> T withFailover(FailoverCall<T> call) {
        RuntimeException last = null;
        for (MarketDataProvider provider : providers) {
            try {
                return call.apply(provider);
            } catch (RuntimeException ex) {
                last = ex;
            }
        }
        throw new IllegalStateException("All broker market-data nodes failed", last);
    }

    @FunctionalInterface
    private interface FailoverCall<T> {
        T apply(MarketDataProvider provider);
    }
}
