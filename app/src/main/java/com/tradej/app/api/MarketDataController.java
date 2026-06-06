package com.tradej.app.api;

import com.tradej.historical.service.BrokerHistoricalQueryService;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.port.HistoricalAnalyticsService;
import com.tradej.core.domain.value.ExchangeSegment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/market")
public class MarketDataController {

    private final Optional<BrokerHistoricalQueryService> brokerHistoricalQueryService;
    private final Optional<HistoricalAnalyticsService> historicalAnalyticsService;
    private final InstrumentResolver instrumentResolver;

    public MarketDataController(
            InstrumentResolver instrumentResolver,
            @Autowired(required = false) BrokerHistoricalQueryService brokerHistoricalQueryService,
            @Autowired(required = false) HistoricalAnalyticsService historicalAnalyticsService
    ) {
        this.instrumentResolver = instrumentResolver;
        this.brokerHistoricalQueryService = Optional.ofNullable(brokerHistoricalQueryService);
        this.historicalAnalyticsService = Optional.ofNullable(historicalAnalyticsService);
    }

    @GetMapping(value = "/ltp", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> ltp(
            @RequestParam String symbol,
            @RequestParam ExchangeSegment exchangeSegment
    ) {
        BrokerHistoricalQueryService broker = brokerHistoricalQueryService.orElseThrow(() ->
                new IllegalStateException("Broker market data is not configured"));
        Instrument instrument = instrumentResolver.resolveNormalized(symbol, exchangeSegment);
        long ltpPaisa = broker.getLtpPaisa(instrument.key());
        return ResponseEntity.ok(Map.of(
                "symbol", instrument.canonicalSymbol(),
                "canonicalSymbol", instrument.canonicalSymbol(),
                "exchangeSegment", exchangeSegment.name(),
                "ltpPaisa", ltpPaisa
        ));
    }

    @GetMapping(value = "/historical/candles", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> historicalCandles(
            @RequestParam String symbol,
            @RequestParam ExchangeSegment exchangeSegment,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(defaultValue = "broker") String source
    ) {
        List<Candle> candles = "parquet".equalsIgnoreCase(source)
                ? loadParquetCandles(symbol, exchangeSegment, interval, from, to)
                : loadBrokerCandles(symbol, exchangeSegment, interval, from, to);
        Instrument instrument = instrumentResolver.resolveNormalized(symbol, exchangeSegment);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("symbol", instrument.canonicalSymbol());
        body.put("canonicalSymbol", instrument.canonicalSymbol());
        body.put("exchangeSegment", exchangeSegment.name());
        body.put("interval", interval);
        body.put("from", from.toString());
        body.put("to", to.toString());
        body.put("source", source.toLowerCase());
        body.put("count", candles.size());
        body.put("candles", candles.stream().map(this::candlePayload).toList());
        return ResponseEntity.ok(body);
    }

    private List<Candle> loadParquetCandles(
            String symbol,
            ExchangeSegment exchangeSegment,
            String interval,
            LocalDate from,
            LocalDate to
    ) {
        HistoricalAnalyticsService analytics = historicalAnalyticsService.orElseThrow(() ->
                new IllegalStateException("Historical analytics service is not configured"));
        return analytics.queryEquityCandles(new CandleHistoryRequest(
                InstrumentKey.of(symbol, exchangeSegment),
                interval,
                from,
                to
        ));
    }

    private List<Candle> loadBrokerCandles(
            String symbol,
            ExchangeSegment exchangeSegment,
            String interval,
            LocalDate from,
            LocalDate to
    ) {
        BrokerHistoricalQueryService broker = brokerHistoricalQueryService.orElseThrow(() ->
                new IllegalStateException("Broker market data is not configured"));
        Instrument instrument = instrumentResolver.resolveNormalized(symbol, exchangeSegment);
        return broker.getCandlesChunked(new CandleHistoryRequest(instrument.key(), interval, from, to));
    }

    private Map<String, Object> candlePayload(Candle candle) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("symbol", candle.symbol());
        map.put("canonicalSymbol", candle.symbol());
        map.put("startTimeMs", candle.startTimeMs());
        map.put("endTimeMs", candle.endTimeMs());
        map.put("openPaisa", candle.openPaisa());
        map.put("highPaisa", candle.highPaisa());
        map.put("lowPaisa", candle.lowPaisa());
        map.put("closePaisa", candle.closePaisa());
        map.put("volume", candle.volume());
        return map;
    }
}
