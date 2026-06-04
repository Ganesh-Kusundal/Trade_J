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
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.PriceMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class DhanMarketDataProvider extends DhanBaseRestAdapter implements MarketDataProvider {
    private static final Logger log = LoggerFactory.getLogger(DhanMarketDataProvider.class);
    private final DhanHistoricalDataClient historicalDataClient;
    private final DhanHistoricalDataMapper historicalDataMapper;

    /**
     * Primary constructor — all dependencies injected from outside.
     * Used by Spring DI and test configurations.
     */
    public DhanMarketDataProvider(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver instrumentResolver,
            DhanRetryExecutor resilienceExecutor,
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
            DhanRetryExecutor resilienceExecutor
    ) {
        super(clientHolder, instrumentResolver, resilienceExecutor);
        this.historicalDataClient = new DhanHistoricalDataClient(httpClient, resilienceExecutor);
        this.historicalDataMapper = new DhanHistoricalDataMapper();
    }

    @Override
    public long getLtpPaisa(InstrumentKey instrumentKey) {
        DhanInstrumentDefinition definition = resolveDef(instrumentKey);
        return execute(ApiCategory.QUOTE, "quote-ltp", () -> {
            DhanSdkResponse<?> raw = extractQuotePayload(
                    new DhanSdkResponse<>(clientHolder.client().getLtp(
                            Map.of(definition.exchangeSegment().name(), List.of(Integer.parseInt(definition.securityId()))))),
                    definition
            );
            return PriceMath.toPaisa(raw.decimal("getLastPrice"));
        });
    }

    @Override
    public Quote getQuote(InstrumentKey instrumentKey) {
        DhanInstrumentDefinition definition = resolveDef(instrumentKey);
        return execute(ApiCategory.QUOTE, "quote-snapshot", () -> {
            DhanSdkResponse<?> raw = extractQuotePayload(
                    new DhanSdkResponse<>(clientHolder.client().getQuote(
                            Map.of(definition.exchangeSegment().name(), List.of(Integer.parseInt(definition.securityId()))))),
                    definition
            );
            return DhanSdkMapper.toQuote(raw, definition.toInstrument());
        });
    }

    @Override
    public MarketDepth getDepth(InstrumentKey instrumentKey) {
        DhanInstrumentDefinition definition = resolveDef(instrumentKey);
        return execute(ApiCategory.QUOTE, "quote-depth", () -> {
            DhanSdkResponse<?> raw = extractQuotePayload(
                    new DhanSdkResponse<>(clientHolder.client().getQuote(
                            Map.of(definition.exchangeSegment().name(), List.of(Integer.parseInt(definition.securityId()))))),
                    definition
            );
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
        List<Candle> merged = new ArrayList<>();
        for (var payload : historicalDataClient.fetchRange(request, definition)) {
            merged.addAll(historicalDataMapper.toCandles(payload, instrument, request.interval()));
        }
        return mergeCandles(merged);
    }

    static List<Candle> mergeCandles(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }
        return candles.stream()
                .collect(Collectors.toMap(
                        candle -> candle.startTimeMs() + "|" + candle.endTimeMs(),
                        candle -> candle,
                        (left, right) -> left
                ))
                .values().stream()
                .sorted(Comparator.comparingLong(Candle::startTimeMs))
                .toList();
    }

    @Override
    public Map<InstrumentKey, Long> getLtpBatch(Collection<InstrumentKey> instrumentKeys) {
        if (instrumentKeys == null || instrumentKeys.isEmpty()) {
            return Map.of();
        }
        return execute(ApiCategory.QUOTE, "quote-ltp-batch", () -> {
            Map<String, List<Integer>> segmentSecurityIds = groupBySegment(instrumentKeys);
            DhanSdkResponse<?> raw = new DhanSdkResponse<>(clientHolder.client().getLtp(segmentSecurityIds));
            Map<InstrumentKey, Long> out = new HashMap<>();
            for (InstrumentKey key : instrumentKeys) {
                DhanInstrumentDefinition def = resolveDef(key);
                try {
                    DhanSdkResponse<?> nested = extractQuotePayload(raw, def);
                    out.put(key, PriceMath.toPaisa(nested.decimal("getLastPrice")));
                } catch (RuntimeException ex) {
                    log.warn("Failed to extract LTP for {}:{}: {}", def.exchangeSegment(), def.securityId(), ex.getMessage());
                }
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
            Map<String, List<Integer>> segmentSecurityIds = groupBySegment(instrumentKeys);
            DhanSdkResponse<?> raw = new DhanSdkResponse<>(clientHolder.client().getQuote(segmentSecurityIds));
            Map<InstrumentKey, Quote> out = new HashMap<>();
            for (InstrumentKey key : instrumentKeys) {
                DhanInstrumentDefinition def = resolveDef(key);
                try {
                    DhanSdkResponse<?> nested = extractQuotePayload(raw, def);
                    out.put(key, DhanSdkMapper.toQuote(nested, def.toInstrument()));
                } catch (RuntimeException ex) {
                    log.warn("Failed to extract quote for {}:{}: {}", def.exchangeSegment(), def.securityId(), ex.getMessage());
                }
            }
            return Map.copyOf(out);
        });
    }

    @Override
    public Map<InstrumentKey, Quote> getOhlcBatch(Collection<InstrumentKey> instrumentKeys) {
        return getQuoteBatch(instrumentKeys);
    }

    private Map<String, List<Integer>> groupBySegment(Collection<InstrumentKey> instrumentKeys) {
        return instrumentKeys.stream()
                .collect(Collectors.groupingBy(
                        key -> resolveDef(key).exchangeSegment().name(),
                        Collectors.mapping(
                                key -> Integer.parseInt(resolveDef(key).securityId()),
                                Collectors.toList()
                        )
                ));
    }

    /**
     * Dhan quote/ltp response shape can vary by segment keys (e.g. IDX_I vs NSE_IDX)
     * and sometimes returns security maps directly at the root. This method resolves
     * the payload robustly without assuming one fixed envelope.
     */
    @SuppressWarnings("unchecked")
    private DhanSdkResponse<?> extractQuotePayload(DhanSdkResponse<?> envelope, DhanInstrumentDefinition definition) {
        String segment = definition.exchangeSegment().name();
        String securityId = definition.securityId();

        try {
            return envelope.nestedValue(segment, securityId);
        } catch (RuntimeException ignored) {
            // fallback path below
        }

        if (!(envelope.raw() instanceof Map<?, ?> root)) {
            throw new IllegalStateException("Unexpected Dhan quote payload type: " + envelope.className());
        }

        Optional<Object> direct = valueBySecurityId((Map<?, ?>) root, securityId);
        if (direct.isPresent()) {
            return new DhanSdkResponse<>(direct.get());
        }

        for (Object value : root.values()) {
            if (value instanceof Map<?, ?> nested) {
                Optional<Object> nestedMatch = valueBySecurityId((Map<?, ?>) nested, securityId);
                if (nestedMatch.isPresent()) {
                    return new DhanSdkResponse<>(nestedMatch.get());
                }
            }
        }

        throw new IllegalStateException("Missing Dhan quote payload for securityId="
                + securityId + " in segment " + segment);
    }

    private Optional<Object> valueBySecurityId(Map<?, ?> map, String securityId) {
        Object byString = map.get(securityId);
        if (byString != null) {
            return Optional.of(byString);
        }
        try {
            Object byInt = map.get(Integer.parseInt(securityId));
            return Optional.ofNullable(byInt);
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
