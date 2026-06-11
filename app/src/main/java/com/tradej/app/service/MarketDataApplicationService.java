package com.tradej.app.service;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.brokergateway.MarketGateway;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.port.HistoricalAnalyticsService;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.historical.service.BrokerHistoricalQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MarketDataApplicationService {

    private final MarketGateway marketGateway;
    private final Optional<BrokerHistoricalQueryService> brokerHistoricalQueryService;
    private final Optional<HistoricalAnalyticsService> historicalAnalyticsService;
    private final Optional<MarketDataProvider> marketDataProvider;

    public MarketDataApplicationService(
            MarketGateway marketGateway,
            Optional<BrokerHistoricalQueryService> brokerHistoricalQueryService,
            Optional<HistoricalAnalyticsService> historicalAnalyticsService,
            Optional<MarketDataProvider> marketDataProvider
    ) {
        this.marketGateway = marketGateway;
        this.brokerHistoricalQueryService = brokerHistoricalQueryService;
        this.historicalAnalyticsService = historicalAnalyticsService;
        this.marketDataProvider = marketDataProvider;
    }

    public long getLtpPaisa(InstrumentKey instrumentKey) {
        return brokerHistoricalQueryService
                .map(service -> {
                    try {
                        return service.getLtpPaisa(instrumentKey);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElseGet(() -> marketDataProvider.orElseThrow(() ->
                        new IllegalStateException("No market data source available"))
                        .getLtpPaisa(instrumentKey));
    }

    public List<Candle> queryCandles(
            InstrumentKey instrumentKey,
            String interval,
            LocalDate from,
            LocalDate to,
            String source
    ) {
        CandleHistoryRequest request = new CandleHistoryRequest(instrumentKey, interval, from, to);
        if ("parquet".equalsIgnoreCase(source)) {
            HistoricalAnalyticsService analytics = historicalAnalyticsService.orElseThrow(() ->
                    new IllegalStateException("Historical analytics service is not configured"));
            return analytics.queryEquityCandles(request);
        }
        return brokerHistoricalQueryService
                .map(service -> {
                    try {
                        return service.getCandlesChunked(request);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElseGet(() -> marketDataProvider.orElseThrow(() ->
                        new IllegalStateException("No market data source available"))
                        .getCandles(request));
    }

    public Map<String, Object> supportedIntervals(String interval) {
        Map<String, Object> body = new LinkedHashMap<>();
        var dhan = com.tradej.broker.api.model.HistoricalDataCapabilities.dhanDefaults();
        var upstox = com.tradej.broker.api.model.HistoricalDataCapabilities.upstoxDefaults();
        var icici = com.tradej.broker.api.model.HistoricalDataCapabilities.iciciDefaults();
        if (interval != null && !interval.isBlank()) {
            String normalized = interval.trim().toLowerCase();
            body.put("interval", normalized);
            body.put("dhan", dhan.supportedIntervals().contains(normalized));
            body.put("upstox", upstox.supportedIntervals().contains(normalized));
            body.put("icici", icici.supportedIntervals().contains(normalized));
        } else {
            body.put("dhan", dhan.supportedIntervals());
            body.put("upstox", upstox.supportedIntervals());
            body.put("icici", icici.supportedIntervals());
        }
        return body;
    }

    public GatewayResult<?> capabilities() {
        return marketGateway.capabilities();
    }
}
