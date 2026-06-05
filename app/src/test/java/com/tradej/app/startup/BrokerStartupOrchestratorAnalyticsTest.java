package com.tradej.app.startup;

import com.tradej.app.config.BrokerRuntimeMode;
import com.tradej.app.config.BrokerRuntimeModeResolver;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.core.domain.port.EventBus;
import com.tradej.hotpath.MarketDataPipeline;
import com.tradej.hotpath.OrderPipeline;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class BrokerStartupOrchestratorAnalyticsTest {

    @Test
    void upstoxAnalyticsRestDoesNotExpectWebSocket() {
        assertFalse(BrokerRuntimeMode.UPSTOX_ANALYTICS_REST.expectsWebSocket());
    }

    @Test
    void setupWebSocketHandlersRegistersCallbacksWhenInvokedDirectly() {
        WebSocketMultiplexer multiplexer = mock(WebSocketMultiplexer.class);
        com.tradej.broker.api.IBrokerConnection brokerConnection = mock(com.tradej.broker.api.IBrokerConnection.class);
        when(brokerConnection.websocket()).thenReturn(multiplexer);

        BrokerRuntimeModeResolver resolver = mock(BrokerRuntimeModeResolver.class);
        when(resolver.resolve()).thenReturn(BrokerRuntimeMode.UPSTOX_TRADING_WS);
        BrokerStartupOrchestrator orchestrator = new BrokerStartupOrchestrator(resolver);
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
        BrokerRuntimeMode mode = BrokerRuntimeMode.UPSTOX_ANALYTICS_REST;
        assertFalse(mode.expectsWebSocket(), "Production branch must skip setupWebSocketHandlers for analytics REST");
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
