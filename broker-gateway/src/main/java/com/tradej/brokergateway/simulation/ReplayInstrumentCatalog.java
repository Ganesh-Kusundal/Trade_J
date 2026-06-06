package com.tradej.brokergateway.simulation;

import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.value.ExchangeSegment;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Time-aware instrument catalog for replay and backtest scenarios.
 * Filters instruments based on the current replay date — expired options
 * are removed from the catalog after their expiry date.
 */
public final class ReplayInstrumentCatalog {

    private final List<Instrument> allInstruments;
    private volatile LocalDate currentDate;

    public ReplayInstrumentCatalog(List<Instrument> instruments) {
        this.allInstruments = List.copyOf(instruments);
        this.currentDate = LocalDate.now();
    }

    /**
     * Advance the catalog to a specific date. Instruments that have expired
     * before this date will be filtered out.
     *
     * @param date the new current date for the replay session
     */
    public void advanceTo(LocalDate date) {
        this.currentDate = date;
    }

    /**
     * Returns the current replay date.
     */
    public LocalDate currentDate() {
        return currentDate;
    }

    /**
     * Returns all instruments valid at the current replay date.
     * Options past their expiry are excluded.
     */
    public List<Instrument> activeInstruments() {
        return allInstruments.stream()
                .filter(i -> !isExpired(i, currentDate))
                .collect(Collectors.toList());
    }

    /**
     * Returns instruments matching the given symbol and segment
     * that are active at the current replay date.
     *
     * @param symbol  the symbol to match (case-insensitive)
     * @param segment the exchange segment to match
     * @return matching active instruments
     */
    public List<Instrument> findBySymbol(String symbol, ExchangeSegment segment) {
        return activeInstruments().stream()
                .filter(i -> i.symbol().equalsIgnoreCase(symbol))
                .filter(i -> i.exchangeSegment() == segment)
                .collect(Collectors.toList());
    }

    /**
     * Returns all option instruments for the given underlying expiring on or after
     * the current date.
     *
     * @param underlying the underlying symbol
     * @param segment    the exchange segment
     * @return active option instruments for the underlying
     */
    public List<Instrument> findOptions(String underlying, ExchangeSegment segment) {
        return activeInstruments().stream()
                .filter(i -> i.exchangeSegment() == segment)
                .filter(i -> i.instrumentType() != null && i.instrumentType().toUpperCase().contains("OPT"))
                .filter(i -> i.underlying() != null && i.underlying().equalsIgnoreCase(underlying))
                .collect(Collectors.toList());
    }

    /**
     * Returns the count of instruments active at the current replay date.
     */
    public int activeCount() {
        return activeInstruments().size();
    }

    /**
     * Returns the total count of all instruments in the catalog (including expired).
     */
    public int totalCount() {
        return allInstruments.size();
    }

    private static boolean isExpired(Instrument instrument, LocalDate date) {
        if (instrument.expiry() == null) return false;
        return instrument.expiry().isBefore(date);
    }
}
