package com.tradej.institutional.features;

import com.tradej.core.domain.model.Candle;
import com.tradej.institutional.model.InstitutionalScanConfig;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class FeaturePipeline {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime OPENING_RANGE_END = LocalTime.of(9, 30);
    private static final int RS_LOOKBACK = 15;
    private static final int TREND_WINDOW = 15;

    private FeaturePipeline() {
    }

    public record BarFeatures(
            String symbol,
            Instant barTime,
            long closePaisa,
            long highPaisa,
            long lowPaisa,
            long openPaisa,
            long volume,
            double rsScore,
            double volumeExpansionScore,
            double trendEfficiencyScore,
            double openingDriveScore,
            double bidAskImbalanceScore,
            double deltaExpansionScore,
            double volatilityExpansionScore,
            double liquidityScore,
            double masterScore
    ) {
    }

    public static List<BarFeatures> computeAllFeatures(
            List<Candle> stockBars,
            List<Candle> benchmarkBars,
            Map<String, Double> weights
    ) {
        Map<String, List<Candle>> bySymbol = stockBars.stream()
                .collect(Collectors.groupingBy(Candle::symbol, LinkedHashMap::new, Collectors.toList()));
        Map<Instant, Double> benchmarkReturns = computeReturns(benchmarkBars, RS_LOOKBACK);

        List<BarFeatures> raw = new ArrayList<>();
        for (Map.Entry<String, List<Candle>> entry : bySymbol.entrySet()) {
            List<Candle> bars = entry.getValue().stream()
                    .sorted(Comparator.comparingLong(Candle::startTimeMs))
                    .toList();
            raw.addAll(computeSymbolFeatures(entry.getKey(), bars, benchmarkReturns));
        }

        Map<Instant, List<BarFeatures>> byTime = raw.stream()
                .collect(Collectors.groupingBy(BarFeatures::barTime));
        List<BarFeatures> normalized = new ArrayList<>();
        for (List<BarFeatures> group : byTime.values()) {
            normalized.addAll(normalizeCrossSection(group, weights == null ? defaultWeights() : weights));
        }
        normalized.sort(Comparator.comparing(BarFeatures::barTime).thenComparing(BarFeatures::symbol));
        return normalized;
    }

    private static List<BarFeatures> computeSymbolFeatures(
            String symbol,
            List<Candle> bars,
            Map<Instant, Double> benchmarkReturns
    ) {
        List<BarFeatures> result = new ArrayList<>(bars.size());
        Map<LocalTime, List<Long>> volumeByTime = new HashMap<>();

        for (int i = 0; i < bars.size(); i++) {
            Candle candle = bars.get(i);
            Instant barTime = Instant.ofEpochMilli(candle.startTimeMs());
            LocalTime time = barTime.atZone(IST).toLocalTime();
            volumeByTime.computeIfAbsent(time, ignored -> new ArrayList<>()).add(candle.volume());

            double stockReturn = returnAt(bars, i, RS_LOOKBACK);
            double benchReturn = benchmarkReturns.getOrDefault(barTime, 0.0);
            double rsScore = safeZ(List.of(stockReturn - benchReturn));

            double volumeExpansion = volumeExpansionScore(candle, volumeByTime.get(time));
            double trendEfficiency = trendEfficiency(bars, i, TREND_WINDOW);
            double openingDrive = openingDrive(bars, i, barTime.atZone(IST).toLocalDate());
            double imbalance = candle.closePaisa() >= candle.openPaisa() ? 1.0 : -1.0;
            double deltaExpansion = Math.abs(stockReturn);
            double volatilityExpansion = rangeRatio(candle);
            double liquidity = liquidityScore(candle);

            result.add(new BarFeatures(
                    symbol,
                    barTime,
                    candle.closePaisa(),
                    candle.highPaisa(),
                    candle.lowPaisa(),
                    candle.openPaisa(),
                    candle.volume(),
                    rsScore,
                    volumeExpansion,
                    trendEfficiency,
                    openingDrive,
                    imbalance,
                    deltaExpansion,
                    volatilityExpansion,
                    liquidity,
                    0.0
            ));
        }
        return result;
    }

    private static List<BarFeatures> normalizeCrossSection(List<BarFeatures> group, Map<String, Double> weights) {
        Map<String, Double> volNorm = zscoreMap(group, BarFeatures::volumeExpansionScore);
        Map<String, Double> trendNorm = zscoreMap(group, BarFeatures::trendEfficiencyScore);
        Map<String, Double> volExpNorm = zscoreMap(group, BarFeatures::volatilityExpansionScore);
        Map<String, Double> liqNorm = zscoreMap(group, BarFeatures::liquidityScore);

        List<BarFeatures> result = new ArrayList<>(group.size());
        for (BarFeatures bar : group) {
            String key = bar.symbol() + "|" + bar.barTime().toEpochMilli();
            double orderflow = 0.6 * bar.bidAskImbalanceScore() + 0.4 * bar.deltaExpansionScore();
            double master = weights.getOrDefault("rs", 0.25) * bar.rsScore()
                    + weights.getOrDefault("volume", 0.20) * volNorm.getOrDefault(key, 0.0)
                    + weights.getOrDefault("opening", 0.15) * bar.openingDriveScore()
                    + weights.getOrDefault("sector", 0.10) * 0.0
                    + weights.getOrDefault("trend", 0.10) * trendNorm.getOrDefault(key, 0.0)
                    + weights.getOrDefault("orderflow", 0.10) * orderflow
                    + weights.getOrDefault("volatility", 0.05) * volExpNorm.getOrDefault(key, 0.0)
                    + weights.getOrDefault("liquidity", 0.05) * liqNorm.getOrDefault(key, 0.0);
            result.add(new BarFeatures(
                    bar.symbol(), bar.barTime(), bar.closePaisa(), bar.highPaisa(), bar.lowPaisa(),
                    bar.openPaisa(), bar.volume(), bar.rsScore(), bar.volumeExpansionScore(),
                    bar.trendEfficiencyScore(), bar.openingDriveScore(), bar.bidAskImbalanceScore(),
                    bar.deltaExpansionScore(), bar.volatilityExpansionScore(), bar.liquidityScore(), master
            ));
        }
        return result;
    }

    private static Map<String, Double> zscoreMap(List<BarFeatures> group, java.util.function.ToDoubleFunction<BarFeatures> extractor) {
        double mean = group.stream().mapToDouble(extractor).average().orElse(0.0);
        double std = Math.sqrt(group.stream()
                .mapToDouble(value -> {
                    double delta = extractor.applyAsDouble(value) - mean;
                    return delta * delta;
                })
                .average()
                .orElse(0.0));
        Map<String, Double> map = new HashMap<>();
        for (BarFeatures bar : group) {
            String key = bar.symbol() + "|" + bar.barTime().toEpochMilli();
            map.put(key, std == 0.0 ? 0.0 : (extractor.applyAsDouble(bar) - mean) / std);
        }
        return map;
    }

    private static Map<Instant, Double> computeReturns(List<Candle> benchmarkBars, int lookback) {
        List<Candle> sorted = benchmarkBars.stream()
                .sorted(Comparator.comparingLong(Candle::startTimeMs))
                .toList();
        Map<Instant, Double> returns = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            returns.put(Instant.ofEpochMilli(sorted.get(i).startTimeMs()), returnAt(sorted, i, lookback));
        }
        return returns;
    }

    private static double returnAt(List<Candle> bars, int index, int lookback) {
        if (index < lookback || bars.get(index - lookback).closePaisa() == 0) {
            return 0.0;
        }
        long prev = bars.get(index - lookback).closePaisa();
        long curr = bars.get(index).closePaisa();
        return (curr - prev) / (double) prev;
    }

    private static double volumeExpansionScore(Candle candle, List<Long> sameTimeVolumes) {
        if (sameTimeVolumes == null || sameTimeVolumes.size() < 2) {
            return 1.0;
        }
        double avg = sameTimeVolumes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        if (avg <= 0) {
            return 1.0;
        }
        return Math.min(5.0, candle.volume() / avg);
    }

    private static double trendEfficiency(List<Candle> bars, int index, int window) {
        int start = Math.max(0, index - window + 1);
        long net = bars.get(index).closePaisa() - bars.get(start).openPaisa();
        long path = 0;
        for (int i = start + 1; i <= index; i++) {
            path += Math.abs(bars.get(i).closePaisa() - bars.get(i - 1).closePaisa());
        }
        if (path == 0) {
            return 0.0;
        }
        return net / (double) path;
    }

    private static double openingDrive(List<Candle> bars, int index, LocalDate date) {
        long sessionOpen = 0;
        for (Candle bar : bars) {
            Instant time = Instant.ofEpochMilli(bar.startTimeMs());
            LocalTime localTime = time.atZone(IST).toLocalTime();
            if (time.atZone(IST).toLocalDate().equals(date) && !localTime.isBefore(MARKET_OPEN)) {
                sessionOpen = bar.openPaisa();
                break;
            }
        }
        if (sessionOpen == 0) {
            return 0.0;
        }
        Candle current = bars.get(index);
        LocalTime currentTime = Instant.ofEpochMilli(current.startTimeMs()).atZone(IST).toLocalTime();
        if (currentTime.isAfter(OPENING_RANGE_END)) {
            return 0.0;
        }
        return (current.closePaisa() - sessionOpen) / (double) sessionOpen;
    }

    private static double rangeRatio(Candle candle) {
        if (candle.lowPaisa() <= 0) {
            return 0.0;
        }
        return (candle.highPaisa() - candle.lowPaisa()) / (double) candle.lowPaisa();
    }

    private static double liquidityScore(Candle candle) {
        long range = Math.max(1, candle.highPaisa() - candle.lowPaisa());
        double position = (candle.closePaisa() - candle.lowPaisa()) / (double) range;
        return position * Math.log1p(candle.volume());
    }

    private static double safeZ(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double std = Math.sqrt(values.stream()
                .mapToDouble(v -> {
                    double d = v - mean;
                    return d * d;
                })
                .average()
                .orElse(0.0));
        return std == 0.0 ? 0.0 : (values.getFirst() - mean) / std;
    }

    private static Map<String, Double> defaultWeights() {
        return InstitutionalScanConfig.baseline().masterScoreWeights();
    }
}
