package com.tradej.app.health;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerTransportCapabilities;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.upstox.auth.UpstoxBearerTokenSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class UpstoxHealthIndicatorTest {

    // Use extreme values relative to any wall clock to avoid timing-dependent tests.
    private static final long FAR_FUTURE = Long.MAX_VALUE - 1;  // always valid
    private static final long FAR_PAST = 1L;                     // always expired

    private IBrokerConnection brokerConnection;
    private WebSocketMultiplexer webSocketAdapter;
    private UpstoxBearerTokenSource tokenSource;
    private BrokerTransportCapabilities transportCapabilities;
    private MarketDataProvider marketDataProvider;

    @BeforeEach
    void setUp() {
        brokerConnection = mock(IBrokerConnection.class);
        webSocketAdapter = mock(WebSocketMultiplexer.class);
        when(brokerConnection.websocket()).thenReturn(webSocketAdapter);

        tokenSource = mock(UpstoxBearerTokenSource.class);
        transportCapabilities = mock(BrokerTransportCapabilities.class);
        marketDataProvider = mock(MarketDataProvider.class);
    }

    @Test
    void tokenIsValidWhenExpiryIsInFuture() {
        when(tokenSource.expiryEpochMs()).thenReturn(FAR_FUTURE);
        when(transportCapabilities.analyticsOnly()).thenReturn(true);
        when(transportCapabilities.supportsRestMarketData()).thenReturn(true);
        when(marketDataProvider.getLtpPaisa(any())).thenReturn(750_00L);

        var indicator = createIndicator();
        var health = indicator.health();

        assertEquals("UP", health.getStatus().getCode());
        assertEquals(true, health.getDetails().get("tokenValid"));
        assertNotNull(health.getDetails().get("tokenExpiresAt"));
    }

    @Test
    void tokenIsExpiredWhenExpiryIsInPast() {
        when(tokenSource.expiryEpochMs()).thenReturn(FAR_PAST);
        when(transportCapabilities.analyticsOnly()).thenReturn(true);
        when(transportCapabilities.supportsRestMarketData()).thenReturn(true);
        when(marketDataProvider.getLtpPaisa(any())).thenReturn(750_00L);

        var indicator = createIndicator();
        var health = indicator.health();

        assertEquals("DOWN", health.getStatus().getCode());
        assertEquals(false, health.getDetails().get("tokenValid"));
    }

    @Test
    void tokenIsAssumedValidWhenExpiryIsZero() {
        when(tokenSource.expiryEpochMs()).thenReturn(0L);
        when(transportCapabilities.analyticsOnly()).thenReturn(true);
        when(transportCapabilities.supportsRestMarketData()).thenReturn(true);
        when(marketDataProvider.getLtpPaisa(any())).thenReturn(750_00L);

        var indicator = createIndicator();
        var health = indicator.health();

        assertEquals("UP", health.getStatus().getCode());
        assertEquals(true, health.getDetails().get("tokenValid"));
    }

    @Test
    void tokenIsAssumedValidWhenExpiryIsNegative() {
        when(tokenSource.expiryEpochMs()).thenReturn(-1L);
        when(transportCapabilities.analyticsOnly()).thenReturn(true);
        when(transportCapabilities.supportsRestMarketData()).thenReturn(true);
        when(marketDataProvider.getLtpPaisa(any())).thenReturn(750_00L);

        var indicator = createIndicator();
        var health = indicator.health();

        assertEquals("UP", health.getStatus().getCode());
        assertEquals(true, health.getDetails().get("tokenValid"));
    }

    @Test
    void tokenExpiresAtDetailPresentWhenExpiryIsPositive() {
        when(tokenSource.expiryEpochMs()).thenReturn(FAR_FUTURE);
        when(transportCapabilities.analyticsOnly()).thenReturn(true);
        when(transportCapabilities.supportsRestMarketData()).thenReturn(true);
        when(marketDataProvider.getLtpPaisa(any())).thenReturn(750_00L);

        var indicator = createIndicator();
        var health = indicator.health();

        String expiresAt = (String) health.getDetails().get("tokenExpiresAt");
        assertNotNull(expiresAt);
    }

    @Test
    void websocketDownWhenNotAnalyticsOnly() {
        when(webSocketAdapter.isConnected()).thenReturn(false);
        when(transportCapabilities.analyticsOnly()).thenReturn(false);
        when(transportCapabilities.supportsWebSocket()).thenReturn(true);

        var indicator = createIndicator();
        var health = indicator.health();

        assertEquals("DOWN", health.getStatus().getCode());
    }

    private UpstoxHealthIndicator createIndicator() {
        @SuppressWarnings("unchecked")
        ObjectProvider<BrokerTransportCapabilities> capsProvider = mock(ObjectProvider.class);
        when(capsProvider.getIfUnique()).thenReturn(transportCapabilities);
        return new UpstoxHealthIndicator(
                brokerConnection, tokenSource, capsProvider, marketDataProvider);
    }
}
