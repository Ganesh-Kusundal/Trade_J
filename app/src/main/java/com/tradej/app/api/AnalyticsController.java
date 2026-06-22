package com.tradej.app.api;

import com.tradej.app.config.TradingProperties;
import com.tradej.core.domain.instrument.StandardInstrumentIdentityService;
import com.tradej.core.domain.model.AnalyticsCatalogSnapshot;
import com.tradej.core.domain.model.AnalyticsQueryResult;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.RollingOptionBar;
import com.tradej.core.domain.model.RollingOptionSeriesRequest;
import com.tradej.core.domain.model.UniverseEntry;
import com.tradej.core.domain.port.HistoricalAnalyticsService;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final HistoricalAnalyticsService analyticsService;
    private final TradingProperties tradingProperties;

    public AnalyticsController(
            HistoricalAnalyticsService analyticsService,
            TradingProperties tradingProperties
    ) {
        this.analyticsService = analyticsService;
        this.tradingProperties = tradingProperties;
    }

    @GetMapping(value = "/catalog", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AnalyticsCatalogSnapshot> catalog() {
        return ResponseEntity.ok(analyticsService.catalog());
    }

    @GetMapping(value = "/equity/candles", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> equityCandles(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "NSE_EQ") ExchangeSegment exchangeSegment,
            @RequestParam String interval,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(defaultValue = "5000") int limit
    ) {
        InstrumentKey key = InstrumentKey.of(symbol, exchangeSegment);
        List<Candle> candles = analyticsService.queryEquityCandles(new CandleHistoryRequest(
                key,
                interval,
                from,
                to
        ));
        if (candles.size() > limit) {
            candles = candles.subList(0, limit);
        }
        return ResponseEntity.ok(Map.of(
                "symbol", key.symbol(),
                "exchangeSegment", exchangeSegment.name(),
                "interval", interval,
                "from", from.toString(),
                "to", to.toString(),
                "count", candles.size(),
                "candles", candles.stream().map(this::toCandleMap).toList()
        ));
    }

    @GetMapping(value = "/equity/universe", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> equityUniverse() {
        List<UniverseEntry> universe = analyticsService.queryEquityUniverse();
        return ResponseEntity.ok(Map.of(
                "count", universe.size(),
                "symbols", universe.stream().map(this::toUniverseMap).toList()
        ));
    }

    @GetMapping(value = "/options/bars", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> optionBars(
            @RequestParam String underlying,
            @RequestParam String expiryKind,
            @RequestParam int expiryCode,
            @RequestParam int strikeOffset,
            @RequestParam String optionType,
            @RequestParam(defaultValue = "5") int intervalMin,
            @RequestParam long from,
            @RequestParam long to,
            @RequestParam(defaultValue = "1000") int limit
    ) {
        String canonicalUnderlying = StandardInstrumentIdentityService.INSTANCE.canonicalSymbol(underlying);
        List<RollingOptionBar> bars = analyticsService.queryOptionBars(new RollingOptionSeriesRequest(
                canonicalUnderlying,
                expiryKind,
                expiryCode,
                strikeOffset,
                OptionType.fromCode(optionType),
                intervalMin,
                from,
                to,
                limit
        ));
        return ResponseEntity.ok(Map.of(
                "underlying", canonicalUnderlying,
                "expiryKind", expiryKind,
                "expiryCode", expiryCode,
                "strikeOffset", strikeOffset,
                "optionType", optionType,
                "intervalMin", intervalMin,
                "from", from,
                "to", to,
                "count", bars.size(),
                "bars", bars.stream().map(this::toOptionBarMap).toList()
        ));
    }

    @PostMapping(value = "/sql", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AnalyticsQueryResult> sql(@RequestBody SqlRequest request) {
        if (!tradingProperties.analytics().sqlEnabled()) {
            throw new IllegalStateException("Analytics SQL is disabled");
        }
        int rowLimit = request.limit() == null
                ? tradingProperties.analytics().sqlMaxRows()
                : request.limit();
        return ResponseEntity.ok(analyticsService.executeReadOnlySql(request.sql(), rowLimit));
    }

    private Map<String, Object> toCandleMap(Candle candle) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("symbol", candle.symbol());
        row.put("interval", candle.interval());
        row.put("barTimeMs", candle.startTimeMs());
        row.put("openPaisa", candle.openPaisa());
        row.put("highPaisa", candle.highPaisa());
        row.put("lowPaisa", candle.lowPaisa());
        row.put("closePaisa", candle.closePaisa());
        row.put("volume", candle.volume());
        return row;
    }

    private Map<String, Object> toUniverseMap(UniverseEntry entry) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("symbol", entry.symbol());
        row.put("companyName", entry.companyName());
        row.put("isin", entry.isin());
        row.put("industry", entry.industry());
        row.put("macroSector", entry.macroSector());
        row.put("asOfDate", entry.asOfDate());
        return row;
    }

    private Map<String, Object> toOptionBarMap(RollingOptionBar bar) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("timestampMs", bar.timestampMs());
        row.put("openPaisa", bar.openPaisa());
        row.put("highPaisa", bar.highPaisa());
        row.put("lowPaisa", bar.lowPaisa());
        row.put("closePaisa", bar.closePaisa());
        row.put("volume", bar.volume());
        row.put("iv", bar.iv());
        row.put("oi", bar.oi());
        row.put("spotPaisa", bar.spotPaisa());
        row.put("strikePaisa", bar.strikePaisa());
        return row;
    }

    public record SqlRequest(String sql, Integer limit) {
    }
}
