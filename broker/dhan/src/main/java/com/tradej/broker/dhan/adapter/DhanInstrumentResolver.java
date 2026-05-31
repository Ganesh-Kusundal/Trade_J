package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanSdkResponse;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Dhan-specific extension of {@link InstrumentResolver} that exposes methods
 * returning {@link DhanInstrumentDefinition} for broker-internal use.
 *
 * <p>All Dhan adapter classes should depend on this interface rather than
 * the concrete {@link InMemoryInstrumentResolver} class, fixing the DIP
 * violation found in the architectural audit.
 */
public interface DhanInstrumentResolver extends InstrumentResolver {

    /** Look up by broker security ID — delegates to {@link #requireSecurityId}. */
    default DhanInstrumentDefinition getBySecurityId(String securityId) {
        return requireSecurityId(securityId);
    }

    /** Resolve an InstrumentKey to a Dhan-specific definition. */
    DhanInstrumentDefinition requireDhanDefinition(InstrumentKey key);

    /** Resolve a symbol + exchange segment pair to a Dhan-specific definition. */
    DhanInstrumentDefinition requireDhanDefinition(String symbol, ExchangeSegment segment);

    /** Resolve an arbitrary Dhan SDK payload to a DhanInstrumentDefinition. */
    DhanInstrumentDefinition resolveDhanPayload(Object payload);

    /** Resolve a typed Dhan SDK response to a DhanInstrumentDefinition. */
    default DhanInstrumentDefinition resolveDhanPayload(DhanSdkResponse<?> response) {
        return resolveDhanPayload(response.raw());
    }

    /** Look up by broker security ID. */
    DhanInstrumentDefinition requireSecurityId(String securityId);

    /** Get the nearest live futures contract. */
    DhanInstrumentDefinition nearestFuturesContract(String underlying, ExchangeSegment exchangeSegment);

    /** List all futures contracts for an underlying. */
    List<DhanInstrumentDefinition> futuresContracts(String underlying, ExchangeSegment exchangeSegment);

    /** List all futures expiry dates for an underlying. */
    List<LocalDate> futuresExpiries(String underlying, ExchangeSegment segment);

    /** List all option expiry dates for an underlying. */
    List<LocalDate> optionExpiries(String underlying, ExchangeSegment segment);

    /** List all option contracts for an underlying + expiry. */
    List<DhanInstrumentDefinition> optionContracts(String underlying, ExchangeSegment segment, LocalDate expiry);

    /** Find a specific option contract by parameters. */
    DhanInstrumentDefinition findOptionContract(
            String underlying,
            ExchangeSegment segment,
            LocalDate expiry,
            long strikePricePaisa,
            OptionType optionType
    );

    /** Load instrument catalog from file. */
    void loadCatalog(Path catalogPath);

    /** Replace all instrument definitions in the catalog. */
    void replaceDefinitions(List<DhanInstrumentDefinition> definitions);
}
