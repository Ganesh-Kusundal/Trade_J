package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.dhan.exceptions.DhanBrokerException;
import com.tradej.broker.dhan.exceptions.DhanHttpException;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.options.DhanOptionChainClient;
import com.tradej.broker.dhan.options.DhanOptionChainResponseMapper;
import com.tradej.broker.dhan.options.DhanRollingOptionClient;
import com.tradej.broker.dhan.options.OptionExpiryCache;
import com.tradej.broker.dhan.resilience.DhanResilienceExecutor;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.OptionGreeks;
import com.tradej.core.domain.model.OptionQuote;
import com.tradej.core.domain.model.RollingOptionHistoryRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.core.domain.value.PriceMath;
import com.tradej.core.domain.value.StrikeSelectionKind;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class DhanOptionsAdapter extends DhanBaseRestAdapter implements OptionsProvider {
    private static final Map<String, Long> DEFAULT_STRIKE_STEPS_PAISA = DhanProtocolConstants.DEFAULT_STRIKE_STEPS_PAISA;

    private final DhanOptionChainClient optionChainClient;
    private final DhanRollingOptionClient rollingOptionClient;
    private final OptionExpiryCache expiryCache;

    public DhanOptionsAdapter(
            DhanInstrumentResolver instrumentResolver,
            DhanOptionChainClient optionChainClient,
            DhanRollingOptionClient rollingOptionClient,
            OptionExpiryCache expiryCache,
            DhanResilienceExecutor resilienceExecutor
    ) {
        super(instrumentResolver, resilienceExecutor);
        this.optionChainClient = optionChainClient;
        this.rollingOptionClient = rollingOptionClient;
        this.expiryCache = expiryCache;
    }

    @Override
    public List<LocalDate> getExpiries(String underlying, ExchangeSegment exchangeSegment) {
        DhanInstrumentDefinition underlyingDefinition = resolveUnderlying(underlying, exchangeSegment);
        return expiryCache.getOrLoad(
                expiryCacheKey(underlyingDefinition),
                () -> optionChainClient.fetchExpiries(underlyingDefinition)
        );
    }

    @Override
    public List<Instrument> getOptionContracts(String underlying, ExchangeSegment exchangeSegment, LocalDate expiry) {
        if (!resolver.isLoaded()) {
            throw new IllegalStateException(
                    "Instrument catalog is not loaded; call loadInstrumentCatalog before getOptionContracts");
        }
        List<DhanInstrumentDefinition> contracts = resolver.optionContracts(underlying, exchangeSegment, expiry);
        if (contracts.isEmpty()) {
            throw new IllegalStateException(
                    "No option contracts in catalog for " + underlying + " " + exchangeSegment + " " + expiry);
        }
        return contracts.stream().map(DhanInstrumentDefinition::toInstrument).toList();
    }

    @Override
    public OptionChainSnapshot getOptionChain(String underlying, ExchangeSegment exchangeSegment, LocalDate expiry) {
        DhanInstrumentDefinition underlyingDefinition = resolveUnderlying(underlying, exchangeSegment);
        assertExpiryAccepted(underlyingDefinition, underlying, exchangeSegment, expiry);
        DhanOptionChainResponseMapper.OptionChainData chainData =
                optionChainClient.fetchChain(underlyingDefinition, expiry);
        List<OptionChainEntry> strikes = new ArrayList<>();
        chainData.optionChain().fields().forEachRemaining(entry -> {
            long strikePricePaisa = PriceMath.toPaisa(entry.getKey());
            DhanJsonResponse callNode = entry.getValue().path("ce");
            DhanJsonResponse putNode = entry.getValue().path("pe");
            OptionQuote call = callNode.isMissingNode() || callNode.isNull()
                    ? null
                    : mapLeg(underlying, exchangeSegment, expiry, strikePricePaisa, OptionType.CALL, callNode);
            OptionQuote put = putNode.isMissingNode() || putNode.isNull()
                    ? null
                    : mapLeg(underlying, exchangeSegment, expiry, strikePricePaisa, OptionType.PUT, putNode);
            strikes.add(new OptionChainEntry(strikePricePaisa, call, put));
        });
        strikes.sort(Comparator.comparingLong(OptionChainEntry::strikePricePaisa));
        return new OptionChainSnapshot(
                underlyingDefinition.toInstrument(),
                expiry,
                chainData.spotPricePaisa(),
                List.copyOf(strikes)
        );
    }

    @Override
    public OptionQuote getGreeks(InstrumentKey instrumentKey) {
        DhanInstrumentDefinition contract = resolveDef(instrumentKey);
        if (!contract.isOption() || contract.expiry() == null || contract.strikePricePaisa() == null || contract.underlying().isBlank()) {
            throw new IllegalArgumentException("Instrument is not a resolvable option contract: " + instrumentKey);
        }
        OptionChainSnapshot chain = getOptionChain(contract.underlying(), contract.exchangeSegment(), contract.expiry());
        return chain.strikes().stream()
                .filter(entry -> entry.strikePricePaisa() == contract.strikePricePaisa())
                .map(entry -> contract.optionType() == OptionType.CALL ? entry.call() : entry.put())
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new DhanHttpException("Unable to locate option greeks for " + instrumentKey));
    }

    @Override
    public Map<String, List<Double>> getExpiredOptionHistory(RollingOptionHistoryRequest request) {
        DhanInstrumentDefinition underlyingDefinition = resolveUnderlying(request.underlying(), request.exchangeSegment());
        return rollingOptionClient.fetch(underlyingDefinition, request);
    }

    @Override
    public long selectStrikePaisa(
            String underlying,
            ExchangeSegment exchangeSegment,
            long spotPricePaisa,
            OptionType optionType,
            StrikeSelectionKind selectionKind,
            int depth
    ) {
        long step = DEFAULT_STRIKE_STEPS_PAISA.getOrDefault(underlying == null ? "" : underlying.trim().toUpperCase(), DhanProtocolConstants.DEFAULT_STRIKE_STEP_PAISA);
        long atm = Math.round((double) spotPricePaisa / step) * step;
        if (selectionKind == StrikeSelectionKind.ATM) {
            return atm;
        }
        long offset = Math.max(depth, 1) * step;
        boolean call = optionType == OptionType.CALL;
        long signed = switch (selectionKind) {
            case OTM -> call ? offset : -offset;
            case ITM -> call ? -offset : offset;
            case ATM -> 0L;
        };
        return atm + signed;
    }

    private void assertExpiryAccepted(
            DhanInstrumentDefinition underlyingDefinition,
            String underlying,
            ExchangeSegment exchangeSegment,
            LocalDate expiry
    ) {
        List<LocalDate> expiries = getExpiries(underlying, exchangeSegment);
        if (expiries.contains(expiry)) {
            return;
        }
        String nearest = expiries.isEmpty() ? "none" : expiries.getFirst().toString();
        throw new IllegalArgumentException(
                "Expiry " + expiry + " is not accepted by Dhan for "
                        + underlying + " on " + exchangeSegment
                        + " (underlying scrip " + underlyingDefinition.securityId()
                        + "). Nearest valid expiry: " + nearest
                        + ". Call getExpiries() for the full list."
        );
    }

    private static String expiryCacheKey(DhanInstrumentDefinition underlyingDefinition) {
        return underlyingDefinition.securityId() + "|" + underlyingDefinition.exchangeSegment().name();
    }

    private DhanInstrumentDefinition resolveUnderlying(String underlying, ExchangeSegment exchangeSegment) {
        if (exchangeSegment == ExchangeSegment.MCX_COMM) {
            return resolver.nearestFuturesContract(underlying, ExchangeSegment.MCX_COMM);
        }
        for (ExchangeSegment candidate : lookupSegments(exchangeSegment)) {
            try {
                return resolver.requireDhanDefinition(new InstrumentKey(underlying, candidate));
            } catch (IllegalArgumentException ignored) {
                // try next venue
            }
        }
        throw new IllegalArgumentException("Unable to resolve underlying " + underlying + " for option-chain venue " + exchangeSegment);
    }

    private OptionQuote mapLeg(
            String underlying,
            ExchangeSegment exchangeSegment,
            LocalDate expiry,
            long strikePricePaisa,
            OptionType optionType,
            DhanJsonResponse leg
    ) {
        DhanInstrumentDefinition contract = resolver.findOptionContract(underlying, exchangeSegment, expiry, strikePricePaisa, optionType);
        if (contract == null) {
            throw new DhanBrokerException("Option chain leg missing catalog mapping for " + underlying + " " + expiry + " " + strikePricePaisa + " " + optionType);
        }
        DhanJsonResponse greeks = leg.path("greeks");
        return new OptionQuote(
                contract.toInstrument(),
                leg.decimalPrice("last_price", "lastPrice"),
                leg.longValue("oi", "open_interest", "openInterest"),
                leg.longValue("volume"),
                leg.decimalPrice("best_bid_price", "bestBidPrice", "bid_price"),
                leg.longValue("best_bid_quantity", "bestBidQuantity", "bid_quantity"),
                leg.decimalPrice("best_ask_price", "bestAskPrice", "ask_price"),
                leg.longValue("best_ask_quantity", "bestAskQuantity", "ask_quantity"),
                new OptionGreeks(
                        greeks.doubleValue("delta"),
                        greeks.doubleValue("theta"),
                        greeks.doubleValue("gamma"),
                        greeks.doubleValue("vega"),
                        leg.doubleValue("implied_volatility", "impliedVolatility")
                )
        );
    }

    private List<ExchangeSegment> lookupSegments(ExchangeSegment exchangeSegment) {
        return switch (exchangeSegment) {
            case NSE_FNO -> List.of(ExchangeSegment.IDX_I, ExchangeSegment.NSE_EQ, ExchangeSegment.NSE_FNO);
            case BSE_FNO -> List.of(ExchangeSegment.IDX_I, ExchangeSegment.BSE_EQ, ExchangeSegment.BSE_FNO);
            default -> List.of(exchangeSegment);
        };
    }
}
