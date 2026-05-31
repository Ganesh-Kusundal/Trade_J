package com.tradej.scanner.criterion;

import com.tradej.scanner.model.ScanContext;

public final class VolumeSpikeCriterion implements ScanCriterion {
    private final double multiplier;
    private final long minVolume;

    public VolumeSpikeCriterion(double multiplier, long minVolume) {
        this.multiplier = multiplier;
        this.minVolume = minVolume;
    }

    @Override
    public String type() {
        return "volume-spike";
    }

    @Override
    public boolean matches(ScanContext context) {
        if (!context.hasValidQuote()) {
            return false;
        }
        long volume = context.quote().volume();
        if (volume < minVolume) {
            return false;
        }
        if (context.intradayCandles().isEmpty()) {
            return volume >= minVolume;
        }
        double avg = context.intradayCandles().stream()
                .mapToLong(c -> c.volume())
                .filter(v -> v > 0L)
                .average()
                .orElse(0.0);
        if (avg <= 0.0) {
            return volume >= minVolume;
        }
        return volume >= avg * multiplier;
    }

    @Override
    public double score(ScanContext context) {
        if (!context.hasValidQuote()) {
            return 0.0;
        }
        return context.quote().volume();
    }

    @Override
    public String reason(ScanContext context) {
        return type() + "=vol:" + context.quote().volume();
    }
}
