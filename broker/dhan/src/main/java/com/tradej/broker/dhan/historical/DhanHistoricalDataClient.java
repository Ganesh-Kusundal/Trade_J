package com.tradej.broker.dhan.historical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.instrument.DhanSegmentMapper;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.SessionSchedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public final class DhanHistoricalDataClient {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(DhanProtocolConstants.HISTORICAL_TIME_FORMAT);
    private static final Logger log = LoggerFactory.getLogger(DhanHistoricalDataClient.class);

    private final ObjectMapper objectMapper;
    private final DhanAuthenticatedHttpClient httpClient;
    private final DhanApiUrlResolver apiUrlResolver;
    private final DhanRetryExecutor resilienceExecutor;

    public DhanHistoricalDataClient(
            DhanAuthenticatedHttpClient httpClient,
            DhanApiUrlResolver apiUrlResolver,
            DhanRetryExecutor resilienceExecutor
    ) {
        this.objectMapper = new ObjectMapper();
        this.httpClient = httpClient;
        this.apiUrlResolver = apiUrlResolver;
        this.resilienceExecutor = resilienceExecutor;
    }

    @Deprecated
    public DhanHistoricalDataClient(DhanAuthenticatedHttpClient httpClient, DhanRetryExecutor resilienceExecutor) {
        this(httpClient, new DhanApiUrlResolver(DhanConnectionSettings.defaultBaseUrl(null)), resilienceExecutor);
    }

    public DhanJsonResponse fetch(CandleHistoryRequest request, DhanInstrumentDefinition definition) {
        DateWindow resolved = resolveDateRange(request.fromDate(), request.toDate());
        CandleHistoryRequest bounded = new CandleHistoryRequest(
                request.instrument(),
                request.interval(),
                resolved.fromDate(),
                resolved.toDate()
        );
        boolean daily = isDailyInterval(request.interval());
        String endpoint = daily ? apiUrlResolver.historicalDailyUrl() : apiUrlResolver.historicalIntradayUrl();
        ObjectNode payload = daily ? dailyPayload(bounded, definition) : intradayPayload(bounded, definition);
        return resilienceExecutor.execute(ApiCategory.DATA, daily ? "historical-daily" : "historical-intraday",
                () -> httpClient.postJson(endpoint, payload));
    }

    /**
     * Fetch historical candles over large ranges by splitting into API-safe windows.
     * Callers get one payload per window and may merge downstream.
     * Inserts a brief delay between windows to avoid burst-rate violations.
     */
    public List<DhanJsonResponse> fetchRange(CandleHistoryRequest request, DhanInstrumentDefinition definition) {
        DateWindow resolved = resolveDateRange(request.fromDate(), request.toDate());
        boolean daily = isDailyInterval(request.interval());
        int maxDays = daily
                ? DhanProtocolConstants.HISTORICAL_DAILY_MAX_DAYS
                : DhanProtocolConstants.HISTORICAL_INTRADAY_MAX_DAYS;
        List<DateWindow> windows = splitDateWindows(resolved.fromDate(), resolved.toDate(), maxDays);
        List<DhanJsonResponse> responses = new ArrayList<>(windows.size());
        for (int i = 0; i < windows.size(); i++) {
            DateWindow window = windows.get(i);
            CandleHistoryRequest chunk = new CandleHistoryRequest(
                    request.instrument(),
                    request.interval(),
                    window.fromDate(),
                    window.toDate()
            );
            responses.add(fetch(chunk, definition));
            if (i < windows.size() - 1) {
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Historical fetch interrupted after {}/{} windows for {}; results may be incomplete",
                            i + 1, windows.size(), request.instrument());
                    break;
                }
            }
        }
        return responses;
    }

    private ObjectNode dailyPayload(CandleHistoryRequest request, DhanInstrumentDefinition definition) {
        String instrumentType = toHistoricalInstrumentType(definition.instrumentType());
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("securityId", definition.securityId());
        payload.put("exchangeSegment", DhanSegmentMapper.toWireValue(definition.exchangeSegment()));
        payload.put("instrument", instrumentType);
        payload.put("expiryCode", 0);
        payload.put("oi", carriesOi(instrumentType));
        payload.put("fromDate", request.fromDate().toString());
        payload.put("toDate", request.toDate().toString());
        return payload;
    }

    private ObjectNode intradayPayload(CandleHistoryRequest request, DhanInstrumentDefinition definition) {
        String instrumentType = toHistoricalInstrumentType(definition.instrumentType());
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("securityId", definition.securityId());
        payload.put("exchangeSegment", DhanSegmentMapper.toWireValue(definition.exchangeSegment()));
        payload.put("instrument", instrumentType);
        payload.put("interval", Integer.parseInt(toDhanInterval(request.interval())));
        payload.put("oi", carriesOi(instrumentType));
        payload.put("fromDate", request.fromDate() + " " + SessionSchedule.sessionOpen(definition.exchangeSegment()).format(TIME_FORMATTER));
        payload.put("toDate", request.toDate() + " " + SessionSchedule.sessionClose(definition.exchangeSegment()).format(TIME_FORMATTER));
        return payload;
    }

    private boolean isDailyInterval(String interval) {
        String normalized = interval == null ? "" : interval.trim().toLowerCase();
        return DhanProtocolConstants.DAILY_INTERVALS.contains(normalized);
    }

    private String toDhanInterval(String interval) {
        String normalized = interval == null ? "" : interval.trim().toLowerCase();
        String code = DhanProtocolConstants.INTERVAL_TO_DHAN_CODE.get(normalized);
        if (code != null) {
            return code;
        }
        throw new IllegalArgumentException("Unsupported intraday interval " + interval);
    }

    private String toHistoricalInstrumentType(String instrumentType) {
        if (instrumentType == null || instrumentType.isBlank()) {
            throw new IllegalArgumentException("Missing Dhan instrument type for historical request");
        }
        String normalized = instrumentType.toUpperCase();
        if ("INDEX".equals(normalized)) {
            return "EQUITY";
        }
        if (normalized.equals("EQUITY") || normalized.startsWith("FUT") || normalized.startsWith("OPT")) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported Dhan historical instrument type " + instrumentType);
    }

    private boolean carriesOi(String instrumentType) {
        return instrumentType.startsWith("FUT") || instrumentType.startsWith("OPT");
    }

    public static List<DateWindow> splitDateWindows(LocalDate fromDate, LocalDate toDate, int maxDaysPerRequest) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate and toDate are required");
        }
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("toDate must be on/after fromDate");
        }
        if (maxDaysPerRequest <= 0) {
            throw new IllegalArgumentException("maxDaysPerRequest must be > 0");
        }
        List<DateWindow> windows = new ArrayList<>();
        LocalDate cursor = fromDate;
        while (!cursor.isAfter(toDate)) {
            LocalDate windowEnd = cursor.plusDays(maxDaysPerRequest - 1L);
            if (windowEnd.isAfter(toDate)) {
                windowEnd = toDate;
            }
            windows.add(new DateWindow(cursor, windowEnd));
            cursor = windowEnd.plusDays(1L);
        }
        return windows;
    }

    static DateWindow resolveDateRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate resolvedTo = toDate == null ? LocalDate.now() : toDate;
        LocalDate resolvedFrom = fromDate == null
                ? resolvedTo.minusDays(DhanProtocolConstants.HISTORICAL_INTRADAY_MAX_DAYS - 1L)
                : fromDate;
        if (resolvedTo.isBefore(resolvedFrom)) {
            throw new IllegalArgumentException("toDate must be on/after fromDate");
        }
        return new DateWindow(resolvedFrom, resolvedTo);
    }

    private LocalTime sessionOpen(ExchangeSegment exchangeSegment) {
        return SessionSchedule.sessionOpen(exchangeSegment);
    }

    private LocalTime sessionClose(ExchangeSegment exchangeSegment) {
        return SessionSchedule.sessionClose(exchangeSegment);
    }

    public record DateWindow(LocalDate fromDate, LocalDate toDate) {
    }
}
