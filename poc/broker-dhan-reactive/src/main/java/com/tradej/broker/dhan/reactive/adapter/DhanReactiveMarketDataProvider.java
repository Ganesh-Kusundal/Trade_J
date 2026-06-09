package com.tradej.broker.dhan.reactive.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.reactive.client.DhanReactiveHttpClient;
import com.tradej.broker.dhan.reactive.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.reactive.instrument.DhanInstrumentResolver;
import com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.reactive.port.ReactiveMarketDataProvider;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Quote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Reactive market data provider for Dhan.
 * 
 * GREEN Phase: Minimal implementation to make tests pass.
 */
public final class DhanReactiveMarketDataProvider implements ReactiveMarketDataProvider {
    
    private static final Logger log = LoggerFactory.getLogger(DhanReactiveMarketDataProvider.class);
    
    private final DhanReactiveHttpClient httpClient;
    private final DhanInstrumentResolver instrumentResolver;
    
    public DhanReactiveMarketDataProvider(
            DhanReactiveHttpClient httpClient,
            DhanInstrumentResolver instrumentResolver
    ) {
        this.httpClient = httpClient;
        this.instrumentResolver = instrumentResolver;
    }
    
    @Override
    public Mono<Long> getLtpPaisa(InstrumentKey instrumentKey) {
        DhanInstrumentDefinition definition = instrumentResolver.resolve(instrumentKey);
        String url = buildLtpUrl(definition);
        
        return httpClient.getJson(url)
            .map(response -> {
                String priceStr = response.get("last_price").asText();
                // Convert to paise (multiply by 100, round to avoid floating point issues)
                return Math.round(Double.parseDouble(priceStr) * 100);
            })
            .doOnError(ex -> log.error("Failed to fetch LTP for {}: {}", instrumentKey, ex.getMessage()));
    }
    
    @Override
    public Mono<Quote> getQuote(InstrumentKey instrumentKey) {
        DhanInstrumentDefinition definition = instrumentResolver.resolve(instrumentKey);
        String url = buildQuoteUrl(definition);
        
        return httpClient.getJson(url)
            .map(response -> {
                long ltp = Math.round(Double.parseDouble(response.get("last_price").asText()) * 100);
                long open = Math.round(Double.parseDouble(response.get("open").asText()) * 100);
                long high = Math.round(Double.parseDouble(response.get("high").asText()) * 100);
                long low = Math.round(Double.parseDouble(response.get("low").asText()) * 100);
                long volume = response.has("volume") ? response.get("volume").asLong() : 0;
                
                return new Quote(
                    definition.toInstrument(),
                    ltp, open, high, low, ltp, volume,
                    0L, 0L, 0L,
                    System.currentTimeMillis()
                );
            });
    }
    
    @Override
    public Mono<MarketDepth> getDepth(InstrumentKey instrumentKey) {
        // TODO: Implement market depth
        return Mono.error(new UnsupportedOperationException("Market depth not yet implemented"));
    }
    
    @Override
    public Flux<Candle> getCandles(CandleHistoryRequest request) {
        DhanInstrumentDefinition definition = instrumentResolver.resolve(request.instrument());
        String url = buildHistoricalUrl(request, definition);
        
        return httpClient.getJsonStream(url)
            .flatMapIterable(response -> parseCandles(response, request));
    }
    
    @Override
    public Mono<Map<InstrumentKey, Long>> getLtpBatch(Collection<InstrumentKey> instrumentKeys) {
        if (instrumentKeys == null || instrumentKeys.isEmpty()) {
            return Mono.just(Map.of());
        }
        
        ObjectNode payload = buildBatchPayload(instrumentKeys);
        
        return httpClient.postJson(buildBatchLtpUrl(), payload)
            .map(response -> parseBatchLtpResponse(response, instrumentKeys));
    }
    
    @Override
    public Mono<Map<InstrumentKey, Quote>> getQuoteBatch(Collection<InstrumentKey> instrumentKeys) {
        // TODO: Implement batch quote
        return Mono.error(new UnsupportedOperationException("Batch quote not yet implemented"));
    }
    
    private String buildLtpUrl(DhanInstrumentDefinition definition) {
        return "/marketfeed/ltp";
    }
    
    private String buildQuoteUrl(DhanInstrumentDefinition definition) {
        return "/marketfeed/quote";
    }
    
    private String buildHistoricalUrl(CandleHistoryRequest request, DhanInstrumentDefinition definition) {
        return "/charts/intraday";
    }
    
    private String buildBatchLtpUrl() {
        return "/marketfeed/ltp";
    }
    
    private ObjectNode buildBatchPayload(Collection<InstrumentKey> keys) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        // Group by segment and build payload
        // Simplified for now
        return payload;
    }
    
    private Map<InstrumentKey, Long> parseBatchLtpResponse(
            DhanJsonResponse response, 
            Collection<InstrumentKey> keys
    ) {
        Map<InstrumentKey, Long> result = new HashMap<>();
        
        // Build a map from security ID to instrument key using resolver
        Map<String, InstrumentKey> securityIdToKey = new HashMap<>();
        for (InstrumentKey key : keys) {
            DhanInstrumentDefinition def = instrumentResolver.resolve(key);
            if (def != null) {
                securityIdToKey.put(def.securityId(), key);
            }
        }
        
        // Parse response structure: {"NSE_EQ": {"2885": {"last_price": "2456.70"}}}
        JsonNode rootNode = response.raw();
        rootNode.fields().forEachRemaining(entry -> {
            String segment = entry.getKey();
            if (entry.getValue().isObject()) {
                entry.getValue().fields().forEachRemaining(secEntry -> {
                    String securityId = secEntry.getKey();
                    if (secEntry.getValue().has("last_price")) {
                        long price = Math.round(Double.parseDouble(
                            secEntry.getValue().get("last_price").asText()
                        ) * 100);
                        
                        // Find matching instrument key by security ID
                        InstrumentKey key = securityIdToKey.get(securityId);
                        if (key != null) {
                            result.put(key, price);
                        }
                    }
                });
            }
        });
        
        return Map.copyOf(result);
    }
    
    private java.util.List<Candle> parseCandles(DhanJsonResponse response, CandleHistoryRequest request) {
        java.util.List<Candle> candles = new java.util.ArrayList<>();
        
        // Parse JSON array of candles
        JsonNode rootNode = response.raw();
        if (rootNode.isArray()) {
            rootNode.forEach(candleNode -> {
                if (candleNode.isObject()) {
                    long open = Math.round(Double.parseDouble(candleNode.get("open").asText()) * 100);
                    long high = Math.round(Double.parseDouble(candleNode.get("high").asText()) * 100);
                    long low = Math.round(Double.parseDouble(candleNode.get("low").asText()) * 100);
                    long close = Math.round(Double.parseDouble(candleNode.get("close").asText()) * 100);
                    long volume = candleNode.has("volume") ? candleNode.get("volume").asLong() : 0;
                    
                    candles.add(new Candle(
                        request.instrument().symbol(),
                        request.interval(),
                        request.fromDate().atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli(),
                        request.toDate().atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli(),
                        open, high, low, close, volume,
                        true // closed
                    ));
                }
            });
        }
        
        return candles;
    }
}
