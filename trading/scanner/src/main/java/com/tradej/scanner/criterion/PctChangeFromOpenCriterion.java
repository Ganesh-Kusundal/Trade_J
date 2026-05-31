package com.tradej.scanner.criterion;

import com.tradej.scanner.model.ScanContext;

public final class PctChangeFromOpenCriterion implements ScanCriterion {
    private final double minPct;
    private final Double maxPct;

    public PctChangeFromOpenCriterion(double minPct, Double maxPct) {
        this.minPct = minPct;
        this.maxPct = maxPct;
    }

    @Override
    public String type() {
        return "pct-change-from-open";
    }

    @Override
    public boolean matches(ScanContext context) {
        double pct = pctChange(context);
        if (Double.isNaN(pct)) {
            return false;
        }
        if (pct < minPct) {
            return false;
        }
        return maxPct == null || pct <= maxPct;
    }

    @Override
    public double score(ScanContext context) {
        double pct = pctChange(context);
        return Double.isNaN(pct) ? 0.0 : Math.abs(pct);
    }

    @Override
    public String reason(ScanContext context) {
        return type() + "=" + String.format("%.2f", pctChange(context)) + "%";
    }

    private double pctChange(ScanContext context) {
        if (!context.hasValidQuote()) {
            return Double.NaN;
        }
        long open = context.quote().openPaisa();
        if (open <= 0L) {
            return Double.NaN;
        }
        long ltp = context.quote().ltpPaisa();
        return (ltp - open) * 100.0 / open;
    }
}
