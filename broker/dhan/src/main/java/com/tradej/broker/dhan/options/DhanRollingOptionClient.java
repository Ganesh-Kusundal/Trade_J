package com.tradej.broker.dhan.options;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.historical.DhanHistoricalDataClient;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanResilienceExecutor;
import com.tradej.core.domain.instrument.RollingOptionSeriesKey;
import com.tradej.core.domain.model.RollingOptionBar;
import com.tradej.core.domain.model.RollingOptionHistoryRequest;
import com.tradej.core.domain.model.RollingOptionSeries;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class DhanRollingOptionClient {
    private static final Set<String> INDEX_UNDERLYINGS = Set.of(
            "NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY", "BANKEX", "SENSEX"
    );
    private static final List<String> DEFAULT_REQUIRED_DATA = List.of(
            "open", "high", "low", "close", "volume", "iv", "oi", "spot", "strike"
    );

    private final ObjectMapper mapper = new ObjectMapper();
    private final DhanRollingOptionMapper rollingOptionMapper = new DhanRollingOptionMapper();
    private final DhanAuthenticatedHttpClient httpClient;
    private final DhanApiUrlResolver apiUrlResolver;
    private final DhanResilienceExecutor resilienceExecutor;

    public DhanRollingOptionClient(
            DhanAuthenticatedHttpClient httpClient,
            DhanApiUrlResolver apiUrlResolver,
            DhanResilienceExecutor resilienceExecutor
    ) {
        this.httpClient = httpClient;
        this.apiUrlResolver = apiUrlResolver;
        this.resilienceExecutor = resilienceExecutor;
    }

    public RollingOptionSeries fetch(DhanInstrumentDefinition underlying, RollingOptionHistoryRequest request) {
        return resilienceExecutor.execute(ApiCategory.DATA, "rolling-option-history", () -> {
            var response = httpClient.postJson(apiUrlResolver.rollingOptionUrl(), buildPayload(underlying, request));
            return rollingOptionMapper.toSeries(new DhanJsonResponse(response.raw()), request);
        });
    }

    public RollingOptionSeries fetchRange(DhanInstrumentDefinition underlying, RollingOptionHistoryRequest request) {
        LocalDate from = request.fromDate();
        LocalDate to = request.toDate();

        List<DhanHistoricalDataClient.DateWindow> windows = DhanHistoricalDataClient.splitDateWindows(
                from,
                to,
                DhanProtocolConstants.ROLLING_OPTION_MAX_DAYS
        );
        List<RollingOptionBar> merged = new ArrayList<>();
        for (DhanHistoricalDataClient.DateWindow window : windows) {
            RollingOptionHistoryRequest chunk = new RollingOptionHistoryRequest(
                    request.series(),
                    window.fromDate(),
                    window.toDate()
            );
            RollingOptionSeries series = fetch(underlying, chunk);
            merged.addAll(series.bars());
        }
        return new RollingOptionSeries(request, merged);
    }

    private ObjectNode buildPayload(DhanInstrumentDefinition underlying, RollingOptionHistoryRequest request) {
        RollingOptionSeriesKey series = request.series();
        ObjectNode payload = mapper.createObjectNode();
        payload.put("exchangeSegment", rollingExchangeSegment(underlying));
        payload.put("instrument", rollingInstrumentType(underlying));
        payload.put("securityId", underlying.securityId());
        DhanRollingOptionWireMapper.applySeries(payload, series);
        var requiredData = payload.putArray("requiredData");
        for (String field : DEFAULT_REQUIRED_DATA) {
            requiredData.add(field);
        }
        payload.put("fromDate", request.fromDate().toString());
        payload.put("toDate", request.toDate().toString());
        return payload;
    }

    private static String rollingExchangeSegment(DhanInstrumentDefinition underlying) {
        return switch (underlying.exchangeSegment()) {
            case IDX_I, NSE_EQ, NSE_FNO -> "NSE_FNO";
            case BSE_EQ, BSE_FNO -> "BSE_FNO";
            default -> underlying.exchangeSegment().name();
        };
    }

    private static String rollingInstrumentType(DhanInstrumentDefinition underlying) {
        String symbol = underlying.symbol() == null ? "" : underlying.symbol().trim().toUpperCase();
        if (underlying.exchangeSegment() == ExchangeSegment.IDX_I || INDEX_UNDERLYINGS.contains(symbol)) {
            return "OPTIDX";
        }
        return "OPTSTK";
    }
}
