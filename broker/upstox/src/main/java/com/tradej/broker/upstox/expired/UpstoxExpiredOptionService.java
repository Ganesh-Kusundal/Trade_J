package com.tradej.broker.upstox.expired;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.rest.UpstoxExpiredInstrumentRestClient;
import com.tradej.core.domain.instrument.ContractSymbolNormalizer;
import com.tradej.core.domain.instrument.ExpiredOptionContractKey;
import com.tradej.core.domain.model.ExpiredOptionBar;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UpstoxExpiredOptionService {

    private static final int MAX_INTRADAY_WINDOW_DAYS = 90;

    private final UpstoxExpiredInstrumentRestClient restClient;
    private final UpstoxExpiredOptionMapper mapper;
    private final UpstoxInstrumentResolver instrumentResolver;

    public UpstoxExpiredOptionService(
            UpstoxExpiredInstrumentRestClient restClient,
            UpstoxExpiredOptionMapper mapper,
            UpstoxInstrumentResolver instrumentResolver
    ) {
        this.restClient = restClient;
        this.mapper = mapper;
        this.instrumentResolver = instrumentResolver;
    }

    public String resolveUnderlyingKey(String symbol, ExchangeSegment segment) {
        return instrumentResolver.requireInstrumentKey(new InstrumentKey(
                ContractSymbolNormalizer.normalize(symbol),
                segment
        ));
    }

    public List<LocalDate> listExpiries(String symbol, ExchangeSegment segment) {
        String instrumentKey = resolveUnderlyingKey(symbol, segment);
        JsonNode root = restClient.getExpiredExpiries(instrumentKey);
        return mapper.mapExpiries(root);
    }

    public List<ExpiredOptionContractKey> listContracts(String symbol, ExchangeSegment segment, LocalDate expiry) {
        String instrumentKey = resolveUnderlyingKey(symbol, segment);
        JsonNode root = restClient.getExpiredOptionContracts(instrumentKey, expiry);
        return mapper.mapContracts(root, symbol, segment, expiry);
    }

    public List<ExpiredOptionBar> fetchCandles(
            ExpiredOptionContractKey contract,
            String interval,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return fetchCandlesByInstrumentKey(contract.brokerInstrumentKey(), interval, fromDate, toDate);
    }

    public List<ExpiredOptionBar> fetchCandlesByInstrumentKey(
            String expiredInstrumentKey,
            String interval,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must be on or before toDate");
        }
        Map<Long, ExpiredOptionBar> deduped = new LinkedHashMap<>();
        for (DateWindow window : splitDateWindows(fromDate, toDate)) {
            JsonNode root = restClient.getExpiredHistoricalCandles(
                    expiredInstrumentKey,
                    interval,
                    window.from(),
                    window.to()
            );
            for (ExpiredOptionBar bar : mapper.mapCandles(root)) {
                deduped.putIfAbsent(bar.timestampMs(), bar);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    static List<DateWindow> splitDateWindows(LocalDate fromDate, LocalDate toDate) {
        List<DateWindow> windows = new ArrayList<>();
        LocalDate cursor = fromDate;
        while (!cursor.isAfter(toDate)) {
            LocalDate windowEnd = cursor.plusDays(MAX_INTRADAY_WINDOW_DAYS - 1L);
            if (windowEnd.isAfter(toDate)) {
                windowEnd = toDate;
            }
            windows.add(new DateWindow(cursor, windowEnd));
            cursor = windowEnd.plusDays(1);
        }
        return windows;
    }

    record DateWindow(LocalDate from, LocalDate to) {
    }
}
