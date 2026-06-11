package com.tradej.brokergateway;

import com.tradej.broker.api.exception.UnsupportedIntervalException;
import com.tradej.broker.api.model.HistoricalDataCapabilities;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Routes market data requests to brokers.
 *
 * <p>Supports two modes:
 * <ul>
 *   <li><b>Manual mode</b> — caller picks the broker via {@link #setActive(String)}
 *       then uses {@link #active()} to access it directly.</li>
 *   <li><b>Auto mode</b> — the router selects the best broker for each request
 *       based on interval capabilities, with automatic fallback on failure.
 *       Use {@link #historical(String, ExchangeSegment, String, LocalDate, LocalDate)}
 *       for auto-routed historical data.</li>
 * </ul>
 *
 * <p>Broker priority for historical data (fastest first):
 * <ol>
 *   <li>Dhan — widest interval support, fastest retrieval (35-190ms)</li>
 *   <li>ICICI — 1-second granularity, moderate speed (48-700ms)</li>
 *   <li>Upstox — weekly/monthly only, slowest (~2s)</li>
 * </ol>
 */
public final class BrokerRouter {

    private static final List<BrokerSource> HISTORICAL_PRIORITY = List.of(
            BrokerSource.DHAN, BrokerSource.ICICI, BrokerSource.UPSTOX);

    private final BrokerGateway gateway;
    private volatile BrokerSource activeSource;

    public BrokerRouter(BrokerGateway gateway) {
        this.gateway = gateway;
        this.activeSource = gateway.availableBrokers().iterator().next();
    }

    public BrokerRouter(BrokerGateway gateway, BrokerSource initialActive) {
        this.gateway = gateway;
        this.activeSource = initialActive;
    }

    // ── Manual mode ────────────────────────────────────────────────────

    public BrokerHandle active() {
        return gateway.broker(activeSource);
    }

    public BrokerSource activeSource() {
        return activeSource;
    }

    public void setActive(BrokerSource source) {
        if (!gateway.availableBrokers().contains(source)) {
            throw new IllegalArgumentException("Broker '" + source + "' not available");
        }
        this.activeSource = source;
    }

    public void setActive(String name) {
        setActive(BrokerSource.parse(name));
    }

    public BrokerGateway gateway() {
        return gateway;
    }

    // ── Auto mode ──────────────────────────────────────────────────────

    /**
     * Auto-route a historical data request to the best available broker.
     * Tries brokers in priority order (Dhan → ICICI → Upstox), skipping
     * those that don't support the requested interval. Falls back to the
     * next broker on failure.
     *
     * @throws UnsupportedIntervalException if no available broker supports the interval
     */
    public GatewayResult<List<Candle>> historical(
            String symbol, ExchangeSegment segment, String interval,
            LocalDate from, LocalDate to) {
        List<BrokerSource> candidates = selectBrokersForInterval(interval);
        if (candidates.isEmpty()) {
            throw new UnsupportedIntervalException(
                    "gateway", interval,
                    java.util.Set.of("Dhan: 1m/5m/15m/25m/60m/1d",
                            "ICICI: 1s/1m/5m/30m/1d",
                            "Upstox: 1m/30m/1d/week/month"));
        }
        InstrumentKey key = new InstrumentKey(symbol, segment);
        CandleHistoryRequest request = new CandleHistoryRequest(key, interval, from, to);
        Exception lastError = null;
        for (BrokerSource source : candidates) {
            try {
                BrokerHandle handle = gateway.broker(source);
                Instant start = Instant.now();
                List<Candle> candles = handle.connection().marketData().getCandles(request);
                java.time.Duration latency = java.time.Duration.between(start, Instant.now());
                var metadata = new com.tradej.brokergateway.result.ResultMetadata(
                        latency, Instant.now(), UUID.randomUUID().toString(), java.util.Map.of(), null);
                return GatewayResult.success(candles, source, metadata);
            } catch (UnsupportedIntervalException e) {
                lastError = e;
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw new RuntimeException("All brokers failed for " + symbol + " " + interval
                + ". Last error: " + (lastError != null ? lastError.getMessage() : "unknown"), lastError);
    }

    /**
     * Find available brokers that support the given interval, ordered by priority.
     */
    public List<BrokerSource> selectBrokersForInterval(String interval) {
        String normalized = interval == null ? "" : interval.trim().toLowerCase();
        List<BrokerSource> result = new ArrayList<>();
        for (BrokerSource source : HISTORICAL_PRIORITY) {
            if (!gateway.availableBrokers().contains(source)) continue;
            MarketDataProvider provider = gateway.broker(source).connection().marketData();
            HistoricalDataCapabilities caps = provider.capabilities();
            if (caps != null && caps.supportedIntervals().contains(normalized)) {
                result.add(source);
            }
        }
        return result;
    }

    /**
     * Returns true if any available broker supports the given interval.
     */
    public boolean supportsInterval(String interval) {
        return !selectBrokersForInterval(interval).isEmpty();
    }
}
