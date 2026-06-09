package com.tradej.core.domain.port;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.UniverseEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class NoOpHistoricalBarRepository implements HistoricalBarRepository {

    @Override
    public List<Candle> queryCandles(CandleHistoryRequest request) {
        return List.of();
    }

    @Override
    public List<Candle> queryCandles(InstrumentKey instrument, String interval, LocalDate from, LocalDate to) {
        return List.of();
    }

    @Override
    public List<Candle> queryIntradayBars(List<String> symbols, LocalDate date) {
        return List.of();
    }

    @Override
    public List<Candle> queryBenchmarkBars(LocalDate date, String benchmarkSymbol) {
        return List.of();
    }

    @Override
    public List<UniverseEntry> queryUniverse() {
        return List.of();
    }

    @Override
    public List<String> querySymbols(int limit) {
        return List.of();
    }

    @Override
    public List<String> querySymbolsWithDataOn(LocalDate date, int limit) {
        return List.of();
    }

    @Override
    public Optional<LocalDate> latestAvailableTradingDay(int lookbackDays) {
        return Optional.empty();
    }
}
