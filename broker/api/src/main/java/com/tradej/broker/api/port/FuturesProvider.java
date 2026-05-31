package com.tradej.broker.api.port;

import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.value.ExchangeSegment;

import java.time.LocalDate;
import java.util.List;

public interface FuturesProvider {
    List<Instrument> getContracts(String underlying, ExchangeSegment exchangeSegment);

    Instrument getNearestContract(String underlying, ExchangeSegment exchangeSegment);

    List<LocalDate> getExpiries(String underlying, ExchangeSegment exchangeSegment);

    boolean isCommodity(String underlying);
}
