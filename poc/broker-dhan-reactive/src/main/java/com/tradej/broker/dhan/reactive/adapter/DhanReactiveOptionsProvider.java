package com.tradej.broker.dhan.reactive.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.reactive.client.DhanReactiveHttpClient;
import com.tradej.broker.dhan.reactive.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.reactive.instrument.DhanInstrumentResolver;
import com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reactive options data provider.
 */
public class DhanReactiveOptionsProvider {
    
    private final DhanReactiveHttpClient httpClient;
    private final DhanInstrumentResolver instrumentResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public DhanReactiveOptionsProvider(
            DhanReactiveHttpClient httpClient,
            DhanInstrumentResolver instrumentResolver
    ) {
        this.httpClient = httpClient;
        this.instrumentResolver = instrumentResolver;
    }
    
    /**
     * Fetch option expiry dates for an underlying.
     */
    public Flux<LocalDate> getExpiries(InstrumentKey underlyingKey) {
        DhanInstrumentDefinition underlying = instrumentResolver.resolve(underlyingKey);
        ObjectNode payload = buildUnderlyingPayload(underlying);
        
        return httpClient.postJson("/optionchain/expirylist", payload)
            .flatMapMany(this::parseExpiries);
    }
    
    /**
     * Fetch complete option chain for a specific expiry.
     */
    public Flux<OptionStrike> getOptionChain(InstrumentKey underlyingKey, LocalDate expiry) {
        DhanInstrumentDefinition underlying = instrumentResolver.resolve(underlyingKey);
        ObjectNode payload = buildUnderlyingPayload(underlying);
        payload.put("Expiry", expiry.toString());
        
        return httpClient.postJson("/optionchain", payload)
            .flatMapMany(response -> parseOptionChain(response, underlying.tradingSymbol(), expiry));
    }
    
    private ObjectNode buildUnderlyingPayload(DhanInstrumentDefinition underlying) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("UnderlyingScrip", Integer.parseInt(underlying.securityId()));
        request.put("UnderlyingSeg", toWireSegment(ExchangeSegment.fromCode(underlying.exchangeSegment())));
        return request;
    }
    
    private String toWireSegment(ExchangeSegment segment) {
        return switch (segment) {
            case NSE_EQ -> "NSE_EQ";
            case NSE_FNO -> "NSE_FNO";
            case BSE_EQ -> "BSE_EQ";
            case BSE_FNO -> "BSE_FNO";
            case IDX_I -> "NSE_FNO"; // Options on indices use FNO segment
            case MCX_COMM -> "MCX";
            case NSE_CURRENCY, BSE_CURRENCY -> "CURRENCY";
            default -> segment.name();
        };
    }
    
    private Flux<LocalDate> parseExpiries(DhanJsonResponse response) {
        try {
            var expiriesArray = response.get("expiryList");
            if (expiriesArray == null || expiriesArray.isNull()) {
                return Flux.empty();
            }
            
            List<LocalDate> expiries = new ArrayList<>();
            for (var item : expiriesArray) {
                String expiryStr = item.asText();
                expiries.add(LocalDate.parse(expiryStr));
            }
            return Flux.fromIterable(expiries);
        } catch (Exception e) {
            return Flux.error(new RuntimeException("Failed to parse expiries", e));
        }
    }
    
    private Flux<OptionStrike> parseOptionChain(DhanJsonResponse response, String underlying, LocalDate expiry) {
        try {
            var optionChainArray = response.get("optionChain");
            if (optionChainArray == null || optionChainArray.isNull()) {
                return Flux.empty();
            }
            
            List<OptionStrike> strikes = new ArrayList<>();
            for (var strikeNode : optionChainArray) {
                OptionStrike strike = parseStrike(strikeNode, underlying, expiry);
                if (strike != null) {
                    strikes.add(strike);
                }
            }
            return Flux.fromIterable(strikes);
        } catch (Exception e) {
            return Flux.error(new RuntimeException("Failed to parse option chain", e));
        }
    }
    
    private OptionStrike parseStrike(JsonNode strikeNode, String underlying, LocalDate expiry) {
        try {
            double strikePrice = strikeNode.get("strikePrice").asDouble();
            long strikePricePaisa = Math.round(strikePrice * 100);
            
            var callNode = strikeNode.get("call");
            var putNode = strikeNode.get("put");
            
            OptionLeg call = parseOptionLeg(callNode, "CE");
            OptionLeg put = parseOptionLeg(putNode, "PE");
            
            return new OptionStrike(
                underlying,
                expiry,
                strikePricePaisa,
                call,
                put
            );
        } catch (Exception e) {
            return null; // Skip malformed strikes
        }
    }
    
    private OptionLeg parseOptionLeg(JsonNode legNode, String optionType) {
        if (legNode == null || legNode.isNull()) {
            return null;
        }
        
        return new OptionLeg(
            optionType,
            legNode.has("ltp") ? legNode.get("ltp").asDouble() : 0.0,
            legNode.has("oi") ? legNode.get("oi").asLong() : 0L,
            legNode.has("volume") ? legNode.get("volume").asLong() : 0L,
            legNode.has("iv") ? legNode.get("iv").asDouble() : null,
            legNode.has("bidPrice") ? legNode.get("bidPrice").asDouble() : null,
            legNode.has("askPrice") ? legNode.get("askPrice").asDouble() : null
        );
    }
    
    public record OptionStrike(
        String underlying,
        LocalDate expiry,
        long strikePricePaisa,
        OptionLeg call,
        OptionLeg put
    ) {}
    
    public record OptionLeg(
        String optionType,
        double ltp,
        long openInterest,
        long volume,
        Double impliedVolatility,
        Double bidPrice,
        Double askPrice
    ) {}
}
