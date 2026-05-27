package com.tradej.broker.api.model;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;

import java.util.Set;

public record VenueCapability(
        ExchangeSegment exchangeSegment,
        Set<FeedMode> supportedFeedModes,
        boolean supportsDepth20,
        boolean supportsDepth200,
        boolean requiresContractDiscovery,
        MarketSessionPolicy sessionPolicy
) {
}
