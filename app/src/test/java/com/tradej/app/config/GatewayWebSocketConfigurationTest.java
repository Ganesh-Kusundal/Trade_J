package com.tradej.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.port.EventBus;
import com.tradej.gateway.bridge.GatewayEventBridge;
import com.tradej.gateway.config.GatewayProperties;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.gateway.websocket.GatewayReplayCommandProcessor;
import com.tradej.gateway.websocket.GatewayWebSocketHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class GatewayWebSocketConfigurationTest {

    @Test
    void gatewayWebSocketEndpointUsesConfiguredPath() {
        GatewayProperties properties = new GatewayProperties();
        properties.setWebsocketPath("/ws/custom");
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(any(WebSocketHandler.class), eq(properties.websocketPath()))).thenReturn(registration);

        new GatewayBrokerConfiguration.GatewayWebSocketConfiguration(properties).registerWebSocketHandlers(registry);

        ArgumentCaptor<GatewayWebSocketHandler> handler = ArgumentCaptor.forClass(GatewayWebSocketHandler.class);
        verify(registry).addHandler(handler.capture(), eq(properties.websocketPath()));
        verify(registration).setAllowedOrigins("*");
    }

    @Test
    void gatewayWebSocketHandlerAcceptsOptionalReplayProcessor() {
        GatewayTopicRouter router = mock(GatewayTopicRouter.class);
        GatewayReplayCommandProcessor replayCommandProcessor = mock(GatewayReplayCommandProcessor.class);

        GatewayWebSocketHandler handler = new GatewayBrokerConfiguration.GatewayWebSocketConfiguration(new GatewayProperties())
                .gatewayWebSocketHandler(router, replayCommandProcessor);

        assertNotNull(handler);
        assertSame(router, routerOf(handler));
    }

    @Test
    void gatewayEventBridgeRegistersWithEventBus() {
        GatewayTopicRouter router = mock(GatewayTopicRouter.class);
        EventBus eventBus = mock(EventBus.class);
        GatewayBrokerConfiguration.GatewayWebSocketConfiguration configuration =
                new GatewayBrokerConfiguration.GatewayWebSocketConfiguration(new GatewayProperties());

        GatewayEventBridge bridge = configuration.gatewayEventBridge(router, new ObjectMapper(), null);
        Boolean registered = configuration.gatewayEventBusRegistrar(bridge, eventBus);

        // The registrar's job is to wire the bridge into the event bus
        // (a side effect — the bean return value is just a marker).
        assertNotNull(registered, "gatewayEventBusRegistrar must return a non-null marker");
        verify(eventBus).subscribe(eq(MarketTickEvent.class), any());
    }

    private static GatewayTopicRouter routerOf(GatewayWebSocketHandler handler) {
        try {
            var field = GatewayWebSocketHandler.class.getDeclaredField("router");
            field.setAccessible(true);
            return (GatewayTopicRouter) field.get(handler);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
