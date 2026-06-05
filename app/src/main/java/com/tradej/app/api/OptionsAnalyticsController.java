package com.tradej.app.api;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.core.domain.model.VolatilitySurface;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.options.calculator.MaxPainCalculator;
import com.tradej.options.greeks.OptionsAnalyticsCache;
import com.tradej.options.surface.VolatilitySurfaceBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/options")
public class OptionsAnalyticsController {

    private final ObjectProvider<OptionsAnalyticsCache> cache;
    private final ObjectProvider<VolatilitySurfaceBuilder> surfaceBuilder;
    private final ObjectProvider<IBrokerConnection> brokerConnection;

    public OptionsAnalyticsController(
            ObjectProvider<OptionsAnalyticsCache> cache,
            ObjectProvider<VolatilitySurfaceBuilder> surfaceBuilder,
            ObjectProvider<IBrokerConnection> brokerConnection
    ) {
        this.cache = cache;
        this.surfaceBuilder = surfaceBuilder;
        this.brokerConnection = brokerConnection;
    }

    @GetMapping("/volatility-surface")
    public ResponseEntity<Map<String, Object>> volatilitySurface(
            @RequestParam String underlying,
            @RequestParam(defaultValue = "NSE_FNO") String segment,
            @RequestParam(required = false) LocalDate expiry
    ) {
        IBrokerConnection conn = brokerConnection.getIfAvailable();
        VolatilitySurfaceBuilder builder = surfaceBuilder.getIfAvailable();
        if (conn == null || builder == null) {
            return ResponseEntity.status(503).body(Map.of("error", "Options analytics not enabled"));
        }
        OptionsProvider provider = conn.getCapability(OptionsProvider.class).orElse(null);
        if (provider == null) {
            return ResponseEntity.status(503).body(Map.of("error", "Options provider unavailable"));
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
        return ResponseEntity.ok(body);
    }
}
