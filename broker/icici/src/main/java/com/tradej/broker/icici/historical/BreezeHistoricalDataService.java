package com.tradej.broker.icici.historical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.api.model.HistoricalDataCapabilities;
import com.tradej.broker.core.historical.HistoricalCandleMerger;
import com.tradej.broker.core.historical.HistoricalDateWindowSplitter;
import com.tradej.broker.icici.http.BreezeHistoricalIntervals;
import com.tradej.broker.icici.instrument.BreezeInstrumentDefinition;
import com.tradej.broker.icici.mapper.BreezeDomainMapper;
import com.tradej.broker.icici.resilience.IciciResilienceExecutor;
import com.tradej.broker.icici.rest.BreezeHistoricalRestClient;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.Instrument;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class BreezeHistoricalDataService {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    /** Max seconds per v2 request (1000 rows at 1-second granularity). */
    private static final int MAX_SECONDS_PER_V2_REQUEST = 999;
    private static final DateTimeFormatter BREEZE_HISTORICAL_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.000'Z'").withZone(INDIA);
    private static final DateTimeFormatter BREEZE_CANDLE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(INDIA);

    private final BreezeHistoricalRestClient historicalRestClient;
    private final BreezeDomainMapper mapper;
    private final IciciResilienceExecutor resilienceExecutor;
    private final HistoricalDataCapabilities capabilities;

    public BreezeHistoricalDataService(
            BreezeHistoricalRestClient historicalRestClient,
            BreezeDomainMapper mapper,
            IciciResilienceExecutor resilienceExecutor
    ) {
        this(historicalRestClient, mapper, resilienceExecutor, HistoricalDataCapabilities.iciciDefaults());
    }

    public BreezeHistoricalDataService(
            BreezeHistoricalRestClient historicalRestClient,
            BreezeDomainMapper mapper,
            IciciResilienceExecutor resilienceExecutor,
            HistoricalDataCapabilities capabilities
    ) {
        this.historicalRestClient = historicalRestClient;
        this.mapper = mapper;
        this.resilienceExecutor = resilienceExecutor;
        this.capabilities = capabilities;
    }

    public List<Candle> fetchCandles(CandleHistoryRequest request, BreezeInstrumentDefinition definition) {
        LocalDate fromDate = request.fromDate();
        LocalDate toDate = request.toDate();
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("Invalid historical date range");
        }
        String apiInterval = BreezeHistoricalIntervals.toApiInterval(request.interval());
        if (BreezeHistoricalIntervals.isSecond(apiInterval)) {
            return fetchSecondHistorical(request.interval(), fromDate, toDate, definition);
        }
        boolean daily = BreezeHistoricalIntervals.isDaily(apiInterval);
        int maxDays = daily ? capabilities.maxDailyDaysPerRequest() : capabilities.maxIntradayDaysPerRequest();
        Instrument instrument = definition.toInstrument();
        List<Candle> merged = new ArrayList<>();
        for (HistoricalDateWindowSplitter.DateWindow window : HistoricalDateWindowSplitter.split(fromDate, toDate, maxDays)) {
            merged.addAll(fetchWindow(request.interval(), apiInterval, window, definition, instrument, daily));
        }
        return HistoricalCandleMerger.dedupeAndSort(merged);
    }

    private List<Candle> fetchSecondHistorical(
            String requestInterval,
            LocalDate fromDate,
            LocalDate toDate,
            BreezeInstrumentDefinition definition
    ) {
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("Invalid historical date range");
        }
        Instrument instrument = definition.toInstrument();
        String v2Interval = BreezeHistoricalIntervals.toV2ApiInterval(requestInterval);
        List<Candle> merged = new ArrayList<>();
        for (HistoricalDateWindowSplitter.DateWindow window :
                HistoricalDateWindowSplitter.split(fromDate, toDate, capabilities.maxIntradayDaysPerRequest())) {
            merged.addAll(fetchSecondDayWindow(requestInterval, v2Interval, window, definition, instrument));
        }
        return HistoricalCandleMerger.dedupeAndSort(merged);
    }

    private List<Candle> fetchSecondDayWindow(
            String requestInterval,
            String v2Interval,
            HistoricalDateWindowSplitter.DateWindow window,
            BreezeInstrumentDefinition definition,
            Instrument instrument
    ) {
        ZonedDateTime sessionOpen = window.fromDate().atTime(9, 15).atZone(INDIA);
        ZonedDateTime sessionClose = window.toDate().atTime(15, 30).atZone(INDIA);
        List<Candle> collected = new ArrayList<>();
        ZonedDateTime chunkStart = sessionOpen;
        while (!chunkStart.isAfter(sessionClose)) {
            ZonedDateTime chunkEnd = chunkStart.plusSeconds(MAX_SECONDS_PER_V2_REQUEST);
            if (chunkEnd.isAfter(sessionClose)) {
                chunkEnd = sessionClose;
            }
            collected.addAll(fetchSecondChunk(requestInterval, v2Interval, chunkStart, chunkEnd, definition, instrument));
            chunkStart = chunkEnd.plusSeconds(1);
        }
        return collected;
    }

    private List<Candle> fetchSecondChunk(
            String requestInterval,
            String v2Interval,
            ZonedDateTime rangeFrom,
            ZonedDateTime rangeTo,
            BreezeInstrumentDefinition definition,
            Instrument instrument
    ) {
        String fromStr = BREEZE_HISTORICAL_DATE.format(rangeFrom);
        String toStr = BREEZE_HISTORICAL_DATE.format(rangeTo);
        List<Candle> collected = new ArrayList<>();
        String currentTo = toStr;
        while (true) {
            Map<String, String> params = mapper.toHistoricalV2QueryParams(definition, v2Interval, fromStr, currentTo);
            JsonNode success = resilienceExecutor.executeData(
                    "historical-charts-v2",
                    () -> historicalRestClient.getHistoricalChartsV2(params)
            );
            List<Candle> page = parseCandles(success, instrument, requestInterval);
            collected.addAll(page);
            if (page.size() < capabilities.maxRowsPerRequest()) {
                break;
            }
            long earliestMs = page.stream()
                    .mapToLong(Candle::startTimeMs)
                    .min()
                    .orElseThrow();
            ZonedDateTime nextTo = Instant.ofEpochMilli(earliestMs - 1_000L).atZone(INDIA);
            if (nextTo.isBefore(rangeFrom)) {
                break;
            }
            currentTo = BREEZE_HISTORICAL_DATE.format(nextTo);
            if (currentTo.compareTo(fromStr) < 0) {
                break;
            }
        }
        return collected;
    }

    private List<Candle> fetchWindow(
            String requestInterval,
            String apiInterval,
            HistoricalDateWindowSplitter.DateWindow window,
            BreezeInstrumentDefinition definition,
            Instrument instrument,
            boolean daily
    ) {
        ZonedDateTime rangeFrom = window.fromDate().atTime(9, 15).atZone(INDIA);
        ZonedDateTime rangeTo = window.toDate().atTime(15, 30).atZone(INDIA);
        String fromStr = BREEZE_HISTORICAL_DATE.format(rangeFrom);
        String toStr = BREEZE_HISTORICAL_DATE.format(rangeTo);
        List<Candle> collected = new ArrayList<>();
        String currentTo = toStr;
        while (true) {
            ObjectNode payload = mapper.toHistoricalPayload(definition, apiInterval, fromStr, currentTo);
            JsonNode success = fetchHistorical(apiInterval, payload);
            List<Candle> page = parseCandles(success, instrument, requestInterval);
            collected.addAll(page);
            if (page.size() < capabilities.maxRowsPerRequest()) {
                break;
            }
            long earliestMs = page.stream()
                    .mapToLong(Candle::startTimeMs)
                    .min()
                    .orElseThrow();
            long stepMs = BreezeHistoricalIntervals.intervalDurationMs(apiInterval);
            ZonedDateTime nextTo = Instant.ofEpochMilli(earliestMs - stepMs).atZone(INDIA);
            if (nextTo.isBefore(rangeFrom)) {
                break;
            }
            currentTo = BREEZE_HISTORICAL_DATE.format(nextTo);
            if (currentTo.compareTo(fromStr) < 0) {
                break;
            }
        }
        return collected;
    }

    private JsonNode fetchHistorical(String apiInterval, ObjectNode payload) {
        if (BreezeHistoricalIntervals.isDaily(apiInterval)) {
            return resilienceExecutor.executeDaily("historical-charts", () -> historicalRestClient.getHistoricalCharts(payload));
        }
        return resilienceExecutor.executeData("historical-charts", () -> historicalRestClient.getHistoricalCharts(payload));
    }

    static List<Candle> parseCandles(JsonNode success, Instrument instrument, String interval) {
        List<Candle> candles = new ArrayList<>();
        if (success == null || !success.isArray()) {
            return candles;
        }
        for (JsonNode node : success) {
            long open = Math.round(node.path("open").asDouble(0.0) * 100.0);
            long high = Math.round(node.path("high").asDouble(0.0) * 100.0);
            long low = Math.round(node.path("low").asDouble(0.0) * 100.0);
            long close = Math.round(node.path("close").asDouble(0.0) * 100.0);
            long volume = node.path("volume").asLong(0L);
            long timestampMs = parseCandleTimestampMs(node.path("datetime").asText(null));
            candles.add(new Candle(
                    instrument.canonicalSymbol(),
                    interval,
                    timestampMs,
                    timestampMs,
                    open,
                    high,
                    low,
                    close,
                    volume,
                    true
            ));
        }
        candles.sort(Comparator.comparingLong(Candle::startTimeMs));
        return candles;
    }

    static long parseCandleTimestampMs(String datetime) {
        if (datetime == null || datetime.isBlank()) {
            throw new IllegalArgumentException("ICICI historical candle missing datetime");
        }
        try {
            return BREEZE_CANDLE_DATETIME.parse(datetime, Instant::from).toEpochMilli();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid ICICI historical candle datetime: " + datetime, ex);
        }
    }
}
