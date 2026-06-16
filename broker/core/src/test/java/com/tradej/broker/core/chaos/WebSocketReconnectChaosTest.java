package com.tradej.broker.core.chaos;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.*;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.broker.core.chaos.ChaosMetrics;
import com.tradej.broker.core.chaos.ChaosScenario;
import com.tradej.broker.core.chaos.scenarios.RapidReconnectScenario;
import com.tradej.core.domain.value.FeedMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P3.2: Validates that the WebSocket reconnection logic survives
 * 10 rapid connect/disconnect cycles without subscription leaks,
 * connection leaks, or state corruption.
 */
@Tag("chaos")
@Tag("p3")
class WebSocketReconnectChaosTest {

    private static final int RECONNECT_CYCLES = 10;

    private MockWebSocketMultiplexer wsMultiplexer;
    private IBrokerConnection mockConnection;

    @BeforeEach
    void setUp() {
        wsMultiplexer = new MockWebSocketMultiplexer();
        mockConnection = new MockBrokerConnection(wsMultiplexer);
    }

    @Test
    void rapidReconnectScenarioSurvives10Cycles() {
        RapidReconnectScenario scenario = new RapidReconnectScenario(RECONNECT_CYCLES);
        ChaosMetrics metrics = new ChaosMetrics();
        ChaosScenario.ChaosContext ctx = new ChaosScenario.ChaosContext(
                System.currentTimeMillis(), metrics, null);

        assertDoesNotThrow(() -> scenario.apply(mockConnection, ctx),
                "RapidReconnectScenario should not throw after " + RECONNECT_CYCLES + " cycles");

        assertTrue(metrics.reconnectAttempts() >= RECONNECT_CYCLES,
                "Should have recorded at least " + RECONNECT_CYCLES + " reconnect attempts, got "
                        + metrics.reconnectAttempts());
    }

    @Test
    void connectCountMatchesDisconnectCountAfterChaos() {
        RapidReconnectScenario scenario = new RapidReconnectScenario(RECONNECT_CYCLES);
        ChaosMetrics metrics = new ChaosMetrics();
        ChaosScenario.ChaosContext ctx = new ChaosScenario.ChaosContext(
                System.currentTimeMillis(), metrics, null);

        scenario.apply(mockConnection, ctx);

        assertEquals(0, wsMultiplexer.danglingConnections.get(),
                "Should have no dangling connections after chaos. "
                        + "connectCount=" + wsMultiplexer.connectCount.get()
                        + ", disconnectCount=" + wsMultiplexer.disconnectCount.get());
    }

    @Test
    void subscriptionsNotLostOnReconnect() {
        List<MarketSubscriptionRequest> subs = List.of(
                new MarketSubscriptionRequest("NIFTY", com.tradej.core.domain.value.ExchangeSegment.IDX_I),
                new MarketSubscriptionRequest("BANKNIFTY", com.tradej.core.domain.value.ExchangeSegment.IDX_I)
        );
        wsMultiplexer.subscribe(subs, FeedMode.TICKER);
        int preSubCount = wsMultiplexer.subscriptionCount();

        RapidReconnectScenario scenario = new RapidReconnectScenario(5);
        ChaosMetrics metrics = new ChaosMetrics();
        ChaosScenario.ChaosContext ctx = new ChaosScenario.ChaosContext(
                System.currentTimeMillis(), metrics, null);
        scenario.apply(mockConnection, ctx);

        int postSubCount = wsMultiplexer.subscriptionCount();
        assertEquals(preSubCount, postSubCount,
                "Subscription count changed after reconnect cycles: "
                        + preSubCount + " → " + postSubCount);
    }

    @Test
    void circuitBreakerDoesNotOpenUnderNormalCycles() {
        RapidReconnectScenario scenario = new RapidReconnectScenario(RECONNECT_CYCLES);
        ChaosMetrics metrics = new ChaosMetrics();
        ChaosScenario.ChaosContext ctx = new ChaosScenario.ChaosContext(
                System.currentTimeMillis(), metrics, null);

        scenario.apply(mockConnection, ctx);

        assertEquals(0, metrics.circuitBreakerTrips(),
                "Circuit breaker should not trip during normal reconnect cycles");
    }

    // ── Mock infrastructure ──────────────────────────────────────────

    static class MockWebSocketMultiplexer implements WebSocketMultiplexer {
        final AtomicBoolean connected = new AtomicBoolean(false);
        final AtomicInteger connectCount = new AtomicInteger(0);
        final AtomicInteger disconnectCount = new AtomicInteger(0);
        final AtomicInteger danglingConnections = new AtomicInteger(0);
        final Map<MarketSubscriptionRequest, FeedMode> subscriptions = new LinkedHashMap<>();

        @Override public void connect() {
            connected.set(true);
            connectCount.incrementAndGet();
            danglingConnections.incrementAndGet();
        }
        @Override public void disconnect() {
            connected.set(false);
            disconnectCount.incrementAndGet();
            danglingConnections.decrementAndGet();
        }
        @Override public boolean isConnected() { return connected.get(); }
        @Override
        public void subscribe(Collection<MarketSubscriptionRequest> instruments, FeedMode feedMode) {
            for (MarketSubscriptionRequest req : instruments) subscriptions.put(req, feedMode);
        }
        @Override
        public void unsubscribe(Collection<MarketSubscriptionRequest> instruments) {
            for (MarketSubscriptionRequest req : instruments) subscriptions.remove(req);
        }
        @Override public void onMarketData(MarketDataListener listener) {}
        @Override public void onOrderUpdate(OrderUpdateListener listener) {}
        @Override public Map<MarketSubscriptionRequest, FeedMode> subscriptions() {
            return Map.copyOf(subscriptions);
        }
        int subscriptionCount() { return subscriptions.size(); }
    }

    static class MockBrokerConnection implements IBrokerConnection {
        private final WebSocketMultiplexer ws;
        MockBrokerConnection(WebSocketMultiplexer ws) { this.ws = ws; }
        @Override public WebSocketMultiplexer websocket() { return ws; }
        @Override public MarketDataProvider marketData() { return null; }
        @Override public OrderCommand orders() { return null; }
        @Override public OrderQuery orderQuery() { return null; }
        @Override public PortfolioProvider portfolio() { return null; }
        @Override public MarginProvider margin() { return null; }
        @Override public InstrumentResolver instruments() { return null; }
        @Override public FuturesProvider futures() { return null; }
        @Override public OptionsProvider options() { return null; }
        @Override public SliceOrderCommand sliceOrders() { return null; }
        @Override public BracketOrderProvider bracketOrders() { return null; }
        @Override public GttOrderProvider gttOrders() { return null; }
        @Override public SessionRiskProvider sessionRisk() { return null; }
        @Override public ConditionalAlertProvider alerts() { return null; }
        @Override public NewsProvider news() { return null; }
        @Override public BrokerSource source() { return BrokerSource.DHAN; }
        @Override public void connect() { ws.connect(); }
        @Override public void disconnect() { ws.disconnect(); }
        @Override public void loadInstrumentCatalog(Path catalogPath) {}
        @Override public <T> Optional<T> getCapability(Class<T> capabilityClass) { return Optional.empty(); }
    }
}
