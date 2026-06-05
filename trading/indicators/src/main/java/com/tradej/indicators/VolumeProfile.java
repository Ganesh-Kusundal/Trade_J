package com.tradej.indicators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Volume Profile: price-at-volume distribution.
 * Identifies POC (Point of Control), Value Area High/Low, HVN/LVN.
 *
 * <p>Usage:
 * <pre>{@code
 * VolumeProfile vp = new VolumeProfile(100); // 100 paisa tick size
 * vp.addTrade(250000, 500);  // price 2500.00, volume 500
 * vp.addTrade(250100, 300);  // price 2501.00, volume 300
 *
 * long poc = vp.pointOfControl();       // price with highest volume
 * long vah = vp.valueAreaHigh();        // 70% volume upper bound
 * long val = vp.valueAreaLow();         // 70% volume lower bound
 * List<Long> hvn = vp.highVolumeNodes(); // prices with volume > 2x average
 * List<Long> lvn = vp.lowVolumeNodes();  // prices with volume < 0.5x average
 * }</pre>
 */
public final class VolumeProfile {

    private final long tickSizePaisa;
    private final TreeMap<Long, Long> priceToVolume = new TreeMap<>();
    private long totalVolume;
    private long highPrice = Long.MIN_VALUE;
    private long lowPrice = Long.MAX_VALUE;

    public VolumeProfile(long tickSizePaisa) {
        this.tickSizePaisa = tickSizePaisa;
    }

    /**
     * Add a trade at the given price level.
     * Price is snapped to the nearest tick.
     */
    public void addTrade(long pricePaisa, long volume) {
        long snapped = snapToTick(pricePaisa);
        priceToVolume.merge(snapped, volume, Long::sum);
        totalVolume += volume;
        highPrice = Math.max(highPrice, snapped);
        lowPrice = Math.min(lowPrice, snapped);
    }

    /**
     * Point of Control: price level with the highest volume.
     */
    public long pointOfControl() {
        if (priceToVolume.isEmpty()) return 0;
        return priceToVolume.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0L);
    }

    /**
     * Value Area: the price range containing 70% of total volume,
     * centered around the POC.
     */
    public long valueAreaHigh() {
        return valueAreaBounds().high;
    }

    public long valueAreaLow() {
        return valueAreaBounds().low;
    }

    /**
     * High Volume Nodes: prices with volume > 2x the average.
     */
    public List<Long> highVolumeNodes() {
        if (priceToVolume.isEmpty()) return List.of();
        double avg = (double) totalVolume / priceToVolume.size();
        double threshold = avg * 2.0;
        List<Long> hvn = new ArrayList<>();
        for (var entry : priceToVolume.entrySet()) {
            if (entry.getValue() >= threshold) {
                hvn.add(entry.getKey());
            }
        }
        return Collections.unmodifiableList(hvn);
    }

    /**
     * Low Volume Nodes: prices with volume < 0.5x the average.
     */
    public List<Long> lowVolumeNodes() {
        if (priceToVolume.isEmpty()) return List.of();
        double avg = (double) totalVolume / priceToVolume.size();
        double threshold = avg * 0.5;
        List<Long> lvn = new ArrayList<>();
        for (var entry : priceToVolume.entrySet()) {
            if (entry.getValue() <= threshold) {
                lvn.add(entry.getKey());
            }
        }
        return Collections.unmodifiableList(lvn);
    }

    /**
     * Volume at a specific price level.
     */
    public long volumeAt(long pricePaisa) {
        return priceToVolume.getOrDefault(snapToTick(pricePaisa), 0L);
    }

    /**
     * Total volume across all price levels.
     */
    public long totalVolume() {
        return totalVolume;
    }

    /**
     * Number of distinct price levels.
     */
    public int priceLevelCount() {
        return priceToVolume.size();
    }

    /**
     * Highest price in the profile.
     */
    public long highPrice() {
        return highPrice == Long.MIN_VALUE ? 0 : highPrice;
    }

    /**
     * Lowest price in the profile.
     */
    public long lowPrice() {
        return lowPrice == Long.MAX_VALUE ? 0 : lowPrice;
    }

    /**
     * Reset the profile.
     */
    public void reset() {
        priceToVolume.clear();
        totalVolume = 0;
        highPrice = Long.MIN_VALUE;
        lowPrice = Long.MAX_VALUE;
    }

    /**
     * Get all price levels and their volumes (for rendering).
     */
    public Map<Long, Long> priceLevels() {
        return Collections.unmodifiableMap(priceToVolume);
    }

    private long snapToTick(long pricePaisa) {
        return (pricePaisa / tickSizePaisa) * tickSizePaisa;
    }

    private ValueAreaBounds valueAreaBounds() {
        if (priceToVolume.isEmpty()) return new ValueAreaBounds(0, 0);

        long poc = pointOfControl();
        long targetVolume = (long) (totalVolume * 0.70);
        long accumulated = priceToVolume.getOrDefault(poc, 0L);

        // Expand outward from POC
        Long lower = poc;
        Long upper = poc;

        while (accumulated < targetVolume) {
            Long nextLower = priceToVolume.lowerKey(lower);
            Long nextUpper = priceToVolume.higherKey(upper);

            long lowerVol = nextLower != null ? priceToVolume.get(nextLower) : 0;
            long upperVol = nextUpper != null ? priceToVolume.get(nextUpper) : 0;

            if (nextLower == null && nextUpper == null) break;

            if (nextUpper == null || (nextLower != null && lowerVol >= upperVol)) {
                accumulated += lowerVol;
                lower = nextLower;
            } else {
                accumulated += upperVol;
                upper = nextUpper;
            }
        }

        return new ValueAreaBounds(lower != null ? lower : lowPrice, upper != null ? upper : highPrice);
    }

    private record ValueAreaBounds(long low, long high) {}
}
