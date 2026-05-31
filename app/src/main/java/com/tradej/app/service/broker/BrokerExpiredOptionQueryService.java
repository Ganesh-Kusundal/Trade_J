package com.tradej.app.service.broker;

import com.tradej.broker.upstox.expired.UpstoxExpiredOptionService;
import com.tradej.core.domain.instrument.ExpiredOptionContractKey;
import com.tradej.core.domain.model.ExpiredOptionBar;
import com.tradej.core.domain.value.ExchangeSegment;

import java.time.LocalDate;
import java.util.List;

public final class BrokerExpiredOptionQueryService {

    private final UpstoxExpiredOptionService expiredOptionService;

    public BrokerExpiredOptionQueryService(UpstoxExpiredOptionService expiredOptionService) {
        this.expiredOptionService = expiredOptionService;
    }

    public List<LocalDate> listExpiries(String symbol, ExchangeSegment exchangeSegment) {
        return expiredOptionService.listExpiries(symbol, exchangeSegment);
    }

    public List<ExpiredOptionContractKey> listContracts(
            String symbol,
            ExchangeSegment exchangeSegment,
            LocalDate expiry
    ) {
        return expiredOptionService.listContracts(symbol, exchangeSegment, expiry);
    }

    public List<ExpiredOptionBar> fetchCandles(
            String expiredInstrumentKey,
            String interval,
            LocalDate from,
            LocalDate to
    ) {
        return expiredOptionService.fetchCandlesByInstrumentKey(expiredInstrumentKey, interval, from, to);
    }
}
