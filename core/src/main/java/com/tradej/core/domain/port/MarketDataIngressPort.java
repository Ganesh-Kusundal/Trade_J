package com.tradej.core.domain.port;

import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.StreamHealthChanged;

/**
 * Port for publishing normalized market data events into the trading pipeline.
 * Implemented by broker adapters and test fixtures.
 */
public interface MarketDataIngressPort {

    void publishMarketTick(MarketTickEvent event);

    void publishDepthUpdate(DepthUpdateEvent event);

    void publishHealth(StreamHealthChanged event);
}
