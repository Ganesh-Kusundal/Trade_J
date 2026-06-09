package com.tradej.hotpath;

import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.model.MarketDepth;

import java.util.Optional;

/**
 * Static factory that extracts the optional {@link MarketDepth} from a
 * {@link MarketTickEvent} and produces a standalone
 * {@link DepthUpdateEvent} when depth data is present and non-empty.
 *
 * <p>Returns {@code null} if the tick carries no depth book or if both
 * the bid and ask sides are empty &mdash; callers should treat a {@code null}
 * return as "no depth update to publish" rather than an error.
 */
public final class DepthUpdateFactory {

    private DepthUpdateFactory() {
        // static-only utility
    }

    /**
     * Converts the embedded market depth from a canonical market tick into
     * a standalone {@link DepthUpdateEvent}.
     *
     * @param tick the canonical market tick that may carry depth data
     * @return a {@link DepthUpdateEvent} if depth is present and non-empty,
     *         or {@code null} if the tick has no depth book or the book is
     *         empty on both sides
     */
    public static DepthUpdateEvent fromMarketTickEvent(MarketTickEvent tick) {
        Optional<MarketDepth> depthOpt = tick.depth();
        if (depthOpt.isEmpty()) {
            return null;
        }
        MarketDepth depth = depthOpt.get();
        if (depth.instrument() == null) {
            return null;
        }
        if (depth.bids().isEmpty() && depth.asks().isEmpty()) {
            return null;
        }
        return new DepthUpdateEvent(
                tick.metadata(),
                tick.symbol(),
                tick.segment(),
                depth.bids(),
                depth.asks(),
                depth.levels(),
                tick.exchangeTimestampEpochMs()
        );
    }
}
