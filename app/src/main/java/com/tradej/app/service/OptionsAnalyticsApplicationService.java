package com.tradej.app.service;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.core.domain.model.VolatilitySurface;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.options.calculator.MaxPainCalculator;
import com.tradej.options.greeks.OptionsAnalyticsCache;
import com.tradej.options.surface.VolatilitySurfaceBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Application service for options analytics operations.
 * Encapsulates broker options provider access and analytics computation.
 */
@Service
public class OptionsAnalyticsApplicationService {

    private final ObjectProvider<OptionsAnalyticsCache> cache;
    private final ObjectProvider<VolatilitySurfaceBuilder> surfaceBuilder;
    private final ObjectProvider<IBrokerConnection> brokerConnection;

    public OptionsAnalyticsApplicationService(
            ObjectProvider<OptionsAnalyticsCache> cache,
            ObjectProvider<VolatilitySurfaceBuilder> surfaceBuilder,
            ObjectProvider<IBrokerConnection> brokerConnection
    ) {
        this.cache = cache;
        this.surfaceBuilder = surfaceBuilder;
        this.brokerConnection = brokerConnection;
    }

    public Map<String, Object> getVolatilitySurface(String underlying, String segment, LocalDate expiry) {
        IBrokerConnection conn = brokerConnection.getIfAvailable();
        VolatilitySurfaceBuilder builder = surfaceBuilder.getIfAvailable();
        if (conn == null || builder == null) {
            return Map.of("error", "Options analytics not enabled");
        }
        OptionsProvider provider = conn.getCapability(OptionsProvider.class).orElse(null);
        if (provider == null) {
            return Map.of("error", "Options provider unavailable");
        }
        ExchangeSegment exchangeSegment = ExchangeSegment.valueOf(segment);
        LocalDate expiryDate = expiry != null ? expiry : provider.getExpiries(underlying, exchangeSegment).getFirst();
        var chain = provider.getOptionChain(underlying, exchangeSegment, expiryDate);
        VolatilitySurface surface = builder.build(chain);
        MaxPainCalculator.MaxPainResult maxPain = MaxPainCalculator.compute(chain);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("underlying", underlying);
        body.put("expiry", expiryDate.toString());
        body.put("spotPricePaisa", surface.spotPricePaisa());
        body.put("ivByStrikePaisa", surface.ivByStrikePaisa());
        body.put("maxPainStrikePaisa", maxPain.strikePaisa());
        body.put("maxPainTotalPaisa", maxPain.totalPainPaisa());
        return body;
    }

    /**
     * Returns the full option chain (strikes, quotes, greeks) for the
     * given underlying and expiry, with summary analytics (max-pain,
     * put-call ratio) computed from the chain.
     */
    public Map<String, Object> getOptionChain(String underlying, String segment, LocalDate expiry, Integer depth) {
        IBrokerConnection conn = brokerConnection.getIfAvailable();
        if (conn == null) {
            return Map.of("error", "Broker not connected");
        }
        OptionsProvider provider = conn.getCapability(OptionsProvider.class).orElse(null);
        if (provider == null) {
            return Map.of("error", "Options provider unavailable");
        }
        ExchangeSegment exchangeSegment = ExchangeSegment.valueOf(segment);
        var expiries = provider.getExpiries(underlying, exchangeSegment);
        if (expiries == null || expiries.isEmpty()) {
            return Map.of("error", "No expiries for " + underlying);
        }
        LocalDate expiryDate = expiry != null ? expiry : expiries.getFirst();
        var chain = provider.getOptionChain(underlying, exchangeSegment, expiryDate);
        MaxPainCalculator.MaxPainResult maxPain = MaxPainCalculator.compute(chain);

        long totalCallOi = chain.strikes().stream().mapToLong(s -> s.call() == null ? 0 : s.call().openInterest()).sum();
        long totalPutOi = chain.strikes().stream().mapToLong(s -> s.put() == null ? 0 : s.put().openInterest()).sum();
        double pcr = totalCallOi > 0 ? (double) totalPutOi / (double) totalCallOi : 0.0;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("underlying", underlying);
        body.put("segment", segment);
        body.put("expiry", expiryDate.toString());
        body.put("expiries", expiries.stream().map(LocalDate::toString).toList());
        body.put("spotPricePaisa", chain.spotPricePaisa());
        body.put("maxPainStrikePaisa", maxPain.strikePaisa());
        body.put("maxPainTotalPaisa", maxPain.totalPainPaisa());
        body.put("putCallRatio", pcr);
        body.put("totalCallOi", totalCallOi);
        body.put("totalPutOi", totalPutOi);
        body.put("strikeCount", chain.strikes().size());
        if (depth != null) {
            body.put("strikes", chain.strikes().stream().limit(depth).map(s -> Map.of(
                "strikePaisa", s.strikePricePaisa(),
                "call", quoteMap(s.call()),
                "put", quoteMap(s.put())
            )).toList());
        } else {
            body.put("strikes", chain.strikes().stream().map(s -> Map.of(
                "strikePaisa", s.strikePricePaisa(),
                "call", quoteMap(s.call()),
                "put", quoteMap(s.put())
            )).toList());
        }
        return body;
    }

    private static Map<String, Object> quoteMap(com.tradej.core.domain.model.OptionQuote q) {
        if (q == null) return Map.of("present", false);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("present", true);
        m.put("ltpPaisa", q.ltpPaisa());
        m.put("openInterest", q.openInterest());
        m.put("volume", q.volume());
        m.put("bestBidPricePaisa", q.bestBidPricePaisa());
        m.put("bestBidQuantity", q.bestBidQuantity());
        m.put("bestAskPricePaisa", q.bestAskPricePaisa());
        m.put("bestAskQuantity", q.bestAskQuantity());
        if (q.greeks() != null) {
            m.put("delta", q.greeks().delta());
            m.put("theta", q.greeks().theta());
            m.put("gamma", q.greeks().gamma());
            m.put("vega", q.greeks().vega());
            m.put("iv", q.greeks().impliedVolatility());
        }
        return m;
    }
}
