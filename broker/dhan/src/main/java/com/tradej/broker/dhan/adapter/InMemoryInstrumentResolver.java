package com.tradej.broker.dhan.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.instrument.DhanInstrumentCatalog;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.instrument.DhanSegmentMapper;
import com.tradej.core.domain.instrument.ContractSymbolNormalizer;
import com.tradej.broker.core.util.ReflectionSupport;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public final class InMemoryInstrumentResolver implements DhanInstrumentResolver {
    private final DhanInstrumentCatalog catalog = new DhanInstrumentCatalog();

    @Override
    public Instrument resolve(InstrumentKey key) {
        return catalog.resolve(key);
    }

    @Override
    public Instrument getBySymbol(InstrumentKey key) {
        return catalog.getBySymbol(key);
    }

    @Override
    public DhanInstrumentDefinition requireSecurityId(String securityId) {
        return catalog.requireSecurityId(securityId);
    }

    @Override
    public Instrument resolveBySecurityId(String securityId) {
        return catalog.requireSecurityId(securityId).toInstrument();
    }

    @Override
    public boolean isLoaded() {
        return catalog.isLoaded();
    }

    @Override
    public int catalogSize() {
        return catalog.catalogSize();
    }

    // ---- DhanInstrumentResolver (Dhan-specific) ----

    @Override
    public DhanInstrumentDefinition requireDhanDefinition(InstrumentKey key) {
        return catalog.requireDhanDefinition(key);
    }

    @Override
    public DhanInstrumentDefinition requireDhanDefinition(String symbol, ExchangeSegment exchangeSegment) {
        return catalog.requireDhanDefinition(new InstrumentKey(symbol, exchangeSegment));
    }

    // ---- InstrumentResolver (generic) ----

    @Override
    public Instrument requireDefinition(InstrumentKey key) {
        return catalog.requireDefinition(key);
    }

    @Override
    public DhanInstrumentDefinition resolveDhanPayload(Object payload) {
        if (payload instanceof JsonNode node) {
            return resolveJsonPayload(new DhanJsonResponse(node));
        }
        String securityId = ReflectionSupport.optionalString(payload, "getSecurityId");
        if (!securityId.isBlank()) {
            try {
                return getBySecurityId(securityId);
            } catch (IllegalArgumentException ignored) {
                // fall through to explicit symbol + segment resolution
            }
        }
        String tradingSymbol = ContractSymbolNormalizer.normalize(ReflectionSupport.optionalString(payload, "getTradingSymbol"));
        String exchangeSegment = ReflectionSupport.optionalString(payload, "getExchangeSegment");
        if (!tradingSymbol.isBlank() && !exchangeSegment.isBlank()) {
            return requireDhanDefinition(tradingSymbol, DhanSegmentMapper.fromValue(exchangeSegment));
        }
        String exchange = ReflectionSupport.optionalString(payload, "getExchange");
        if (!tradingSymbol.isBlank() && !exchange.isBlank()) {
            ExchangeSegment venue = DhanSegmentMapper.fromValue(exchange);
            if (venue != ExchangeSegment.UNKNOWN) {
                return requireDhanDefinition(tradingSymbol, venue);
            }
        }
        if (!securityId.isBlank()) {
            throw new IllegalArgumentException("Unable to resolve Dhan payload for securityId=" + securityId);
        }
        throw new IllegalArgumentException("Unable to resolve Dhan payload to canonical instrument");
    }

    private DhanInstrumentDefinition resolveJsonPayload(DhanJsonResponse payload) {
        String securityId = payload.string("securityId");
        if (!securityId.isBlank()) {
            try {
                return getBySecurityId(securityId);
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        String tradingSymbol = ContractSymbolNormalizer.normalize(payload.string("tradingSymbol", "symbol"));
        String exchangeSegment = payload.string("exchangeSegment");
        if (!tradingSymbol.isBlank() && !exchangeSegment.isBlank()) {
            return requireDhanDefinition(tradingSymbol, DhanSegmentMapper.fromValue(exchangeSegment));
        }
        String exchange = payload.string("exchange");
        if (!tradingSymbol.isBlank() && !exchange.isBlank()) {
            ExchangeSegment venue = DhanSegmentMapper.fromValue(exchange);
            if (venue != ExchangeSegment.UNKNOWN) {
                return requireDhanDefinition(tradingSymbol, venue);
            }
        }
        if (!securityId.isBlank()) {
            throw new IllegalArgumentException("Unable to resolve Dhan payload for securityId=" + securityId);
        }
        throw new IllegalArgumentException("Unable to resolve Dhan JSON payload to canonical instrument");
    }

    @Override
    public Instrument resolvePayload(Object payload) {
        return resolveDhanPayload(payload).toInstrument();
    }

    @Override
    public DhanInstrumentDefinition nearestFuturesContract(String underlying, ExchangeSegment exchangeSegment) {
        return catalog.nearestFuturesContract(underlying, exchangeSegment);
    }

    @Override
    public List<Instrument> allInstruments() {
        return catalog.allInstruments();
    }

    @Override
    public void loadCatalog(java.nio.file.Path catalogPath) {
        catalog.load(catalogPath);
    }

    @Override
    public void replaceDefinitions(List<DhanInstrumentDefinition> definitions) {
        catalog.replaceAll(definitions);
    }

    @Override
    public List<DhanInstrumentDefinition> futuresContracts(String underlying, ExchangeSegment exchangeSegment) {
        return catalog.futuresContracts(underlying.toUpperCase(Locale.ENGLISH), exchangeSegment);
    }

    @Override
    public List<LocalDate> futuresExpiries(String underlying, ExchangeSegment exchangeSegment) {
        return catalog.futuresExpiries(underlying.toUpperCase(Locale.ENGLISH), exchangeSegment);
    }

    @Override
    public List<LocalDate> optionExpiries(String underlying, ExchangeSegment exchangeSegment) {
        return catalog.optionExpiries(underlying.toUpperCase(Locale.ENGLISH), exchangeSegment);
    }

    @Override
    public List<DhanInstrumentDefinition> optionContracts(String underlying, ExchangeSegment exchangeSegment, LocalDate expiry) {
        return catalog.optionContracts(underlying.toUpperCase(Locale.ENGLISH), exchangeSegment, expiry);
    }

    @Override
    public DhanInstrumentDefinition findOptionContract(
            String underlying,
            ExchangeSegment exchangeSegment,
            LocalDate expiry,
            long strikePricePaisa,
            com.tradej.core.domain.value.OptionType optionType
    ) {
        return catalog.findOptionContract(underlying.toUpperCase(Locale.ENGLISH), exchangeSegment, expiry, strikePricePaisa, optionType);
    }
}
