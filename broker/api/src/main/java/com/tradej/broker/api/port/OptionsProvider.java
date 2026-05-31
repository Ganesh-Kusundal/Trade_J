package com.tradej.broker.api.port;

import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.OptionQuote;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.RollingOptionHistoryRequest;
import com.tradej.core.domain.model.RollingOptionSeries;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.core.domain.value.StrikeSelectionKind;

import java.time.LocalDate;
import java.util.List;

public interface OptionsProvider {
    List<LocalDate> getExpiries(String underlying, ExchangeSegment exchangeSegment);

    List<Instrument> getOptionContracts(String underlying, ExchangeSegment exchangeSegment, LocalDate expiry);

    OptionChainSnapshot getOptionChain(String underlying, ExchangeSegment exchangeSegment, LocalDate expiry);

    OptionQuote getGreeks(InstrumentKey instrumentKey);

    RollingOptionSeries getExpiredOptionHistory(RollingOptionHistoryRequest request);

    long selectStrikePaisa(String underlying, ExchangeSegment exchangeSegment, long spotPricePaisa, OptionType optionType, StrikeSelectionKind selectionKind, int depth);
}
