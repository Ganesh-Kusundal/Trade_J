package com.tradej.scanner.option;

import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.OptionQuote;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.scanner.fetch.OptionChainFetcher;
import com.tradej.scanner.model.OptionScanSpec;
import com.tradej.scanner.model.ScanProfile;
import com.tradej.scanner.model.ScanResult;
import com.tradej.scanner.model.ScanRun;
import com.tradej.scanner.model.UniverseSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class OptionLiquidityScanner {
    private static final Logger log = LoggerFactory.getLogger(OptionLiquidityScanner.class);
    private static final Set<String> INDEX_UNDERLYINGS = Set.of(
            "NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY", "SENSEX", "BANKEX"
    );

    private final OptionsProvider optionsProvider;
    private final OptionChainFetcher chainFetcher;

    public OptionLiquidityScanner(OptionsProvider optionsProvider) {
        this.optionsProvider = optionsProvider;
        this.chainFetcher = new OptionChainFetcher(optionsProvider);
    }

    public OptionScanResult scan(OptionScanRequest request) {
        ScanRun run = ScanRun.started("option-scan:" + request.underlying(), 1);
        try {
            OptionChainSnapshot chain = chainFetcher.fetchChain(
                    request.underlying(),
                    request.exchangeSegment(),
                    request.spec().expiryPolicy(),
                    request.spec().explicitExpiry()
            );
            List<OptionContractHit> ranked = rankContracts(
                    request.underlying(),
                    request.exchangeSegment(),
                    chain,
                    request.spec()
            );
            ScanRun completed = run.completed(ranked.size(), 0);
            return new OptionScanResult(
                    request.underlying(),
                    chain.expiry(),
                    completed,
                    ranked,
                    0
            );
        } catch (RuntimeException ex) {
            log.warn("Option scan failed for {}: {}", request.underlying(), ex.getMessage());
            return new OptionScanResult(
                    request.underlying(),
                    null,
                    run.failed(ex.getMessage()),
                    List.of(),
                    1
            );
        }
    }

    public ScanResult scanProfile(ScanProfile profile) {
        OptionScanSpec spec = profile.optionScan();
        if (spec == null) {
            throw new IllegalArgumentException("Profile " + profile.id() + " is not an option scan profile");
        }
        UniverseSpec universe = profile.universe();
        ScanRun run = ScanRun.started(profile.id(), universe.underlyings().size());
        List<OptionContractHit> allHits = new ArrayList<>();
        int partialFailures = 0;

        for (String underlying : universe.underlyings()) {
            ExchangeSegment segment = resolveSegment(underlying, universe.segments());
            try {
                OptionChainSnapshot chain = chainFetcher.fetchChain(
                        underlying,
                        segment,
                        spec.expiryPolicy(),
                        spec.explicitExpiry()
                );
                allHits.addAll(rankContracts(underlying, segment, chain, spec));
            } catch (RuntimeException ex) {
                partialFailures++;
                log.warn("Option scan failed for {} on {}: {}", underlying, segment, ex.getMessage());
            }
        }

        allHits.sort(Comparator
                .comparingDouble(OptionContractHit::liquidityScore).reversed()
                .thenComparing(OptionContractHit::underlying)
                .thenComparingLong(OptionContractHit::strikePricePaisa));

        if (spec.topNGlobal() > 0 && allHits.size() > spec.topNGlobal()) {
            allHits = new ArrayList<>(allHits.subList(0, spec.topNGlobal()));
        }

        OptionScanResult optionResult = new OptionScanResult(
                profile.id(),
                null,
                run,
                allHits,
                partialFailures
        );
        return optionResult.toScanResult();
    }

    private List<OptionContractHit> rankContracts(
            String underlying,
            ExchangeSegment segment,
            OptionChainSnapshot chain,
            OptionScanSpec spec
    ) {
        List<OptionContractHit> hits = new ArrayList<>();
        for (OptionChainEntry entry : chain.strikes()) {
            if (entry.call() != null && spec.sides().includes(OptionType.CALL)) {
                addLeg(hits, underlying, segment, chain, entry.strikePricePaisa(), OptionType.CALL, entry.call(), spec);
            }
            if (entry.put() != null && spec.sides().includes(OptionType.PUT)) {
                addLeg(hits, underlying, segment, chain, entry.strikePricePaisa(), OptionType.PUT, entry.put(), spec);
            }
        }
        hits.sort(Comparator
                .comparingDouble(OptionContractHit::liquidityScore).reversed()
                .thenComparingLong(OptionContractHit::strikePricePaisa));
        int limit = spec.topNPerUnderlying() > 0 ? spec.topNPerUnderlying() : hits.size();
        if (hits.size() <= limit) {
            return hits;
        }
        return new ArrayList<>(hits.subList(0, limit));
    }

    private void addLeg(
            List<OptionContractHit> hits,
            String underlying,
            ExchangeSegment segment,
            OptionChainSnapshot chain,
            long strikePricePaisa,
            OptionType optionType,
            OptionQuote quote,
            OptionScanSpec spec
    ) {
        LiquidityScorer.ScoreResult scored = LiquidityScorer.score(
                quote,
                spec.minOpenInterest(),
                spec.minVolume(),
                spec.maxSpreadBps(),
                spec.strictSpread()
        );
        if (!scored.passedFilters()) {
            return;
        }
        InstrumentKey key = resolveKey(quote, underlying, segment, chain.expiry(), strikePricePaisa, optionType);
        hits.add(new OptionContractHit(
                key,
                underlying,
                chain.expiry(),
                strikePricePaisa,
                optionType,
                quote.openInterest(),
                quote.volume(),
                scored.spreadBps(),
                quote.ltpPaisa(),
                scored.score(),
                List.of("option-liquidity", scored.reason())
        ));
    }

    private InstrumentKey resolveKey(
            OptionQuote quote,
            String underlying,
            ExchangeSegment segment,
            java.time.LocalDate expiry,
            long strikePricePaisa,
            OptionType optionType
    ) {
        Instrument instrument = quote.instrument();
        if (instrument != null) {
            return instrument.key();
        }
        return OptionContractHit.syntheticKey(underlying, segment, expiry, strikePricePaisa, optionType);
    }

    static ExchangeSegment resolveSegment(String underlying, List<ExchangeSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return ExchangeSegment.NSE_FNO;
        }
        String normalized = underlying.trim().toUpperCase(Locale.ROOT);
        if (INDEX_UNDERLYINGS.contains(normalized) && segments.contains(ExchangeSegment.IDX_I)) {
            return ExchangeSegment.IDX_I;
        }
        if (segments.contains(ExchangeSegment.NSE_FNO)) {
            return ExchangeSegment.NSE_FNO;
        }
        return segments.getFirst();
    }
}
