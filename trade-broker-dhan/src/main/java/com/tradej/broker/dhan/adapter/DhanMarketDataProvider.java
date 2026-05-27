package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.historical.DhanHistoricalDataClient;
import com.tradej.broker.dhan.historical.DhanHistoricalDataMapper;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanSdkMapper;
import com.tradej.broker.dhan.mapper.DhanSdkResponse;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanResilienceExecutor;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.PriceMath;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DhanMarketDataProvider extends DhanBaseRestAdapter implements MarketDataProvider {
    private final DhanHistoricalDataClient historicalDataClient;
    private final DhanHistoricalDataMapper historicalDataMapper;

    /**
     * Primary constructor — all dependencies injected from outside.
     * Used by Spring DI and test configurations.
     */
    public DhanMarketDataProvider(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver instrumentResolver,
            DhanResilienceExecutor resilienceExecutor,
            DhanHistoricalDataClient historicalDataClient,
            DhanHistoricalDataMapper historicalDataMapper
    ) {
        super(clientHolder, instrumentResolver, resilienceExecutor);
        this.historicalDataClient = historicalDataClient;
        this.historicalDataMapper = historicalDataMapper;
    }

    /**
     * Convenience constructor for legacy callers that do not have
     * pre-constructed historical data components. Creates them internally.
     *
     * @deprecated Use the 5-arg constructor for DI environments.
     */
    @Deprecated
    public DhanMarketDataProvider(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver instrumentResolver,
            DhanAuthenticatedHttpClient httpClient,
            DhanResilienceExecutor resilienceExecutor
    ) {
        super(clientHolder, instrumentResolver, resilienceExecutor);
        this.historicalDataClient = new DhanHistoricalDataClient(httpClient, resilienceExecutor);
        this.historicalDataMapper = new DhanHistoricalDataMapper();
    }

    @Override
    public long getLtpPaisa(InstrumentKey instrumentKey) {
        DhanInstrumentDefinition definition = resolveDef(instrumentKey);
        return execute(ApiCategory.QUOTE, "quote-ltp", () -> {
            DhanSdkResponse<?> raw = new DhanSdkResponse<>(clientHolder.client().getLtp(
                    Map.of(definition.exchangeSegment().name(), List.of(Integer.parseInt(definition.securityId())))))
                    .nestedValue(definition.exchangeSegment().name(), definition.securityId());
            return PriceMath.toPaisa(raw.decimal("getLastPrice"));
        });
    }

    @Override
    public Quote getQuote(InstrumentKey instrumentKey) {
        DhanInstrumentDefinition definition = resolveDef(instrumentKey);
        return execute(ApiCategory.QUOTE, "quote-snapshot", () -> {
            DhanSdkResponse<?> raw = new DhanSdkResponse<>(clientHolder.client().getQuote(
                    Map.of(definition.exchangeSegment().name(), List.of(Integer.parseInt(definition.securityId())))))
                    .nestedValue(definition.exchangeSegment().name(), definition.securityId());
            return DhanSdkMapper.toQuote(raw, definition.toInstrument());
        });
    }

    @Override
    public MarketDepth getDepth(InstrumentKey instrumentKey) {
        DhanInstrumentDefinition definition = resolveDef(instrumentKey);
        return execute(ApiCategory.QUOTE, "quote-depth", () -> {
            DhanSdkResponse<?> raw = new DhanSdkResponse<>(clientHolder.client().getQuote(
                    Map.of(definition.exchangeSegment().name(), List.of(Integer.parseInt(definition.securityId())))))
                    .nestedValue(definition.exchangeSegment().name(), definition.securityId());
            return DhanSdkMapper.toDepth(raw, definition.toInstrument());
        });
    }

    @Override
    public Quote getOhlcSnapshot(InstrumentKey instrumentKey) {
        return getQuote(instrumentKey);
    }

    @Override
    public List<Candle> getCandles(CandleHistoryRequest request) {
        DhanInstrumentDefinition definition = resolveDef(request.instrument());
        Instrument instrument = definition.toInstrument();
        return historicalDataMapper.toCandles(historicalDataClient.fetch(request, definition), instrument, request.interval());
    }

    @Override
    public Map<InstrumentKey, Long> getLtpBatch(Collection<InstrumentKey> instrumentKeys) {
        if (instrumentKeys == null || instrumentKeys.isEmpty()) {
            return Map.of();
        }
        return execute(ApiCategory.QUOTE, "quote-ltp-batch", () -> {
            Map<InstrumentKey, Long> out = new HashMap<>();
            for (InstrumentKey key : instrumentKeys) {
                DhanInstrumentDefinition definition = resolveDef(key);
                DhanSdkResponse<?> raw = new DhanSdkResponse<>(clientHolder.client().getLtp(
                        Map.of(definition.exchangeSegment().name(), List.of(Integer.parseInt(definition.securityId())))))
                        .nestedValue(definition.exchangeSegment().name(), definition.securityId());
                out.put(key, PriceMath.toPaisa(raw.decimal("getLastPrice")));
            }
            return Map.copyOf(out);
        });
    }

    @Override
    public Map<InstrumentKey, Quote> getQuoteBatch(Collection<InstrumentKey> instrumentKeys) {
        if (instrumentKeys == null || instrumentKeys.isEmpty()) {
            return Map.of();
        }
        return execute(ApiCategory.QUOTE, "quote-snapshot-batch", () -> {
            Map<InstrumentKey, Quote> out = new HashMap<>();
            for (InstrumentKey key : instrumentKeys) {
                DhanInstrumentDefinition definition = resolveDef(key);
                DhanSdkResponse<?> raw = new DhanSdkResponse<>(clientHolder.client().getQuote(
                        Map.of(definition.exchangeSegment().name(), List.of(Integer.parseInt(definition.securityId())))))
                        .nestedValue(definition.exchangeSegment().name(), definition.securityId());
                out.put(key, DhanSdkMapper.toQuote(raw, definition.toInstrument()));
            }
            return Map.copyOf(out);
        });
    }

    @Override
    public Map<InstrumentKey, Quote> getOhlcBatch(Collection<InstrumentKey> instrumentKeys) {
        return getQuoteBatch(instrumentKeys);
    }
}
