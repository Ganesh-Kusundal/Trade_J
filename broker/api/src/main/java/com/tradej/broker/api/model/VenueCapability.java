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
        boolean supportsBracketOrders,
        boolean supportsTrailingStop,
        boolean supportsCoverOrders,
        boolean supportsIceberg,
        boolean supportsGTT,
        MarketSessionPolicy sessionPolicy
) {
    /**
     * Convenience factory used by brokers that do not support advanced order types natively.
     * OMS will orchestrate these synthetically.
     */
    public static VenueCapability withSyntheticAdvancedOrders(
            ExchangeSegment exchangeSegment,
            Set<FeedMode> supportedFeedModes,
            boolean supportsDepth20,
            boolean supportsDepth200,
            boolean requiresContractDiscovery,
            MarketSessionPolicy sessionPolicy
    ) {
        return new VenueCapability(
                exchangeSegment, supportedFeedModes,
                supportsDepth20, supportsDepth200, requiresContractDiscovery,
                false, false, false, false, false,
                sessionPolicy
        );
    }
}
