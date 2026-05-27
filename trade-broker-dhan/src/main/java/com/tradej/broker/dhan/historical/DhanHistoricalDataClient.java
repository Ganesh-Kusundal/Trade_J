package com.tradej.broker.dhan.historical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanResilienceExecutor;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.SessionSchedule;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class DhanHistoricalDataClient {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(DhanProtocolConstants.HISTORICAL_TIME_FORMAT);

    private final ObjectMapper objectMapper;
    private final DhanAuthenticatedHttpClient httpClient;
    private final DhanApiUrlResolver apiUrlResolver;
    private final DhanResilienceExecutor resilienceExecutor;

    public DhanHistoricalDataClient(
            DhanAuthenticatedHttpClient httpClient,
            DhanApiUrlResolver apiUrlResolver,
            DhanResilienceExecutor resilienceExecutor
    ) {
        this.objectMapper = new ObjectMapper();
        this.httpClient = httpClient;
        this.apiUrlResolver = apiUrlResolver;
        this.resilienceExecutor = resilienceExecutor;
    }

    @Deprecated
    public DhanHistoricalDataClient(DhanAuthenticatedHttpClient httpClient, DhanResilienceExecutor resilienceExecutor) {
        this(httpClient, new DhanApiUrlResolver(DhanConnectionSettings.defaultBaseUrl(null)), resilienceExecutor);
    }

    public DhanJsonResponse fetch(CandleHistoryRequest request, DhanInstrumentDefinition definition) {
        boolean daily = isDailyInterval(request.interval());
        String endpoint = daily ? apiUrlResolver.historicalDailyUrl() : apiUrlResolver.historicalIntradayUrl();
        ObjectNode payload = daily ? dailyPayload(request, definition) : intradayPayload(request, definition);
        return resilienceExecutor.execute(ApiCategory.DATA, daily ? "historical-daily" : "historical-intraday",
                () -> httpClient.postJson(endpoint, payload));
    }

    private ObjectNode dailyPayload(CandleHistoryRequest request, DhanInstrumentDefinition definition) {
        String instrumentType = toHistoricalInstrumentType(definition.instrumentType());
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("securityId", definition.securityId());
        payload.put("exchangeSegment", definition.exchangeSegment().name());
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
        payload.put("exchangeSegment", definition.exchangeSegment().name());
        payload.put("instrument", instrumentType);
        payload.put("interval", toDhanInterval(request.interval()));
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

    private LocalTime sessionOpen(ExchangeSegment exchangeSegment) {
        return SessionSchedule.sessionOpen(exchangeSegment);
    }

    private LocalTime sessionClose(ExchangeSegment exchangeSegment) {
        return SessionSchedule.sessionClose(exchangeSegment);
    }
}
