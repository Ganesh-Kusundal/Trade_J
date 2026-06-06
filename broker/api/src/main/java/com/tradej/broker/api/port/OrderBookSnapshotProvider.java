package com.tradej.broker.api.port;

import com.tradej.broker.api.annotation.BrokerInternal;
import com.tradej.core.domain.value.ExchangeSegment;

import java.util.List;
import java.util.Map;

/**
 * Broker-internal SPI for accessing the live order book snapshots maintained
 * by the broker's depth feed.
 *
 * <p>Used by the broker gateway REST surface ({@code /api/v1/market/depth/{symbol}})
 * to serve a consolidated L2 book without re-subscribing to the depth WebSocket.
 *
 * <p>Tagged {@link BrokerInternal} — application code that needs an order book
 * should consume the {@code OrderBookEngine} or the {@code MARKET_DEPTH}
 * gateway topic instead. This SPI is the broker's internal projection of
 * its own depth state.
 */
@BrokerInternal
public interface OrderBookSnapshotProvider {

    /**
     * Get the current order book snapshot for a single instrument.
     *
     * @return snapshot, or {@code null} if no book has been received yet
     */
    Object snapshot(String symbol, ExchangeSegment segment, int levels);

    /**
     * Snapshot every active order book (per broker).
     */
    List<Object> snapshotAll(int levels);

    /**
     * Number of distinct order books currently held in memory.
     */
    int bookCount();

    /**
     * Map of (symbol -> segment) for every active book.
     */
    Map<String, ExchangeSegment> activeBooks();
}
