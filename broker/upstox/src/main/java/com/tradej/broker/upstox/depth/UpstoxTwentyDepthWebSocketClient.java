package com.tradej.broker.upstox.depth;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.value.FeedMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dedicated depth-of-book client for Upstox L2 20-level market depth.
 *
 * <p>Subscribes to the {@link FeedMode#DEPTH_20} feed via the shared
 * {@link WebSocketMultiplexer} and captures {@link DepthUpdateEvent}s
 * to maintain the latest 20-deep snapshot per instrument.
 *
 * <p>The Upstox WebSocket protocol delivers depth data inside
 * {@code MarketFullFeed.marketLevel} when subscribed with the
 * {@code full_d30} mode. This client extracts bid/ask levels and
 * stores them as {@link MarketDepth} snapshots.
 */
public final class UpstoxTwentyDepthWebSocketClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(UpstoxTwentyDepthWebSocketClient.class);

    private final WebSocketMultiplexer multiplexer;
    private final UpstoxInstrumentResolver instrumentResolver;
    private final ConcurrentHashMap<String, MarketDepth> depthSnapshots = new ConcurrentHashMap<>();

    public UpstoxTwentyDepthWebSocketClient(
            WebSocketMultiplexer multiplexer,
            UpstoxInstrumentResolver instrumentResolver
    ) {
        this.multiplexer = multiplexer;
        this.instrumentResolver = instrumentResolver;
        multiplexer.onMarketData(this::handleMarketEvent);
    }

    public void subscribe(InstrumentKey instrumentKey) {
        String upstoxKey = instrumentResolver.requireInstrumentKey(instrumentKey);
        multiplexer.subscribe(
                List.of(new MarketSubscriptionRequest(instrumentKey.symbol(), instrumentKey.exchangeSegment())),
                FeedMode.DEPTH_20
        );
    }

    public void subscribeAll(List<InstrumentKey> instrumentKeys) {
        for (InstrumentKey key : instrumentKeys) {
            subscribe(key);
        }
    }

    public void unsubscribe(InstrumentKey instrumentKey) {
        multiplexer.unsubscribe(
                List.of(new MarketSubscriptionRequest(instrumentKey.symbol(), instrumentKey.exchangeSegment()))
        );
    }

    public Optional<MarketDepth> depthFor(InstrumentKey instrumentKey) {
        return Optional.ofNullable(depthSnapshots.get(bookKey(instrumentKey)));
    }

    public int snapshotCount() {
        return depthSnapshots.size();
    }

    @Override
    public void close() {
        depthSnapshots.clear();
    }

    private void handleMarketEvent(DomainEvent event) {
        if (!(event instanceof DepthUpdateEvent depth)) {
            return;
        }
        String key = depth.symbol() + "::" + depth.segment().name();
        MarketDepth snapshot = new MarketDepth(
                null,
                depth.bids(),
                depth.asks(),
                depth.levels(),
                depth.exchangeTimestampMs());
        depthSnapshots.put(key, snapshot);
    }

    private static String bookKey(InstrumentKey key) {
        return key.symbol() + "::" + key.exchangeSegment().name();
    }
}
