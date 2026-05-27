package com.tradej.broker.dhan.instrument;

import com.tradej.broker.dhan.adapter.DhanInstrumentResolver;
import com.tradej.broker.dhan.mapper.ReflectionSupport;
import com.tradej.broker.dhan.instrument.SymbolNormalizationEngine;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DhanInstrumentCatalog implements com.tradej.broker.dhan.adapter.DhanInstrumentResolver {
    private final DhanInstrumentLoader loader;
    private final Map<String, DhanInstrumentDefinition> bySymbol = new ConcurrentHashMap<>();
    private final Map<String, DhanInstrumentDefinition> bySecurityId = new ConcurrentHashMap<>();
    private final Map<String, List<DhanInstrumentDefinition>> futuresByUnderlying = new ConcurrentHashMap<>();
    private final Map<String, List<DhanInstrumentDefinition>> optionsByUnderlying = new ConcurrentHashMap<>();

    public DhanInstrumentCatalog() {
        this(new DhanInstrumentLoader());
    }

    DhanInstrumentCatalog(DhanInstrumentLoader loader) {
        this.loader = loader;
    }

    public void load(Path path) {
        replaceAll(loader.load(path));
    }

    public void replaceAll(List<DhanInstrumentDefinition> definitions) {
        bySymbol.clear();
        bySecurityId.clear();
        futuresByUnderlying.clear();
        optionsByUnderlying.clear();
        for (DhanInstrumentDefinition definition : definitions) {
            indexAlias(definition.symbol(), definition.exchangeSegment(), definition);
            indexAlias(definition.canonicalSymbol(), definition.exchangeSegment(), definition);
            indexAlias(SymbolNormalizationEngine.normalizeDhanSymbol(definition.symbol()), definition.exchangeSegment(), definition);
            indexAlias(SymbolNormalizationEngine.normalizeDhanSymbol(definition.canonicalSymbol()), definition.exchangeSegment(), definition);
            indexAlias(SymbolNormalizationEngine.stripped(definition.symbol()), definition.exchangeSegment(), definition);
            indexAlias(SymbolNormalizationEngine.stripped(definition.canonicalSymbol()), definition.exchangeSegment(), definition);
            if (definition.isOption()) {
                indexAlias(aliasOptionCode(definition, true), definition.exchangeSegment(), definition);
                indexAlias(aliasOptionCode(definition, false), definition.exchangeSegment(), definition);
                optionsByUnderlying.compute(key(definition.underlying()), (ignored, existing) -> append(existing, definition));
            }
            if (definition.isFuture()) {
                indexAlias(DhanSymbolNormalizer.canonicalFutureSymbol(definition.underlying(), definition.expiry()), definition.exchangeSegment(), definition);
                futuresByUnderlying.compute(key(definition.underlying()), (ignored, existing) -> append(existing, definition));
            }
            bySecurityId.put(definition.securityId(), definition);
        }
        futuresByUnderlying.replaceAll((ignored, value) -> sortByExpiry(value));
        optionsByUnderlying.replaceAll((ignored, value) -> sortOptions(value));
    }

    @Override
    public Instrument resolve(InstrumentKey key) {
        return requireDefinition(key);
    }

    @Override
    public Instrument getBySymbol(InstrumentKey key) {
        DhanInstrumentDefinition definition = getDefinition(key.symbol(), key.exchangeSegment());
        if (definition == null && supportsContractDiscovery(key.exchangeSegment())) {
            try {
                return nearestFuturesContract(key.symbol(), key.exchangeSegment()).toInstrument();
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return definition == null ? null : definition.toInstrument();
    }

    @Override
    public DhanInstrumentDefinition requireDhanDefinition(InstrumentKey key) {
        DhanInstrumentDefinition definition = getDefinition(key.symbol(), key.exchangeSegment());
        if (definition == null && supportsContractDiscovery(key.exchangeSegment())) {
            return nearestFuturesContract(key.symbol(), key.exchangeSegment());
        }
        if (definition == null) {
            throw new IllegalArgumentException("No Dhan instrument mapping for " + key.symbol() + " on " + key.exchangeSegment());
        }
        return definition;
    }

    @Override
    public Instrument requireDefinition(InstrumentKey key) {
        return requireDhanDefinition(key).toInstrument();
    }

    @Override
    public DhanInstrumentDefinition requireSecurityId(String securityId) {
        DhanInstrumentDefinition definition = bySecurityId.get(securityId);
        if (definition == null) {
            throw new IllegalArgumentException("No Dhan instrument mapping for securityId=" + securityId);
        }
        return definition;
    }

    public DhanInstrumentDefinition getDefinition(String symbol, ExchangeSegment exchangeSegment) {
        if (symbol == null || exchangeSegment == null) {
            return null;
        }
        DhanInstrumentDefinition direct = bySymbol.get(key(symbol, exchangeSegment));
        if (direct != null) {
            return direct;
        }
        String normalized = SymbolNormalizationEngine.normalizeDhanSymbol(symbol);
        direct = bySymbol.get(key(normalized, exchangeSegment));
        if (direct != null) {
            return direct;
        }
        return bySymbol.get(key(SymbolNormalizationEngine.stripped(symbol), exchangeSegment));
    }

    public List<DhanInstrumentDefinition> futuresContracts(String underlying, ExchangeSegment exchangeSegment) {
        return futuresByUnderlying.getOrDefault(key(underlying), List.of()).stream()
                .filter(definition -> candidateDerivativeSegments(exchangeSegment).contains(definition.exchangeSegment()))
                .toList();
    }

    @Override
    public DhanInstrumentDefinition nearestFuturesContract(String underlying, ExchangeSegment exchangeSegment) {
        LocalDate today = LocalDate.now();
        return futuresContracts(underlying, exchangeSegment).stream()
                .filter(definition -> definition.expiry() == null || !definition.expiry().isBefore(today))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No live futures contract for " + underlying + " on " + exchangeSegment));
    }

    public List<LocalDate> futuresExpiries(String underlying, ExchangeSegment exchangeSegment) {
        return futuresContracts(underlying, exchangeSegment).stream()
                .map(DhanInstrumentDefinition::expiry)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    public List<LocalDate> optionExpiries(String underlying, ExchangeSegment exchangeSegment) {
        return optionsByUnderlying.getOrDefault(key(underlying), List.of()).stream()
                .filter(definition -> candidateDerivativeSegments(exchangeSegment).contains(definition.exchangeSegment()))
                .map(DhanInstrumentDefinition::expiry)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    public List<DhanInstrumentDefinition> optionContracts(String underlying, ExchangeSegment exchangeSegment, LocalDate expiry) {
        return optionsByUnderlying.getOrDefault(key(underlying), List.of()).stream()
                .filter(definition -> candidateDerivativeSegments(exchangeSegment).contains(definition.exchangeSegment()))
                .filter(definition -> expiry.equals(definition.expiry()))
                .toList();
    }

    public DhanInstrumentDefinition findOptionContract(
            String underlying,
            ExchangeSegment exchangeSegment,
            LocalDate expiry,
            long strikePricePaisa,
            OptionType optionType
    ) {
        return optionContracts(underlying, exchangeSegment, expiry).stream()
                .filter(definition -> definition.strikePricePaisa() != null && definition.strikePricePaisa() == strikePricePaisa)
                .filter(definition -> optionType == definition.optionType())
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean isLoaded() {
        return !bySecurityId.isEmpty();
    }

    public int size() {
        return bySecurityId.size();
    }

    @Override
    public DhanInstrumentDefinition getBySecurityId(String securityId) {
        return requireSecurityId(securityId);
    }

    @Override
    public Instrument resolveBySecurityId(String securityId) {
        return requireSecurityId(securityId).toInstrument();
    }

    @Override
    public int catalogSize() {
        return size();
    }

    @Override
    public Instrument resolvePayload(Object payload) {
        return resolveDhanPayload(payload).toInstrument();
    }

    @Override
    public DhanInstrumentDefinition resolveDhanPayload(Object payload) {
        String securityId = ReflectionSupport.optionalString(payload, "getSecurityId");
        if (!securityId.isBlank()) {
            try {
                return requireSecurityId(securityId);
            } catch (IllegalArgumentException ignored) {
                // fall through to explicit symbol + segment resolution
            }
        }
        String tradingSymbol = SymbolNormalizationEngine.normalizeDhanSymbol(
                ReflectionSupport.optionalString(payload, "getTradingSymbol"));
        String exchangeSegment = ReflectionSupport.optionalString(payload, "getExchangeSegment");
        if (!tradingSymbol.isBlank() && !exchangeSegment.isBlank()) {
            return requireDhanDefinition(tradingSymbol, DhanSegmentMapper.fromValue(exchangeSegment));
        }
        String exchange = ReflectionSupport.optionalString(payload, "getExchange");
        if (!tradingSymbol.isBlank() && !exchange.isBlank()) {
            com.tradej.core.domain.value.ExchangeSegment venue = DhanSegmentMapper.fromValue(exchange);
            if (venue != com.tradej.core.domain.value.ExchangeSegment.UNKNOWN) {
                return requireDhanDefinition(tradingSymbol, venue);
            }
        }
        if (!securityId.isBlank()) {
            throw new IllegalArgumentException("Unable to resolve Dhan payload for securityId=" + securityId);
        }
        throw new IllegalArgumentException("Unable to resolve Dhan payload to canonical instrument");
    }

    @Override
    public DhanInstrumentDefinition requireDhanDefinition(String symbol, ExchangeSegment exchangeSegment) {
        return requireDhanDefinition(new InstrumentKey(symbol, exchangeSegment));
    }

    @Override
    public void loadCatalog(java.nio.file.Path catalogPath) {
        load(catalogPath);
    }

    @Override
    public void replaceDefinitions(List<DhanInstrumentDefinition> definitions) {
        replaceAll(definitions);
    }

    @Override
    public List<Instrument> allInstruments() {
        return bySecurityId.values().stream().map(DhanInstrumentDefinition::toInstrument).toList();
    }

    private void indexAlias(String symbol, ExchangeSegment exchangeSegment, DhanInstrumentDefinition definition) {
        if (symbol == null || symbol.isBlank() || exchangeSegment == null) {
            return;
        }
        bySymbol.putIfAbsent(key(symbol, exchangeSegment), definition);
    }

    private List<DhanInstrumentDefinition> append(List<DhanInstrumentDefinition> existing, DhanInstrumentDefinition definition) {
        java.util.ArrayList<DhanInstrumentDefinition> values = new java.util.ArrayList<>(existing == null ? List.of() : existing);
        values.add(definition);
        return List.copyOf(values);
    }

    private List<DhanInstrumentDefinition> sortByExpiry(List<DhanInstrumentDefinition> values) {
        return values.stream()
                .sorted(Comparator.comparing(definition -> definition.expiry() == null ? LocalDate.MAX : definition.expiry()))
                .toList();
    }

    private List<DhanInstrumentDefinition> sortOptions(List<DhanInstrumentDefinition> values) {
        return values.stream()
                .sorted(Comparator
                        .comparing((DhanInstrumentDefinition definition) -> definition.expiry() == null ? LocalDate.MAX : definition.expiry())
                        .thenComparing(definition -> definition.strikePricePaisa() == null ? Long.MAX_VALUE : definition.strikePricePaisa())
                        .thenComparing(DhanInstrumentDefinition::optionType))
                .toList();
    }

    private Set<ExchangeSegment> candidateDerivativeSegments(ExchangeSegment exchangeSegment) {
        if (exchangeSegment == null) {
            return Set.of(ExchangeSegment.NSE_FNO, ExchangeSegment.BSE_FNO, ExchangeSegment.MCX_COMM);
        }
        return switch (exchangeSegment) {
            case IDX_I -> Set.of(ExchangeSegment.NSE_FNO, ExchangeSegment.BSE_FNO);
            case NSE_EQ, NSE_FNO -> Set.of(ExchangeSegment.NSE_FNO);
            case BSE_EQ, BSE_FNO -> Set.of(ExchangeSegment.BSE_FNO);
            case MCX_COMM -> Set.of(ExchangeSegment.MCX_COMM);
            default -> Set.of(exchangeSegment);
        };
    }

    private boolean supportsContractDiscovery(ExchangeSegment exchangeSegment) {
        return exchangeSegment == ExchangeSegment.MCX_COMM
                || exchangeSegment == ExchangeSegment.NSE_FNO
                || exchangeSegment == ExchangeSegment.BSE_FNO;
    }

    private String aliasOptionCode(DhanInstrumentDefinition definition, boolean compact) {
        if (!definition.isOption() || definition.expiry() == null || definition.strikePricePaisa() == null) {
            return "";
        }
        String strike = definition.strikePricePaisa() % 100L == 0L
                ? Long.toString(definition.strikePricePaisa() / 100L)
                : String.format(Locale.ENGLISH, "%.2f", definition.strikePricePaisa() / 100.0d);
        String dayMonth = definition.expiry().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)).toUpperCase(Locale.ENGLISH);
        String suffix = definition.optionType() == OptionType.CALL ? "CE" : "PE";
        return compact
                ? (definition.underlying() + dayMonth + strike + suffix).replace(" ", "")
                : (definition.underlying() + " " + dayMonth + " " + strike + " " + suffix);
    }

    private String key(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ENGLISH);
    }

    private String key(String symbol, ExchangeSegment exchangeSegment) {
        return exchangeSegment.name() + "::" + key(symbol);
    }
}
