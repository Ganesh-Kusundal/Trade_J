package com.tradej.institutional.ranking;

import com.tradej.institutional.features.FeaturePipeline;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class RankingEngine {

    private final int maxPerSector;
    private final Map<String, String> sectorMap;

    public RankingEngine(int maxPerSector, Map<String, String> sectorMap) {
        this.maxPerSector = maxPerSector;
        this.sectorMap = sectorMap == null ? Map.of() : sectorMap;
    }

    public List<FeaturePipeline.BarFeatures> rankStocks(List<FeaturePipeline.BarFeatures> bars, int topN) {
        Map<Instant, List<FeaturePipeline.BarFeatures>> byTime = bars.stream()
                .collect(Collectors.groupingBy(FeaturePipeline.BarFeatures::barTime, LinkedHashMap::new, Collectors.toList()));

        List<FeaturePipeline.BarFeatures> ranked = new ArrayList<>();
        for (List<FeaturePipeline.BarFeatures> group : byTime.values()) {
            List<FeaturePipeline.BarFeatures> denseRanked = denseRank(group);
            if (sectorMap.isEmpty()) {
                denseRanked.stream()
                        .filter(bar -> barRank(group, bar) <= topN)
                        .forEach(ranked::add);
            } else {
                ranked.addAll(applySectorFilter(denseRanked, topN));
            }
        }
        ranked.sort(Comparator.comparing(FeaturePipeline.BarFeatures::barTime)
                .thenComparing(bar -> barRankForList(bars, bar)));
        return ranked;
    }

    private static int barRank(List<FeaturePipeline.BarFeatures> group, FeaturePipeline.BarFeatures bar) {
        return (int) group.stream()
                .filter(candidate -> candidate.masterScore() >= bar.masterScore())
                .count();
    }

    private static int barRankForList(List<FeaturePipeline.BarFeatures> all, FeaturePipeline.BarFeatures bar) {
        return (int) all.stream()
                .filter(candidate -> candidate.barTime().equals(bar.barTime())
                        && candidate.masterScore() >= bar.masterScore())
                .count();
    }

    private List<FeaturePipeline.BarFeatures> denseRank(List<FeaturePipeline.BarFeatures> group) {
        List<FeaturePipeline.BarFeatures> sorted = group.stream()
                .sorted(Comparator.comparingDouble(FeaturePipeline.BarFeatures::masterScore).reversed())
                .toList();
        Map<String, Integer> rankByKey = new HashMap<>();
        int rank = 1;
        Double lastScore = null;
        for (FeaturePipeline.BarFeatures bar : sorted) {
            if (lastScore != null && Double.compare(bar.masterScore(), lastScore) != 0) {
                rank++;
            }
            rankByKey.put(key(bar), rank);
            lastScore = bar.masterScore();
        }
        return sorted.stream()
                .filter(bar -> rankByKey.getOrDefault(key(bar), Integer.MAX_VALUE) <= group.size())
                .toList();
    }

    private List<FeaturePipeline.BarFeatures> applySectorFilter(List<FeaturePipeline.BarFeatures> group, int topN) {
        List<FeaturePipeline.BarFeatures> sorted = group.stream()
                .sorted(Comparator.comparingDouble(FeaturePipeline.BarFeatures::masterScore).reversed())
                .toList();
        List<FeaturePipeline.BarFeatures> selected = new ArrayList<>();
        Map<String, Integer> sectorCounts = new HashMap<>();
        for (FeaturePipeline.BarFeatures bar : sorted) {
            String sector = sectorMap.getOrDefault(bar.symbol(), "Unknown");
            int count = sectorCounts.getOrDefault(sector, 0);
            if (count < maxPerSector) {
                selected.add(bar);
                sectorCounts.put(sector, count + 1);
            }
            if (selected.size() >= topN) {
                break;
            }
        }
        return selected;
    }

    private static String key(FeaturePipeline.BarFeatures bar) {
        return bar.symbol() + "|" + bar.barTime().toEpochMilli();
    }
}
