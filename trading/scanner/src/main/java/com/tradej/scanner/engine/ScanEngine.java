package com.tradej.scanner.engine;

import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.scanner.criterion.CriterionGroup;
import com.tradej.scanner.criterion.OptionAwareCriterion;
import com.tradej.scanner.criterion.ScanCriterion;
import com.tradej.scanner.fetch.OptionChainFetcher;
import com.tradej.scanner.fetch.SnapshotFetcher;
import com.tradej.core.domain.scan.AssetClass;
import com.tradej.scanner.model.ScanAsset;
import com.tradej.scanner.model.ScanContext;
import com.tradej.core.domain.scan.ScanHit;
import com.tradej.scanner.model.ScanMode;
import com.tradej.scanner.model.ScanProfile;
import com.tradej.core.domain.scan.ScanResult;
import com.tradej.core.domain.scan.ScanRun;
import com.tradej.scanner.universe.UniverseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public final class ScanEngine {
    private static final Logger log = LoggerFactory.getLogger(ScanEngine.class);

    private final ScanDependencies dependencies;
    private final UniverseBuilder universeBuilder;
    private final SnapshotFetcher snapshotFetcher;
    private final OptionChainFetcher optionChainFetcher;

    public ScanEngine(ScanDependencies dependencies) {
        this.dependencies = dependencies;
        this.universeBuilder = new UniverseBuilder(
                dependencies.instrumentResolver(),
                dependencies.futuresProvider()
        );
        this.snapshotFetcher = new SnapshotFetcher(dependencies.marketDataProvider());
        this.optionChainFetcher = new OptionChainFetcher(dependencies.optionsProvider());
    }

    public ScanResult run(ScanProfile profile) {
        List<ScanAsset> universe = universeBuilder.build(profile.universe());
        ScanRun run = ScanRun.started(profile.id(), universe.size());
        try {
            ScanCriterion evaluator = composeCriteria(profile);
            int partialFailures = 0;
            Map<InstrumentKey, Quote> quotes = Map.of();
            if (profile.mode() != ScanMode.WS_LIVE) {
                SnapshotFetcher.FetchResult fetchResult = snapshotFetcher.fetch(universe, profile.rest().batchSize());
                quotes = fetchResult.quotes();
                partialFailures += fetchResult.partialFailureCount();
            }
            List<ScanHit> coarseHits = evaluateUniverse(universe, quotes, Map.of(), evaluator);
            Map<String, OptionChainSnapshot> optionChains = Map.of();
            if (needsOptionFinePass(profile, evaluator)) {
                List<String> optionUnderlyings = extractOptionUnderlyings(coarseHits);
                ExchangeSegment fnoSegment = resolveFnoSegment(profile);
                optionChains = optionChainFetcher.fetchForUnderlyings(optionUnderlyings, fnoSegment);
                coarseHits = evaluateUniverse(universe, quotes, optionChains, evaluator);
            }
            int topN = profile.promotion().topN();
            List<ScanHit> ranked = ScanResultRanker.rank(coarseHits, topN > 0 ? topN : coarseHits.size());
            ScanRun completed = run.completed(ranked.size(), partialFailures);
            log.info("Scan profile={} hits={} partialFailures={}", profile.id(), ranked.size(), partialFailures);
            return new ScanResult(completed, ranked);
        } catch (RuntimeException ex) {
            log.error("Scan failed for profile {}: {}", profile.id(), ex.getMessage(), ex);
            return new ScanResult(run.failed(ex.getMessage()), List.of());
        }
    }

    private static ScanCriterion composeCriteria(ScanProfile profile) {
        if (profile.criteria().isEmpty()) {
            return new CriterionGroup(List.of());
        }
        return new CriterionGroup(profile.criteria());
    }

    private static boolean needsOptionFinePass(ScanProfile profile, ScanCriterion evaluator) {
        if (!profile.optionFinePassEnabled()) {
            return false;
        }
        if (profile.mode() == ScanMode.REST_SNAPSHOT && !profile.rest().fetchOptionChainsOnCoarsePass()) {
            return false;
        }
        return hasOptionCriteria(evaluator);
    }

    private static boolean hasOptionCriteria(ScanCriterion criterion) {
        if (criterion instanceof CriterionGroup group) {
            return group.requiresOptionChain();
        }
        // Delegate to the criterion's requiresOptionChain() method rather than
        // fragile string containment check (fixes S-06).
        return criterion instanceof OptionAwareCriterion aware
                && aware.requiresOptionChain();
    }

    private List<ScanHit> evaluateUniverse(
            List<ScanAsset> universe,
            Map<InstrumentKey, Quote> quotes,
            Map<String, OptionChainSnapshot> optionChains,
            ScanCriterion evaluator
    ) {
        List<ScanHit> hits = new ArrayList<>();
        for (ScanAsset asset : universe) {
            Quote quote = quotes.get(asset.key());
            OptionChainSnapshot chain = asset.assetClass() == AssetClass.OPTION
                    ? optionChains.get(asset.underlying())
                    : null;
            ScanContext context = new ScanContext(asset, quote, chain, List.of());
            if (!evaluator.matches(context)) {
                continue;
            }
            hits.add(toHit(asset, context, evaluator));
        }
        return hits;
    }

    private static ScanHit toHit(ScanAsset asset, ScanContext context, ScanCriterion evaluator) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (context.quote() != null) {
            snapshot.put("ltpPaisa", context.quote().ltpPaisa());
            snapshot.put("openPaisa", context.quote().openPaisa());
            snapshot.put("volume", context.quote().volume());
        }
        if (context.optionChain() != null) {
            snapshot.put("spotPricePaisa", context.optionChain().spotPricePaisa());
            snapshot.put("expiry", context.optionChain().expiry().toString());
        }
        return new ScanHit(
                asset.key(),
                asset.assetClass(),
                asset.underlying(),
                evaluator.score(context),
                List.of(evaluator.reason(context)),
                snapshot,
                false
        );
    }

    private static List<String> extractOptionUnderlyings(List<ScanHit> hits) {
        Set<String> underlyings = new LinkedHashSet<>();
        for (ScanHit hit : hits) {
            if (hit.assetClass() == AssetClass.OPTION || hit.assetClass() == AssetClass.EQUITY) {
                underlyings.add(hit.underlying() != null ? hit.underlying() : hit.symbol());
            }
        }
        return List.copyOf(underlyings);
    }

    private static ExchangeSegment resolveFnoSegment(ScanProfile profile) {
        return profile.universe().segments().stream()
                .filter(s -> s == ExchangeSegment.NSE_FNO || s == ExchangeSegment.BSE_FNO || s == ExchangeSegment.MCX_COMM)
                .findFirst()
                .orElse(ExchangeSegment.NSE_FNO);
    }
}
