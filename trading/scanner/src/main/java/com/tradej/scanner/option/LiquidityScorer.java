package com.tradej.scanner.option;

import com.tradej.core.domain.model.OptionQuote;

public final class LiquidityScorer {
    static final double MISSING_SPREAD_PENALTY = 50.0;

    private LiquidityScorer() {
    }

    public record ScoreResult(
            double score,
            double spreadBps,
            boolean passedFilters,
            String reason
    ) {
    }

    public static ScoreResult score(
            OptionQuote quote,
            long minOpenInterest,
            long minVolume,
            double maxSpreadBps,
            boolean strictSpread
    ) {
        long oi = quote.openInterest();
        long volume = quote.volume();
        if (oi < minOpenInterest) {
            return new ScoreResult(0.0, Double.NaN, false, "oi below min " + minOpenInterest);
        }
        if (volume < minVolume) {
            return new ScoreResult(0.0, Double.NaN, false, "volume below min " + minVolume);
        }

        Spread spread = computeSpread(quote);
        if (strictSpread && !spread.hasBidAsk()) {
            return new ScoreResult(0.0, Double.NaN, false, "missing bid/ask");
        }
        if (spread.hasBidAsk() && spread.spreadBps() > maxSpreadBps) {
            return new ScoreResult(0.0, spread.spreadBps(), false, "spread above max " + maxSpreadBps);
        }

        double spreadComponent = spread.hasBidAsk()
                ? Math.max(0.0, 100.0 - spread.spreadBps())
                : Math.max(0.0, 100.0 - MISSING_SPREAD_PENALTY);
        double score = 0.5 * Math.log1p(oi) + 0.3 * Math.log1p(volume) + 0.2 * spreadComponent;
        return new ScoreResult(score, spread.spreadBps(), true, "liquidity");
    }

    private static Spread computeSpread(OptionQuote quote) {
        long bid = quote.bestBidPricePaisa();
        long ask = quote.bestAskPricePaisa();
        if (bid <= 0 || ask <= 0 || ask < bid) {
            long ltp = quote.ltpPaisa();
            if (ltp > 0) {
                return new Spread(false, Double.NaN);
            }
            return new Spread(false, Double.NaN);
        }
        double mid = (bid + ask) / 2.0;
        if (mid <= 0) {
            return new Spread(false, Double.NaN);
        }
        double spreadBps = (ask - bid) / mid * 10_000.0;
        return new Spread(true, spreadBps);
    }

    private record Spread(boolean hasBidAsk, double spreadBps) {
    }
}
