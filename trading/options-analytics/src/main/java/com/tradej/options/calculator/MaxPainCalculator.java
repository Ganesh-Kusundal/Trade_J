package com.tradej.options.calculator;

import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;

/**
 * Computes max pain strike from open interest across the option chain.
 */
public final class MaxPainCalculator {

    public record MaxPainResult(long strikePaisa, long totalPainPaisa) {
    }

    private MaxPainCalculator() {
    }

    public static MaxPainResult compute(OptionChainSnapshot chain) {
        if (chain == null || chain.strikes() == null || chain.strikes().isEmpty()) {
            return new MaxPainResult(0L, Long.MAX_VALUE);
        }
        long bestStrike = 0L;
        long minPain = Long.MAX_VALUE;
        for (OptionChainEntry candidate : chain.strikes()) {
            long pain = totalPainAtStrike(chain, candidate.strikePricePaisa());
            if (pain < minPain) {
                minPain = pain;
                bestStrike = candidate.strikePricePaisa();
            }
        }
        return new MaxPainResult(bestStrike, minPain);
    }

    private static long totalPainAtStrike(OptionChainSnapshot chain, long settlementStrikePaisa) {
        long total = 0L;
        for (OptionChainEntry entry : chain.strikes()) {
            long strike = entry.strikePricePaisa();
            long callOi = entry.call() != null ? entry.call().openInterest() : 0L;
            long putOi = entry.put() != null ? entry.put().openInterest() : 0L;
            if (settlementStrikePaisa > strike) {
                total += (settlementStrikePaisa - strike) * callOi;
            }
            if (settlementStrikePaisa < strike) {
                total += (strike - settlementStrikePaisa) * putOi;
            }
        }
        return total;
    }
}
