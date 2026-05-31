package com.tradej.core.domain.model;

import java.util.List;

public record MarketDepth(
        Instrument instrument,
        List<DepthLevel> bids,
        List<DepthLevel> asks,
        int levels,
        long timestampMs
) {
}
