package com.tradej.app.studio;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.RollingOptionBar;
import com.tradej.core.domain.model.RollingOptionSeriesRequest;
import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.core.domain.port.RollingOptionHistoricalRepository;
import com.tradej.core.domain.value.OptionType;
import com.tradej.indicators.IndicatorEngine;
import com.tradej.institutional.InstitutionalScanEngine;
import com.tradej.institutional.model.InstitutionalScanResult;
import com.tradej.institutional.model.ScoredBar;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StudioChartService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final HistoricalBarRepository barRepository;
    private final RollingOptionHistoricalRepository optionRepository;
    private final InstitutionalScanEngine scanEngine;
    private final IndicatorEngine indicatorEngine;

    public StudioChartService(
            HistoricalBarRepository barRepository,
            RollingOptionHistoricalRepository optionRepository,
            InstitutionalScanEngine scanEngine,
            IndicatorEngine indicatorEngine
    ) {
        this.barRepository = barRepository;
        this.optionRepository = optionRepository;
        this.scanEngine = scanEngine;
        this.indicatorEngine = indicatorEngine;
    }

    public Map<String, Object> startupCandidates(Optional<LocalDate> date, int topN) {
        LocalDate scanDate = date.orElseGet(this::resolveLatestScanDate);
        InstitutionalScanResult result = scanEngine.runHistoricalScan(scanDate, null);
        List<Map<String, Object>> candidates = result.candidates().stream()
                .limit(Math.max(1, topN))
                .map(this::candidatePayload)
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scanDate", result.scanDate());
        payload.put("scanTime", result.scanTime());
        payload.put("requestedScanTime", result.provenance().getOrDefault("requestedScanTime", result.scanTime()));
        payload.put("chartLookbackDays", 20);
        payload.put("selectionMode", result.selectionMode());
        payload.put("provenance", result.provenance());
        payload.put("candidates", candidates);
        return payload;
    }

    public Map<String, Object> chartPayload(
            String symbol,
            com.tradej.core.domain.value.ExchangeSegment exchangeSegment,
            String interval,
            LocalDate from,
            LocalDate to,
            Optional<RollingOptionSeriesRequest> optionOverlay
    ) {
        LocalDate effectiveFrom = from;
        LocalDate effectiveTo = to;
        if (effectiveFrom == null || effectiveTo == null) {
            LocalDate latest = resolveLatestScanDate();
            effectiveFrom = latest;
            effectiveTo = latest;
        }
        InstrumentKey key = InstrumentKey.of(symbol, exchangeSegment);
        List<Candle> candles = barRepository.queryCandles(key, interval, effectiveFrom, effectiveTo);
        if (candles.isEmpty()) {
            throw new IllegalStateException(
                    "No parquet candles for " + symbol + " between " + effectiveFrom + " and " + effectiveTo
            );
        }
        IndicatorEngine.EnrichedChart enriched = indicatorEngine.enrich(candles);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("symbol", symbol);
        payload.put("exchangeSegment", exchangeSegment.name());
        payload.put("interval", interval);
        payload.put("from", effectiveFrom.toString());
        payload.put("to", effectiveTo.toString());
        payload.put("count", candles.size());
        payload.put("candles", candles.stream().map(this::candlePayload).toList());
        payload.put("halfTrend", enriched.halfTrend().stream().map(point -> Map.of(
                "value", point.value(),
                "direction", point.direction(),
                "high", point.high(),
                "low", point.low()
        )).toList());
        payload.put("cvd", enriched.cvd().stream().map(point -> Map.of(
                "cvd", point.cvd(),
                "volumeDelta", point.volumeDelta()
        )).toList());
        payload.put("bollingerSqueeze", enriched.bollingerSqueeze().stream().map(point -> Map.of(
                "middle", point.middle(),
                "upper", point.upper(),
                "lower", point.lower(),
                "squeeze", point.squeeze()
        )).toList());
        payload.put("markers", enriched.markers().stream().map(marker -> Map.of(
                "type", marker.type(),
                "timeMs", marker.timeMs(),
                "price", marker.price()
        )).toList());
        payload.put("orderBlockZones", enriched.orderBlockZones().stream().map(zone -> Map.of(
                "startTimeMs", zone.startTimeMs(),
                "endTimeMs", zone.endTimeMs(),
                "top", zone.top(),
                "bottom", zone.bottom(),
                "bias", zone.bias()
        )).toList());
        optionOverlay.ifPresent(request -> payload.put("optionOverlay", optionOverlayPayload(request)));
        return payload;
    }

    private Map<String, Object> optionOverlayPayload(RollingOptionSeriesRequest request) {
        List<RollingOptionBar> bars = optionRepository.queryBars(request);
        Map<String, Object> overlay = new LinkedHashMap<>();
        overlay.put("underlying", request.underlying());
        overlay.put("expiryKind", request.expiryKind());
        overlay.put("expiryCode", request.expiryCode());
        overlay.put("strikeOffset", request.strikeOffset());
        overlay.put("optionType", request.optionType().name());
        overlay.put("intervalMin", request.intervalMin());
        overlay.put("count", bars.size());
        overlay.put("bars", bars.stream().map(bar -> Map.of(
                "timestampMs", bar.timestampMs(),
                "closePaisa", bar.closePaisa(),
                "volume", bar.volume(),
                "iv", bar.iv(),
                "oi", bar.oi()
        )).toList());
        return overlay;
    }

    public Map<String, Object> chartPayload(
            String symbol,
            com.tradej.core.domain.value.ExchangeSegment exchangeSegment,
            String interval,
            LocalDate from,
            LocalDate to
    ) {
        return chartPayload(symbol, exchangeSegment, interval, from, to, Optional.empty());
    }

    private LocalDate resolveLatestScanDate() {
        return barRepository.latestAvailableTradingDay(0)
                .orElseThrow(() -> new IllegalStateException(
                        "No parquet trading days available under historical-equity warehouse"
                ));
    }

    private Map<String, Object> candidatePayload(ScoredBar bar) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("symbol", bar.symbol());
        map.put("rank", bar.rank());
        map.put("masterScore", bar.masterScore());
        map.put("rsScore", bar.rsScore());
        map.put("volumeExpansionScore", bar.volumeExpansionScore());
        map.put("trendEfficiencyScore", bar.trendEfficiencyScore());
        map.put("openingDriveScore", bar.openingDriveScore());
        map.put("closePaisa", bar.closePaisa());
        map.put("barTimeMs", bar.barTime().toEpochMilli());
        return map;
    }

    private Map<String, Object> candlePayload(Candle candle) {
        Map<String, Object> map = new LinkedHashMap<>();
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
