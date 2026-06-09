package com.tradej.broker.dhan.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.historical.DhanHistoricalDataClient;
import com.tradej.broker.dhan.historical.DhanHistoricalDataMapper;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.instrument.DhanSegmentMapper;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.mapper.DhanJsonMapper;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Quote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class DhanMarketDataProvider implements MarketDataProvider {
    private static final Logger log = LoggerFactory.getLogger(DhanMarketDataProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DhanAdapterContext context;
    private final DhanHistoricalDataClient historicalDataClient;
    private final DhanHistoricalDataMapper historicalDataMapper;
    private final DhanAuthenticatedHttpClient httpClient;
    private final DhanApiUrlResolver apiUrlResolver;

    public DhanMarketDataProvider(
            DhanAdapterContext context,
            DhanHistoricalDataClient historicalDataClient,
            DhanHistoricalDataMapper historicalDataMapper,
            DhanAuthenticatedHttpClient httpClient,
            DhanApiUrlResolver apiUrlResolver
    ) {
        this.context = context;
        this.historicalDataClient = historicalDataClient;
        this.historicalDataMapper = historicalDataMapper;
        this.httpClient = httpClient;
        this.apiUrlResolver = apiUrlResolver;
    }

    @Override
    public long getLtpPaisa(InstrumentKey instrumentKey) {
        DhanInstrumentDefinition definition = context.resolveDef(instrumentKey);
        return context.execute(ApiCategory.QUOTE, "quote-ltp", () -> {
            DhanJsonResponse response = fetchMarketFeed(definition, apiUrlResolver.marketFeedLtpUrl());
            DhanJsonResponse payload = extractQuotePayload(response, definition);
            return payload.decimalPrice("last_price", "lastPrice", "ltp");
        });
    }

    @Override
    public Quote getQuote(InstrumentKey instrumentKey) {
        DhanInstrumentDefinition definition = context.resolveDef(instrumentKey);
        return context.execute(ApiCategory.QUOTE, "quote-snapshot", () -> {
            DhanJsonResponse response = fetchMarketFeed(definition, apiUrlResolver.marketFeedQuoteUrl());
            DhanJsonResponse payload = extractQuotePayload(response, definition);
            return DhanJsonMapper.toQuote(payload, definition.toInstrument());
        });
    }

    @Override
    public MarketDepth getDepth(InstrumentKey instrumentKey) {
        DhanInstrumentDefinition definition = context.resolveDef(instrumentKey);
        return context.execute(ApiCategory.QUOTE, "quote-depth", () -> {
            DhanJsonResponse response = fetchMarketFeed(definition, apiUrlResolver.marketFeedQuoteUrl());
            DhanJsonResponse payload = extractQuotePayload(response, definition);
            return DhanJsonMapper.toDepth(payload, definition.toInstrument());
        });
    }

    @Override
    public Quote getOhlcSnapshot(InstrumentKey instrumentKey) {
        return getQuote(instrumentKey);
    }

    @Override
    public List<Candle> getCandles(CandleHistoryRequest request) {
        validateInterval(request.interval());
        DhanInstrumentDefinition definition = context.resolveDef(request.instrument());
        Instrument instrument = definition.toInstrument();
        List<Candle> merged = new ArrayList<>();
        for (var payload : historicalDataClient.fetchRange(request, definition)) {
            merged.addAll(historicalDataMapper.toCandles(payload, instrument, request.interval()));
        }
        return mergeCandles(merged);
    }

    @Override
    public com.tradej.broker.api.model.HistoricalDataCapabilities capabilities() {
        return com.tradej.broker.api.model.HistoricalDataCapabilities.dhanDefaults();
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
        return context.execute(ApiCategory.QUOTE, "quote-ltp-batch", () -> {
            Map<InstrumentKey, DhanInstrumentDefinition> defs = new HashMap<>();
            Map<String, List<String>> segmentSecurityIds = new HashMap<>();
            for (InstrumentKey key : instrumentKeys) {
                DhanInstrumentDefinition def = context.resolveDef(key);
                defs.put(key, def);
                segmentSecurityIds
                        .computeIfAbsent(DhanSegmentMapper.toWireValue(def.exchangeSegment()), ignored -> new ArrayList<>())
                        .add(def.securityId());
            }
            ObjectNode body = buildMarketFeedRequestBody(segmentSecurityIds);
            DhanJsonResponse response = httpClient.postJson(apiUrlResolver.marketFeedLtpUrl(), body);
            Map<InstrumentKey, Long> out = new HashMap<>();
            for (InstrumentKey key : instrumentKeys) {
                DhanInstrumentDefinition def = defs.get(key);
                try {
                     DhanJsonResponse nested = extractQuotePayload(response, def);
                     out.put(key, nested.decimalPrice("last_price", "lastPrice", "ltp"));
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
        return context.execute(ApiCategory.QUOTE, "quote-snapshot-batch", () -> {
            Map<InstrumentKey, DhanInstrumentDefinition> defs = new HashMap<>();
            Map<String, List<String>> segmentSecurityIds = new HashMap<>();
            for (InstrumentKey key : instrumentKeys) {
                DhanInstrumentDefinition def = context.resolveDef(key);
                defs.put(key, def);
                segmentSecurityIds
                        .computeIfAbsent(DhanSegmentMapper.toWireValue(def.exchangeSegment()), ignored -> new ArrayList<>())
                        .add(def.securityId());
            }
            ObjectNode body = buildMarketFeedRequestBody(segmentSecurityIds);
            DhanJsonResponse response = httpClient.postJson(apiUrlResolver.marketFeedQuoteUrl(), body);
            Map<InstrumentKey, Quote> out = new HashMap<>();
            for (InstrumentKey key : instrumentKeys) {
                DhanInstrumentDefinition def = defs.get(key);
                try {
                     DhanJsonResponse nested = extractQuotePayload(response, def);
                     out.put(key, DhanJsonMapper.toQuote(nested, def.toInstrument()));
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

    private DhanJsonResponse fetchMarketFeed(DhanInstrumentDefinition definition, String url) {
        ObjectNode body = MAPPER.createObjectNode();
        body.set(DhanSegmentMapper.toWireValue(definition.exchangeSegment()), securityIdArray(definition.securityId()));
        return httpClient.postJson(url, body);
    }

    private static ObjectNode buildMarketFeedRequestBody(Map<String, List<String>> segmentSecurityIds) {
        ObjectNode body = MAPPER.createObjectNode();
        segmentSecurityIds.forEach((segment, securityIds) -> body.set(segment, securityIdArray(securityIds)));
        return body;
    }

    private static ArrayNode securityIdArray(String securityId) {
        ArrayNode array = MAPPER.createArrayNode();
        array.add(parseSecurityId(securityId));
        return array;
    }

    private static ArrayNode securityIdArray(List<String> securityIds) {
        ArrayNode array = MAPPER.createArrayNode();
        for (String securityId : securityIds) {
            array.add(parseSecurityId(securityId));
        }
        return array;
    }

    private static long parseSecurityId(String securityId) {
        if (securityId == null || securityId.isBlank()) {
            throw new IllegalArgumentException("Dhan securityId must not be blank");
        }
        try {
            return Long.parseLong(securityId.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid Dhan securityId: " + securityId, ex);
        }
    }

    /**
     * Dhan quote/ltp response shape can vary by segment keys (e.g. IDX_I vs NSE_IDX)
     * and sometimes returns security maps directly at the root. This method resolves
     * the payload robustly without assuming one fixed envelope.
     */
    private DhanJsonResponse extractQuotePayload(DhanJsonResponse envelope, DhanInstrumentDefinition definition) {
        String segment = DhanSegmentMapper.toWireValue(definition.exchangeSegment());
        String securityId = definition.securityId();

        if (envelope.has("data") && envelope.path("data").isObject()) {
            DhanJsonResponse fromData = segmentSecurityPayload(envelope.path("data").path(segment), securityId);
            if (fromData != null) {
                return fromData;
            }
        }

        DhanJsonResponse fromRoot = segmentSecurityPayload(envelope.path(segment), securityId);
        if (fromRoot != null) {
            return fromRoot;
        }

        throw new IllegalStateException("Missing Dhan quote payload for securityId="
                + securityId + " in segment " + segment);
    }

    private DhanJsonResponse segmentSecurityPayload(DhanJsonResponse segmentPayload, String securityId) {
        if (!segmentPayload.isObject()) {
            return null;
        }
        if (segmentPayload.has(securityId)) {
            return segmentPayload.path(securityId);
        }
        if (segmentPayload.raw() != null) {
            var byKey = segmentPayload.raw().get(securityId.trim());
            if (byKey != null && !byKey.isMissingNode()) {
                return new DhanJsonResponse(byKey);
            }
        }
        return null;
    }
}
