package com.tradej.institutional.selection;

import com.tradej.institutional.features.FeaturePipeline;
import com.tradej.institutional.ranking.RankingEngine;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class CandidateSelection {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int topN;
    private final RankingEngine rankingEngine;

    public CandidateSelection(int topN, Map<String, String> sectorMap, int maxPerSector) {
        this.topN = topN;
        this.rankingEngine = new RankingEngine(maxPerSector, sectorMap);
    }

    public SelectionResult selectCandidates(List<FeaturePipeline.BarFeatures> bars, LocalDate date, String scanTime) {
        List<FeaturePipeline.BarFeatures> ranked = rankingEngine.rankStocks(bars, topN);
        Instant cutoff = parseCutoff(date, scanTime);

        CutoffSelection selection = selectAtCutoff(ranked, bars, date, cutoff);
        List<FeaturePipeline.BarFeatures> filtered = selection.selected();

        Map<String, String> provenance = new LinkedHashMap<>();
        provenance.put("selectionMode", "baseline");
        provenance.put("artifactVersion", "");
        if (scanTime != null && !scanTime.isBlank()) {
            provenance.put("requestedScanTime", scanTime);
        }
        if (selection.fallbackReason() != null) {
            provenance.put("fallbackReason", selection.fallbackReason());
        }

        String effectiveScanTime = selection.selectedTime() == null
                ? scanTime
                : LocalTime.ofInstant(selection.selectedTime(), IST).format(TIME_FORMAT);
        return new SelectionResult(filtered, provenance, effectiveScanTime);
    }

    private static CutoffSelection selectAtCutoff(
            List<FeaturePipeline.BarFeatures> ranked,
            List<FeaturePipeline.BarFeatures> bars,
            LocalDate date,
            Instant cutoff
    ) {
        if (cutoff == null) {
            return new CutoffSelection(ranked, null, null);
        }

        List<FeaturePipeline.BarFeatures> exact = filterRankedAtTime(ranked, cutoff);
        if (!exact.isEmpty()) {
            return new CutoffSelection(exact, cutoff, null);
        }

        Instant asOfCutoff = latestBarTimeOnDate(bars, date, time -> !time.isAfter(cutoff));
        if (asOfCutoff != null) {
            List<FeaturePipeline.BarFeatures> asOf = filterRankedAtTime(ranked, asOfCutoff);
            if (!asOf.isEmpty()) {
                return new CutoffSelection(asOf, asOfCutoff, "used_latest_bar_at_or_before_cutoff");
            }
        }

        Instant firstAfterCutoff = earliestBarTimeOnDate(bars, date, time -> !time.isBefore(cutoff));
        if (firstAfterCutoff != null) {
            List<FeaturePipeline.BarFeatures> afterCutoff = filterRankedAtTime(ranked, firstAfterCutoff);
            if (!afterCutoff.isEmpty()) {
                return new CutoffSelection(afterCutoff, firstAfterCutoff, "used_first_bar_after_cutoff");
            }
        }

        return new CutoffSelection(List.of(), cutoff, "no_bars_on_scan_date");
    }

    private static List<FeaturePipeline.BarFeatures> filterRankedAtTime(
            List<FeaturePipeline.BarFeatures> ranked,
            Instant time
    ) {
        return ranked.stream()
                .filter(bar -> bar.barTime().equals(time))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static Instant parseCutoff(LocalDate date, String scanTime) {
        if (scanTime == null || scanTime.isBlank() || date == null) {
            return null;
        }
        LocalTime time = LocalTime.parse(scanTime);
        return date.atTime(time).atZone(IST).toInstant();
    }

    private static Instant latestBarTimeOnDate(
            List<FeaturePipeline.BarFeatures> bars,
            LocalDate date,
            java.util.function.Predicate<Instant> predicate
    ) {
        return bars.stream()
                .map(FeaturePipeline.BarFeatures::barTime)
                .filter(time -> time.atZone(IST).toLocalDate().equals(date))
                .filter(predicate)
                .max(Instant::compareTo)
                .orElse(null);
    }

    private static Instant earliestBarTimeOnDate(
            List<FeaturePipeline.BarFeatures> bars,
            LocalDate date,
            java.util.function.Predicate<Instant> predicate
    ) {
        return bars.stream()
                .map(FeaturePipeline.BarFeatures::barTime)
                .filter(time -> time.atZone(IST).toLocalDate().equals(date))
                .filter(predicate)
                .min(Instant::compareTo)
                .orElse(null);
    }

    private record CutoffSelection(
            List<FeaturePipeline.BarFeatures> selected,
            Instant selectedTime,
            String fallbackReason
    ) {
    }

    public record SelectionResult(
            List<FeaturePipeline.BarFeatures> candidates,
            Map<String, String> provenance,
            String effectiveScanTime
    ) {
    }
}
