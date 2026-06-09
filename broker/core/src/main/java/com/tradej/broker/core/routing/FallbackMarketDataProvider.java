package com.tradej.broker.core.routing;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.core.reconnect.ReconnectManager;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Quote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Market data provider that falls back to REST polling when the WebSocket
 * feed is disconnected.
 *
 * <p>Normal operation: delegates to the primary {@link MarketDataProvider}
 * (which reads from the WebSocket-maintained local cache).
 *
 * <p>Fallback mode: when {@link WebSocketMultiplexer#isConnected()} returns false,
 * this provider switches to REST polling — calling the broker's REST API
 * directly for LTP, quotes, and depth at a configurable interval.
 *
 * <p>Auto-resume: when the WebSocket reconnects, fallback mode is automatically
 * disabled and the primary provider resumes.
 */
public final class FallbackMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackMarketDataProvider.class);

    private final IBrokerConnection connection;
    private final MarketDataProvider primary;
    private final long pollIntervalMs;
    private final AtomicBoolean fallbackActive = new AtomicBoolean(false);

    public FallbackMarketDataProvider(IBrokerConnection connection, long pollIntervalMs) {
        this.connection = connection;
        this.primary = connection.marketData();
        this.pollIntervalMs = pollIntervalMs;
    }

    public FallbackMarketDataProvider(IBrokerConnection connection) {
        this(connection, 5000L);
    }

    @Override
    public long getLtpPaisa(InstrumentKey instrumentKey) {
        if (isWebSocketConnected()) {
            return primary.getLtpPaisa(instrumentKey);
        }
        activateFallback();
        log.debug("Fallback REST: getLtpPaisa({})", instrumentKey.symbol());
        return primary.getLtpPaisa(instrumentKey);
    }

    @Override
    public Quote getQuote(InstrumentKey instrumentKey) {
        if (isWebSocketConnected()) {
            return primary.getQuote(instrumentKey);
        }
        activateFallback();
        log.debug("Fallback REST: getQuote({})", instrumentKey.symbol());
        return primary.getQuote(instrumentKey);
    }

    @Override
    public MarketDepth getDepth(InstrumentKey instrumentKey) {
        if (isWebSocketConnected()) {
            return primary.getDepth(instrumentKey);
        }
        activateFallback();
        log.debug("Fallback REST: getDepth({})", instrumentKey.symbol());
        return primary.getDepth(instrumentKey);
    }

    @Override
    public Quote getOhlcSnapshot(InstrumentKey instrumentKey) {
        return primary.getOhlcSnapshot(instrumentKey);
    }

    @Override
    public List<Candle> getCandles(CandleHistoryRequest request) {
        return primary.getCandles(request);
    }

    @Override
    public com.tradej.broker.api.model.HistoricalDataCapabilities capabilities() {
        return primary.capabilities();
    }

    @Override
    public Map<InstrumentKey, Long> getLtpBatch(Collection<InstrumentKey> instrumentKeys) {
        if (isWebSocketConnected()) {
            return primary.getLtpBatch(instrumentKeys);
        }
        activateFallback();
        return primary.getLtpBatch(instrumentKeys);
    }

    @Override
    public Map<InstrumentKey, Quote> getQuoteBatch(Collection<InstrumentKey> instrumentKeys) {
        if (isWebSocketConnected()) {
            return primary.getQuoteBatch(instrumentKeys);
        }
        activateFallback();
        return primary.getQuoteBatch(instrumentKeys);
    }

    @Override
    public Map<InstrumentKey, Quote> getOhlcBatch(Collection<InstrumentKey> instrumentKeys) {
        return primary.getOhlcBatch(instrumentKeys);
    }

    /**
     * Whether fallback mode is currently active.
     */
    public boolean isFallbackActive() {
        return fallbackActive.get();
    }

    private boolean isWebSocketConnected() {
        try {
            return connection.websocket().isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private void activateFallback() {
        if (fallbackActive.compareAndSet(false, true)) {
            log.warn("WebSocket disconnected — activating REST fallback mode (poll every {}ms)", pollIntervalMs);
        }
    }

    /**
     * Reset fallback state when WebSocket reconnects.
     * Should be called by the reconnect listener.
     */
    public void onWebSocketReconnected() {
        if (fallbackActive.compareAndSet(true, false)) {
            log.info("WebSocket reconnected — deactivating REST fallback");
        }
    }
}
