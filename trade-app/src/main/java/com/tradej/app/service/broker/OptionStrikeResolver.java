package com.tradej.app.service.broker;

import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.OptionStrikeSelection;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.core.domain.value.StrikeSelectionKind;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public final class OptionStrikeResolver {
    private final OptionsProvider optionsProvider;

    public OptionStrikeResolver(OptionsProvider optionsProvider) {
        this.optionsProvider = optionsProvider;
    }

    public OptionStrikeSelection resolve(
            String underlying,
            ExchangeSegment segment,
            LocalDate expiry,
            long spotPricePaisa,
            StrikeSelectionKind selectionKind,
            int depth
    ) {
        long strike = optionsProvider.selectStrikePaisa(
                underlying,
                segment,
                spotPricePaisa,
                OptionType.CALL,
                selectionKind,
                depth
        );
        List<Instrument> contracts = optionsProvider.getOptionContracts(underlying, segment, expiry);
        Instrument call = contracts.stream()
                .filter(Instrument::isOption)
                .filter(instrument -> OptionType.CALL == instrument.optionType())
                .filter(instrument -> instrument.strikePricePaisa() != null && instrument.strikePricePaisa() == strike)
                .min(Comparator.comparing(Instrument::canonicalSymbol))
                .orElseThrow();
        Instrument put = contracts.stream()
                .filter(Instrument::isOption)
                .filter(instrument -> OptionType.PUT == instrument.optionType())
                .filter(instrument -> instrument.strikePricePaisa() != null && instrument.strikePricePaisa() == strike)
                .min(Comparator.comparing(Instrument::canonicalSymbol))
                .orElseThrow();
        return new OptionStrikeSelection(
                expiry,
                strike,
                new InstrumentKey(call.canonicalSymbol(), call.exchangeSegment()),
                new InstrumentKey(put.canonicalSymbol(), put.exchangeSegment())
        );
    }
}
