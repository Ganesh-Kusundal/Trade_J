package com.tradej.app.api;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.indicators.IndicatorEngine;
import com.tradej.indicators.IndicatorEngine.EnrichedChart;
import com.tradej.core.domain.port.HistoricalBarRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST surface for the backend indicator engine. The frontend
 * previously computed MA / VWAP client-side; per the improvement
 * plan, those computations move here so the same indicators are
 * used by the chart, the strategy SDK, the backtest engine, and
 * the replay runner.
 */
@RestController
@RequestMapping("/api/v1/indicators")
public class IndicatorController {

    private final IndicatorEngine indicatorEngine;
    private final HistoricalBarRepository barRepository;

    public IndicatorController(
            IndicatorEngine indicatorEngine,
            HistoricalBarRepository barRepository
    ) {
        this.indicatorEngine = indicatorEngine;
        this.barRepository = barRepository;
    }

    @GetMapping("/enrich")
    public ResponseEntity<Map<String, Object>> enrich(
            @RequestParam String symbol,
            @RequestParam ExchangeSegment exchangeSegment,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        if (barRepository == null) {
            return ResponseEntity.ok(Map.of("error", "HistoricalBarRepository not configured"));
        }
        List<Candle> candles = barRepository.queryCandles(
                com.tradej.core.domain.model.InstrumentKey.of(symbol, exchangeSegment),
                interval, from, to
        );
        if (candles.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "symbol", symbol,
                    "interval", interval,
                    "candles", 0,
                    "indicators", Map.of()
            ));
        }
        EnrichedChart chart = indicatorEngine.enrich(candles);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("symbol", symbol);
        body.put("interval", interval);
        body.put("from", from.toString());
        body.put("to", to.toString());
        body.put("candles", candles.size());
        body.put("indicators", Map.of(
                "halfTrend", chart.halfTrend(),
                "cvd", chart.cvd(),
                "bollingerSqueeze", chart.bollingerSqueeze(),
                "markers", chart.markers(),
                "orderBlockZones", chart.orderBlockZones()
        ));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/sma")
    public ResponseEntity<Map<String, Object>> sma(
            @RequestParam String symbol,
            @RequestParam ExchangeSegment exchangeSegment,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam(defaultValue = "20") int period,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        if (barRepository == null) {
            return ResponseEntity.ok(Map.of("error", "HistoricalBarRepository not configured"));
        }
        List<Candle> candles;
        try {
            candles = barRepository.queryCandles(
                    com.tradej.core.domain.model.InstrumentKey.of(symbol, exchangeSegment),
                    interval, from, to
            );
        } catch (RuntimeException ex) {
            // DuckDB may have a file-handle conflict between the federated
            // equity and options warehouses; report 503 with the cause.
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Indicator data unavailable: " + ex.getMessage()
            ));
        }
        List<Double> sma = new com.tradej.indicators.SMA(period).calculate(candles);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("symbol", symbol);
        body.put("interval", interval);
        body.put("period", period);
        body.put("from", from.toString());
        body.put("to", to.toString());
        body.put("values", sma);
        return ResponseEntity.ok(body);
    }
}
