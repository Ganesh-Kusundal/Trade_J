package com.tradej.scanner.criterion;

import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.scanner.model.ScanContext;

public final class PcrRangeCriterion implements ScanCriterion, OptionAwareCriterion {
    private final double minPcr;
    private final double maxPcr;

    public PcrRangeCriterion(double minPcr, double maxPcr) {
        this.minPcr = minPcr;
        this.maxPcr = maxPcr;
    }

    @Override
    public boolean requiresOptionChain() {
        return true;
    }

    @Override
    public String type() {
        return "pcr-range";
    }

    @Override
    public boolean matches(ScanContext context) {
        double pcr = pcr(context);
        if (Double.isNaN(pcr)) {
            return false;
        }
        return pcr >= minPcr && pcr <= maxPcr;
    }

    @Override
    public double score(ScanContext context) {
        double pcr = pcr(context);
        return Double.isNaN(pcr) ? 0.0 : pcr;
    }

    @Override
    public String reason(ScanContext context) {
        return type() + "=" + String.format("%.2f", pcr(context));
    }

    private double pcr(ScanContext context) {
        OptionChainSnapshot chain = context.optionChain();
        if (chain == null || chain.strikes() == null) {
            return Double.NaN;
        }
        long callOi = 0L;
        long putOi = 0L;
        for (OptionChainEntry entry : chain.strikes()) {
            if (entry.call() != null) {
                callOi += entry.call().openInterest();
            }
            if (entry.put() != null) {
                putOi += entry.put().openInterest();
            }
        }
        if (callOi <= 0L) {
            return Double.NaN;
        }
        return (double) putOi / callOi;
    }
}
