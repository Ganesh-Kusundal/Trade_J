package com.tradej.broker.dhan.reactive.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.tradej.broker.dhan.reactive.client.DhanReactiveHttpClient;
import com.tradej.broker.dhan.reactive.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.reactive.instrument.DhanInstrumentResolver;
import com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Set;

/**
 * Reactive futures data provider.
 * Uses Dhan's rolling option/futures historical endpoint.
 */
public class DhanReactiveFuturesProvider {
    
    private static final Set<String> INDEX_UNDERLYINGS = Set.of(
        "NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY", "BANKEX", "SENSEX"
    );
    
    private final DhanReactiveHttpClient httpClient;
    private final DhanInstrumentResolver instrumentResolver;
    
    public DhanReactiveFuturesProvider(
            DhanReactiveHttpClient httpClient,
            DhanInstrumentResolver instrumentResolver
    ) {
        this.httpClient = httpClient;
        this.instrumentResolver = instrumentResolver;
    }
    
    /**
     * Fetch historical futures data (continuous contract).
     * Returns OHLCV + OI data for futures.
     */
    public Flux<FuturesBar> getFuturesHistory(
            InstrumentKey underlyingKey,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        DhanInstrumentDefinition underlying = instrumentResolver.resolve(underlyingKey);
        ObjectNode payload = buildFuturesPayload(underlying, fromDate, toDate);
        
        return httpClient.postJson("/charts/rollingoption", payload)
            .flatMapMany(this::parseFuturesResponse);
    }
    
    private ObjectNode buildFuturesPayload(
            DhanInstrumentDefinition underlying,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode payload = factory.objectNode();
        
        // Futures use FNO segment
        payload.put("exchangeSegment", toWireSegment(ExchangeSegment.fromCode(underlying.exchangeSegment())));
        payload.put("instrument", futuresInstrumentType(underlying));
        payload.put("securityId", underlying.securityId());
        
        // For futures, we use near month contract
        payload.put("optionType", "FUT"); // Futures, not options
        payload.put("strikeType", "FUT");
        
        // Required data fields
        var requiredData = payload.putArray("requiredData");
        requiredData.add("open");
        requiredData.add("high");
        requiredData.add("low");
        requiredData.add("close");
        requiredData.add("volume");
        requiredData.add("oi");
        requiredData.add("spot");
        requiredData.add("strike");
        
        payload.put("fromDate", fromDate.toString());
        payload.put("toDate", toDate.toString());
        
        return payload;
    }
    
    private String toWireSegment(ExchangeSegment segment) {
        return switch (segment) {
            case NSE_EQ, IDX_I, NSE_FNO -> "NSE_FNO";
            case BSE_EQ, BSE_FNO -> "BSE_FNO";
            case MCX_COMM -> "MCX";
            case NSE_CURRENCY, BSE_CURRENCY -> "CURRENCY";
            default -> segment.name();
        };
    }
    
    private String futuresInstrumentType(DhanInstrumentDefinition underlying) {
        String symbol = underlying.tradingSymbol() == null ? "" : underlying.tradingSymbol().trim().toUpperCase();
        
        // Index futures vs stock futures
        if ("IDX_I".equals(underlying.exchangeSegment()) || INDEX_UNDERLYINGS.contains(symbol)) {
            return "FUTIDX"; // Index futures
        }
        return "FUTSTK"; // Stock futures
    }
    
    private Flux<FuturesBar> parseFuturesResponse(DhanJsonResponse response) {
        try {
            var dataArray = response.get("data");
            if (dataArray == null || dataArray.isNull() || !dataArray.isArray()) {
                return Flux.empty();
            }
            
            return Flux.fromIterable(dataArray)
                .map(this::parseFuturesBar)
                .filter(bar -> bar != null);
        } catch (Exception e) {
            return Flux.error(new RuntimeException("Failed to parse futures data", e));
        }
    }
    
    private FuturesBar parseFuturesBar(JsonNode barNode) {
        try {
            String timestamp = barNode.get("timestamp").asText();
            double open = barNode.get("open").asDouble();
            double high = barNode.get("high").asDouble();
            double low = barNode.get("low").asDouble();
            double close = barNode.get("close").asDouble();
            long volume = barNode.has("volume") ? barNode.get("volume").asLong() : 0L;
            long oi = barNode.has("oi") ? barNode.get("oi").asLong() : 0L;
            double spot = barNode.has("spot") ? barNode.get("spot").asDouble() : 0.0;
            
            return new FuturesBar(
                timestamp,
                Math.round(open * 100),
                Math.round(high * 100),
                Math.round(low * 100),
                Math.round(close * 100),
                volume,
                oi,
                spot
            );
        } catch (Exception e) {
            return null; // Skip malformed bars
        }
    }
    
    public record FuturesBar(
        String timestamp,
        long openPaisa,
        long highPaisa,
        long lowPaisa,
        long closePaisa,
        long volume,
        long openInterest,
        double spotPrice
    ) {}
}
