package com.tradej.app.service;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.capability.BrokerCapabilityRouter;
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
        OptionsProvider provider = BrokerCapabilityRouter.forConnection(conn)
                .find(OptionsProvider.class)
                .orElse(null);
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
}
