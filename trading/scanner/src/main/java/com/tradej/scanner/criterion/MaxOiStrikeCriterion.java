package com.tradej.scanner.criterion;

import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.scanner.model.ScanContext;

public final class MaxOiStrikeCriterion implements ScanCriterion, OptionAwareCriterion {
    private final long minTotalOi;

    public MaxOiStrikeCriterion(long minTotalOi) {
        this.minTotalOi = minTotalOi;
    }

    @Override
    public boolean requiresOptionChain() {
        return true;
    }

    @Override
    public String type() {
        return "max-oi-strike";
    }

    @Override
    public boolean matches(ScanContext context) {
        OptionChainSnapshot chain = context.optionChain();
        if (chain == null || chain.strikes() == null || chain.strikes().isEmpty()) {
            return false;
        }
        return totalOi(chain) >= minTotalOi;
    }

    @Override
    public double score(ScanContext context) {
        OptionChainSnapshot chain = context.optionChain();
        if (chain == null) {
            return 0.0;
        }
        return totalOi(chain);
    }

    @Override
    public String reason(ScanContext context) {
        return type() + "=oi:" + (long) score(context);
    }

    private long totalOi(OptionChainSnapshot chain) {
        long total = 0L;
        for (OptionChainEntry entry : chain.strikes()) {
            if (entry.call() != null) {
                total += entry.call().openInterest();
            }
            if (entry.put() != null) {
                total += entry.put().openInterest();
            }
        }
        return total;
    }
}
