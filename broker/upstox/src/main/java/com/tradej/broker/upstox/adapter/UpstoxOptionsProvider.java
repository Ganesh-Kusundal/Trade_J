package com.tradej.broker.upstox.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.core.domain.instrument.ContractSymbolNormalizer;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.OptionGreeks;
import com.tradej.core.domain.model.OptionQuote;
import com.tradej.core.domain.model.RollingOptionHistoryRequest;
import com.tradej.core.domain.model.RollingOptionSeries;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.core.domain.value.StrikeSelectionKind;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.rest.UpstoxOptionChainRestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class UpstoxOptionsProvider implements OptionsProvider {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UpstoxOptionChainRestClient restClient;
    private final UpstoxInstrumentResolver instrumentResolver;

    public UpstoxOptionsProvider(UpstoxOptionChainRestClient restClient,
                                 UpstoxInstrumentResolver instrumentResolver) {
        this.restClient = restClient;
        this.instrumentResolver = instrumentResolver;
    }

    @Override
    public List<LocalDate> getExpiries(String underlying, ExchangeSegment segment) {
        String instrumentKey = instrumentResolver.requireInstrumentKey(new InstrumentKey(
                ContractSymbolNormalizer.normalize(underlying),
                segment
        ));
        JsonNode root = restClient.getExpiries(instrumentKey);
        List<LocalDate> expiries = new ArrayList<>();
        JsonNode data = root.get("data");
        if (data != null && data.isArray()) {
            for (JsonNode node : data) {
                expiries.add(LocalDate.parse(node.asText().substring(0, 10), DATE_FMT));
            }
        }
        return expiries.stream().sorted().distinct().toList();
    }

    @Override
    public List<Instrument> getOptionContracts(String underlying, ExchangeSegment segment, LocalDate expiry) {
        return instrumentResolver.allInstruments().stream()
                .filter(Instrument::isOption)
                .filter(i -> underlying.equalsIgnoreCase(i.underlying()))
                .filter(i -> i.exchangeSegment() == segment)
                .filter(i -> expiry.equals(i.expiry()))
                .toList();
    }

    @Override
    public OptionChainSnapshot getOptionChain(String underlying, ExchangeSegment segment, LocalDate expiry) {
        String instrumentKey = instrumentResolver.requireInstrumentKey(new InstrumentKey(
                ContractSymbolNormalizer.normalize(underlying),
                segment
        ));
        JsonNode root = restClient.getOptionChain(instrumentKey, expiry.format(DATE_FMT));
        JsonNode data = root.get("data");
        if (data == null) {
            var inst = new Instrument(underlying, underlying, null, segment, "EQ", underlying, null, null, null, 1L, 5L);
            return new OptionChainSnapshot(inst, expiry, 0L, List.of());
        }
        Instrument underlyingInst = resolveUnderlying(underlying, segment);
        long spotPrice = data.has("spot_price") ? (long) (data.get("spot_price").asDouble() * 100) : 0L;
        List<OptionChainEntry> strikes = new ArrayList<>();
        JsonNode strikesNode = data.get("strikes");
        if (strikesNode != null && strikesNode.isArray()) {
            for (JsonNode s : strikesNode) {
                long strikePaisa = s.has("strike_price") ? (long) (s.get("strike_price").asDouble() * 100) : 0L;
                Instrument callInst = resolveOptionContract(underlying, segment, expiry, strikePaisa, OptionType.CALL);
                Instrument putInst = resolveOptionContract(underlying, segment, expiry, strikePaisa, OptionType.PUT);
                OptionQuote call = s.has("call") ? parseOptionQuote(s.get("call"), callInst) : null;
                OptionQuote put = s.has("put") ? parseOptionQuote(s.get("put"), putInst) : null;
                strikes.add(new OptionChainEntry(strikePaisa, call, put));
            }
        }
        return new OptionChainSnapshot(underlyingInst, expiry, spotPrice, strikes);
    }

    @Override
    public OptionQuote getGreeks(InstrumentKey instrumentKey) {
        JsonNode root = restClient.getGreeks(instrumentKey.symbol());
        JsonNode data = root.get("data");
        if (data == null) {
            return null;
        }
        Instrument resolved = instrumentResolver.resolve(instrumentKey);
        return parseOptionQuote(data, resolved);
    }

    @Override
    public RollingOptionSeries getExpiredOptionHistory(RollingOptionHistoryRequest request) {
        throw new UnsupportedOperationException(
                "Upstox expired option history uses contract-native APIs; "
                        + "use UpstoxExpiredOptionService or /api/v1/market/expired-options/* instead of rolling-offset semantics"
        );
    }

    @Override
    public long selectStrikePaisa(String underlying, ExchangeSegment segment,
                                  long spotPricePaisa, OptionType optionType,
                                  StrikeSelectionKind selectionKind, int depth) {
        return 0L;
    }

    private Instrument resolveUnderlying(String underlying, ExchangeSegment segment) {
        var resolved = instrumentResolver.resolve(new InstrumentKey(underlying, segment));
        if (resolved != null) {
            return resolved;
        }
        return new Instrument(underlying, underlying, null, segment, "EQ", underlying, null, null, null, 1L, 5L);
    }

    private Instrument resolveOptionContract(String underlying, ExchangeSegment segment,
                                              LocalDate expiry, long strikePaisa, OptionType optionType) {
        return instrumentResolver.allInstruments().stream()
                .filter(Instrument::isOption)
                .filter(i -> underlying.equalsIgnoreCase(i.underlying()))
                .filter(i -> i.exchangeSegment() == segment)
                .filter(i -> expiry.equals(i.expiry()))
                .filter(i -> strikePaisa == (i.strikePricePaisa() != null ? i.strikePricePaisa() : 0L))
                .filter(i -> optionType == i.optionType())
                .findFirst()
                .orElse(null);
    }

    private OptionQuote parseOptionQuote(JsonNode node, Instrument instrument) {
        OptionGreeks greeks = null;
        if (node.has("delta") || node.has("iv") || node.has("gamma") || node.has("theta") || node.has("vega")) {
            greeks = new OptionGreeks(
                    node.has("delta") ? node.get("delta").asDouble() : null,
                    node.has("theta") ? node.get("theta").asDouble() : null,
                    node.has("gamma") ? node.get("gamma").asDouble() : null,
                    node.has("vega") ? node.get("vega").asDouble() : null,
                    node.has("iv") ? node.get("iv").asDouble() / 100.0 : null
            );
        }
        return new OptionQuote(
                instrument,
                node.has("last_price") ? (long) (node.get("last_price").asDouble() * 100) : 0L,
                node.has("open_interest") ? node.get("open_interest").asLong() : 0L,
                node.has("volume") ? node.get("volume").asLong() : 0L,
                node.has("bid_price") ? (long) (node.get("bid_price").asDouble() * 100) : 0L,
                node.has("bid_qty") ? node.get("bid_qty").asLong() : 0L,
                node.has("ask_price") ? (long) (node.get("ask_price").asDouble() * 100) : 0L,
                node.has("ask_qty") ? node.get("ask_qty").asLong() : 0L,
                greeks
        );
    }
}
