package com.tradej.app.startup;

import com.tradej.app.config.BrokerTransportProfile;
import com.tradej.app.config.TradingProperties;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.core.domain.port.EventBus;
import com.tradej.hotpath.MarketDataPipeline;
import com.tradej.hotpath.OrderPipeline;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class BrokerStartupOrchestratorAnalyticsTest {

    @Test
    void upstoxAnalyticsRestDoesNotExpectWebSocket() {
        var profile = new BrokerTransportProfile(false, true, true, false, false);
        assertFalse(profile.expectsWebSocket());
        assertTrue(profile.isAnalyticsRest());
        assertTrue(profile.isUpstox());
    }

    @Test
    void setupWebSocketHandlersRegistersCallbacksWhenInvokedDirectly() {
        WebSocketMultiplexer multiplexer = mock(WebSocketMultiplexer.class);
        com.tradej.broker.api.IBrokerConnection brokerConnection = mock(com.tradej.broker.api.IBrokerConnection.class);
        when(brokerConnection.websocket()).thenReturn(multiplexer);

        BrokerStartupOrchestrator orchestrator = new BrokerStartupOrchestrator(
                new MockEnvironment(), tradingProperties(), new BrokerLifecycleManager());
        invokeSetupWebSocketHandlers(
                orchestrator,
                brokerConnection,
                mock(MarketDataPipeline.class),
                mock(OrderPipeline.class),
                mock(EventBus.class)
        );

        verify(multiplexer).onMarketData(any());
        verify(multiplexer).onOrderUpdate(any());
    }

    @Test
    void analyticsModeBranchSkipsWebSocketHandlerWiring() {
        var profile = new BrokerTransportProfile(false, true, true, false, false);
        assertFalse(profile.expectsWebSocket(),
                "Production branch must skip setupWebSocketHandlers for analytics REST");
    }

    @Test
    void dhanLiveExpectsWebSocket() {
        var profile = BrokerTransportProfile.resolve(
                env("dhan"), tradingProperties());
        assertTrue(profile.expectsWebSocket());
    }

    @Test
    void upstoxTradingExpectsWebSocket() {
        var profile = BrokerTransportProfile.resolve(
                env("upstox"), tradingProperties());
        assertTrue(profile.expectsWebSocket());
        assertFalse(profile.isAnalyticsRest());
    }

    @Test
    void iciciTradingExpectsWebSocket() {
        var profile = BrokerTransportProfile.resolve(
                env("icici"), tradingProperties());
        assertTrue(profile.expectsWebSocket());
        assertTrue(profile.isIcici());
    }

    @Test
    void gatewayExpectsWebSocket() {
        var profile = BrokerTransportProfile.resolve(
                env("gateway"), tradingProperties());
        assertTrue(profile.expectsWebSocket());
        assertTrue(profile.gateway());
    }

    private static MockEnvironment env(String brokerType) {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("trade.broker-type", brokerType);
        return env;
    }

    private static TradingProperties tradingProperties() {
        return new TradingProperties(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null
        );
    }

    private static void invokeSetupWebSocketHandlers(
            BrokerStartupOrchestrator orchestrator,
            com.tradej.broker.api.IBrokerConnection brokerConnection,
            MarketDataPipeline marketDataPipeline,
            OrderPipeline orderPipeline,
            EventBus eventBus
    ) {
        try {
            var method = BrokerStartupOrchestrator.class.getDeclaredMethod(
                    "setupWebSocketHandlers",
                    com.tradej.broker.api.IBrokerConnection.class,
                    MarketDataPipeline.class,
                    OrderPipeline.class,
                    EventBus.class
            );
            method.setAccessible(true);
            method.invoke(orchestrator, brokerConnection, marketDataPipeline, orderPipeline, eventBus);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
