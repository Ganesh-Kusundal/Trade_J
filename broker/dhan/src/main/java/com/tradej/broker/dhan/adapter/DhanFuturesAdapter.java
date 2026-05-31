package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.SessionSchedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.List;

public final class DhanFuturesAdapter implements FuturesProvider {
    private static final Set<String> COMMON_COMMODITIES = DhanProtocolConstants.COMMON_COMMODITIES;
    private final DhanInstrumentResolver instrumentResolver;

    public DhanFuturesAdapter(DhanInstrumentResolver instrumentResolver) {
        this.instrumentResolver = instrumentResolver;
    }

    @Override
    public List<Instrument> getContracts(String underlying, ExchangeSegment exchangeSegment) {
        return instrumentResolver.futuresContracts(underlying, exchangeSegment).stream()
                .map(contract -> contract.toInstrument())
                .toList();
    }

    @Override
    public Instrument getNearestContract(String underlying, ExchangeSegment exchangeSegment) {
        LocalDate today = LocalDate.now(SessionSchedule.indiaZone());
        LocalTime now = LocalTime.now(SessionSchedule.indiaZone());
        return instrumentResolver.futuresContracts(underlying, exchangeSegment).stream()
                .filter(contract -> contract.expiry() == null || !contract.expiry().isBefore(today))
                .filter(contract -> !SessionSchedule.isPastClosingWindow(contract.expiry(), exchangeSegment, today, now))
                .findFirst()
                .orElseGet(() -> instrumentResolver.nearestFuturesContract(underlying, exchangeSegment))
                .toInstrument();
    }

    @Override
    public List<LocalDate> getExpiries(String underlying, ExchangeSegment exchangeSegment) {
        return instrumentResolver.futuresExpiries(underlying, exchangeSegment);
    }

    @Override
    public boolean isCommodity(String underlying) {
        return underlying != null && COMMON_COMMODITIES.contains(underlying.trim().toUpperCase());
    }


}
