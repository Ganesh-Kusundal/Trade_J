package com.tradej.institutional;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.UniverseEntry;
import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.institutional.features.FeaturePipeline;
import com.tradej.institutional.model.InstitutionalScanConfig;
import com.tradej.institutional.model.InstitutionalScanResult;
import com.tradej.institutional.model.ScoredBar;
import com.tradej.institutional.sector.SectorRankingEngine;
import com.tradej.institutional.selection.CandidateSelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class InstitutionalScanEngine {

    private static final Logger log = LoggerFactory.getLogger(InstitutionalScanEngine.class);

    private final HistoricalBarRepository barRepository;
    private final InstitutionalScanConfig config;

    public InstitutionalScanEngine(HistoricalBarRepository barRepository, InstitutionalScanConfig config) {
        this.barRepository = barRepository;
        this.config = config == null ? InstitutionalScanConfig.baseline() : config;
    }

    public InstitutionalScanEngine(HistoricalBarRepository barRepository) {
        this(barRepository, InstitutionalScanConfig.baseline());
    }

    public InstitutionalScanResult runHistoricalScan(LocalDate date, String scanTime) {
        String effectiveScanTime = scanTime == null || scanTime.isBlank()
                ? config.defaultScanTime()
                : scanTime;

        List<String> symbols = barRepository.querySymbolsWithDataOn(date, config.nStocks());
        if (symbols.isEmpty()) {
            throw new IllegalStateException("No symbols with parquet data on " + date);
        }

        Map<String, String> industryMap = barRepository.queryUniverse().stream()
                .collect(Collectors.toMap(
                        UniverseEntry::symbol,
                        entry -> entry.industry() == null || entry.industry().isBlank()
                                ? "Unknown"
                                : entry.industry(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<Candle> stockBars = barRepository.queryIntradayBars(symbols, date);
        if (stockBars.isEmpty()) {
            throw new IllegalStateException("No intraday parquet data for " + date);
        }

        List<Candle> benchmarkBars = barRepository.queryBenchmarkBars(date, "NIFTY");
        if (benchmarkBars.isEmpty()) {
            log.warn("No benchmark parquet data for {} — RS will be neutral", date);
        }

        List<FeaturePipeline.BarFeatures> features = FeaturePipeline.computeAllFeatures(
                stockBars,
                benchmarkBars,
                config.masterScoreWeights()
        );

        Map<String, Double> sectorMomentum = SectorRankingEngine.computeSectorMomentum(features, industryMap);
        if (!sectorMomentum.isEmpty()) {
            features = SectorRankingEngine.applySectorPenalty(features, industryMap, sectorMomentum);
        }

        CandidateSelection selector = new CandidateSelection(
                config.topN(),
                industryMap,
                config.maxPerSector()
        );
        CandidateSelection.SelectionResult selection = selector.selectCandidates(features, date, effectiveScanTime);
        String resolvedScanTime = selection.effectiveScanTime() == null || selection.effectiveScanTime().isBlank()
                ? effectiveScanTime
                : selection.effectiveScanTime();

        List<ScoredBar> candidates = new ArrayList<>();
        int rank = 1;
        for (FeaturePipeline.BarFeatures bar : selection.candidates()) {
            candidates.add(new ScoredBar(
                    bar.symbol(),
                    bar.barTime(),
                    bar.closePaisa(),
                    bar.highPaisa(),
                    bar.lowPaisa(),
                    bar.openPaisa(),
                    bar.volume(),
                    bar.rsScore(),
                    bar.volumeExpansionScore(),
                    bar.trendEfficiencyScore(),
                    bar.openingDriveScore(),
                    bar.masterScore(),
                    rank++
            ));
        }

        return new InstitutionalScanResult(
                date.toString(),
                resolvedScanTime,
                "baseline",
                candidates,
                selection.provenance()
        );
    }
}
