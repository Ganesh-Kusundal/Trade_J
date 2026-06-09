package com.tradej.app.api;

import com.tradej.app.service.MarketDataApplicationService;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
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

@RestController
@RequestMapping("/api/v1/market")
public class MarketDataController {

    private final MarketDataApplicationService marketDataService;
    private final InstrumentResolver instrumentResolver;

    public MarketDataController(
            MarketDataApplicationService marketDataService,
            InstrumentResolver instrumentResolver
    ) {
        this.marketDataService = marketDataService;
        this.instrumentResolver = instrumentResolver;
    }

    @GetMapping(value = "/ltp", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> ltp(
            @RequestParam String symbol,
            @RequestParam ExchangeSegment exchangeSegment
    ) {
        Instrument instrument = instrumentResolver.resolveNormalized(symbol, exchangeSegment);
        long ltpPaisa = marketDataService.getLtpPaisa(instrument.key());
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
        InstrumentKey key = InstrumentKey.of(symbol, exchangeSegment);
        List<Candle> candles = marketDataService.queryCandles(key, interval, from, to, source);
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

    @GetMapping(value = "/capabilities", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> capabilities() {
        return ResponseEntity.ok(marketDataService.capabilities());
    }

    @GetMapping(value = "/intervals", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> supportedIntervals(@RequestParam(required = false) String interval) {
        return ResponseEntity.ok(marketDataService.supportedIntervals(interval));
    }
}
