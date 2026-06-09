package com.tradej.brokergateway.query;

import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.OptionQuote;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Option chain analytics — compute PCR, top OI, max pain, support/resistance,
 * ITM/OTM classification, and more.
 *
 * <p>All methods are pure functions operating on an {@link OptionChainSnapshot}.
 * No broker calls are made — the chain must be fetched first via {@code BrokerHandle.optionChain()}.
 */
public final class OptionAnalytics {

    private OptionAnalytics() {
    }

    // ── Put-Call Ratio ──────────────────────────────────────────────

    public static PcrResult pcr(OptionChainSnapshot chain) {
        long totalCallOi = 0, totalPutOi = 0;
        long totalCallVolume = 0, totalPutVolume = 0;
        for (OptionChainEntry entry : chain.strikes()) {
            if (entry.call() != null) {
                totalCallOi += entry.call().openInterest();
                totalCallVolume += entry.call().volume();
            }
            if (entry.put() != null) {
                totalPutOi += entry.put().openInterest();
                totalPutVolume += entry.put().volume();
            }
        }
        double ratio = totalCallOi > 0 ? (double) totalPutOi / totalCallOi : 0.0;
        return new PcrResult(ratio, totalCallOi, totalPutOi, totalCallVolume, totalPutVolume);
    }

    // ── Top N by Open Interest ──────────────────────────────────────

    /**
     * Top N call strikes by open interest.
     */
    public static List<StrikeOi> topCallOi(OptionChainSnapshot chain, int n) {
        return chain.strikes().stream()
                .filter(e -> e.call() != null && e.call().openInterest() > 0)
                .map(e -> new StrikeOi(e.strikePricePaisa(), e.call().openInterest(), "CALL"))
                .sorted(Comparator.comparingLong(StrikeOi::openInterest).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    /**
     * Top N put strikes by open interest.
     */
    public static List<StrikeOi> topPutOi(OptionChainSnapshot chain, int n) {
        return chain.strikes().stream()
                .filter(e -> e.put() != null && e.put().openInterest() > 0)
                .map(e -> new StrikeOi(e.strikePricePaisa(), e.put().openInterest(), "PUT"))
                .sorted(Comparator.comparingLong(StrikeOi::openInterest).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    /**
     * Top N strikes by total (call + put) open interest.
     */
    public static List<StrikeOi> topOi(OptionChainSnapshot chain, int n) {
        return chain.strikes().stream()
                .map(e -> new StrikeOi(
                        e.strikePricePaisa(),
                        (e.call() != null ? e.call().openInterest() : 0) + (e.put() != null ? e.put().openInterest() : 0),
                        "TOTAL"))
                .filter(s -> s.openInterest() > 0)
                .sorted(Comparator.comparingLong(StrikeOi::openInterest).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    /**
     * Top N strikes by total volume.
     */
    public static List<StrikeVolume> topVolume(OptionChainSnapshot chain, int n) {
        return chain.strikes().stream()
                .map(e -> new StrikeVolume(
                        e.strikePricePaisa(),
                        (e.call() != null ? e.call().volume() : 0) + (e.put() != null ? e.put().volume() : 0),
                        "TOTAL"))
                .filter(s -> s.volume() > 0)
                .sorted(Comparator.comparingLong(StrikeVolume::volume).reversed())
                .limit(n)
                .toList();
    }

    // ── Max Pain ────────────────────────────────────────────────────

    /**
     * Calculate the max pain strike — the strike where option writers lose the least.
     */
    public static long maxPainStrike(OptionChainSnapshot chain) {
        long minPain = Long.MAX_VALUE;
        long maxPainStrike = 0;

        for (OptionChainEntry candidate : chain.strikes()) {
            long pain = 0;
            long candidateStrike = candidate.strikePricePaisa();
            for (OptionChainEntry entry : chain.strikes()) {
                long strike = entry.strikePricePaisa();
                // Call pain: writers lose when spot > strike
                if (entry.call() != null && candidateStrike > strike) {
                    pain += (candidateStrike - strike) * entry.call().openInterest();
                }
                // Put pain: writers lose when spot < strike
                if (entry.put() != null && candidateStrike < strike) {
                    pain += (strike - candidateStrike) * entry.put().openInterest();
                }
            }
            if (pain < minPain) {
                minPain = pain;
                maxPainStrike = candidateStrike;
            }
        }
        return maxPainStrike;
    }

    // ── Support / Resistance ────────────────────────────────────────

    /**
     * Identify support (highest put OI below spot) and resistance (highest call OI above spot).
     */
    public static SupportResistance supportResistance(OptionChainSnapshot chain) {
        long spot = chain.spotPricePaisa();

        OptionChainEntry support = chain.strikes().stream()
                .filter(e -> e.strikePricePaisa() < spot && e.put() != null)
                .max(Comparator.comparingLong(e -> e.put().openInterest()))
                .orElse(null);

        OptionChainEntry resistance = chain.strikes().stream()
                .filter(e -> e.strikePricePaisa() > spot && e.call() != null)
                .max(Comparator.comparingLong(e -> e.call().openInterest()))
                .orElse(null);

        return new SupportResistance(
                support != null ? support.strikePricePaisa() : 0,
                support != null && support.put() != null ? support.put().openInterest() : 0,
                resistance != null ? resistance.strikePricePaisa() : 0,
                resistance != null && resistance.call() != null ? resistance.call().openInterest() : 0
        );
    }

    // ── ATM ─────────────────────────────────────────────────────────

    /**
     * Find the at-the-money strike (closest to spot).
     */
    public static OptionChainEntry atm(OptionChainSnapshot chain) {
        long spot = chain.spotPricePaisa();
        return chain.strikes().stream()
                .min(Comparator.comparingLong(e -> Math.abs(e.strikePricePaisa() - spot)))
                .orElse(null);
    }

    // ── ITM / OTM Classification ───────────────────────────────────

    public static List<OptionChainEntry> itmCalls(OptionChainSnapshot chain) {
        long spot = chain.spotPricePaisa();
        return chain.strikes().stream()
                .filter(e -> e.strikePricePaisa() < spot)
                .collect(Collectors.toList());
    }

    public static List<OptionChainEntry> otmCalls(OptionChainSnapshot chain) {
        long spot = chain.spotPricePaisa();
        return chain.strikes().stream()
                .filter(e -> e.strikePricePaisa() > spot)
                .collect(Collectors.toList());
    }

    // ── Result records ──────────────────────────────────────────────

    public record PcrResult(
            double ratio,
            long totalCallOi,
            long totalPutOi,
            long totalCallVolume,
            long totalPutVolume
    ) {}

    public record StrikeOi(
            long strikePricePaisa,
            long openInterest,
            String side
    ) {}

    public record StrikeVolume(
            long strikePricePaisa,
            long volume,
            String side
    ) {}

    public record SupportResistance(
            long supportStrikePaisa,
            long supportOi,
            long resistanceStrikePaisa,
            long resistanceOi
    ) {}
}
