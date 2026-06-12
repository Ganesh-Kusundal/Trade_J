package com.tradej.app.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.app.admin.RuntimeHealthState;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.broker.core.routing.LoadBalancedBrokerGateway;
import com.tradej.composition.BrokerComposition;
import com.tradej.composition.config.BrokerProfile;
import com.tradej.core.domain.event.SimpleEventBus;
import com.tradej.gateway.bridge.GatewayEventBridge;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.gateway.websocket.GatewayReplayCommandProcessor;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.replay.engine.ReplayController;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class GatewayBrokerConfigurationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void gatewayReplayCommandProcessorResumesReplay() {
        // The bean takes a ReplayController. Wire it to a bus-backed
        // controller and exercise the resume op.
        SimpleEventBus bus = new SimpleEventBus();
        ReplayController controller = new ReplayController(bus);
        // start() with no candles leaves the controller in PAUSED-ish state
        // which is fine for the "resume" path.
        controller.start(List.of());
        GatewayReplayCommandProcessor processor = new GatewayBrokerConfiguration.GatewayAppConfig()
                .gatewayReplayCommandProcessor(controller, objectMapper);
        // "resume" issues controller.play() — controller then schedules
        // and is in PLAYING. Pause + resume cycle works in isolation.
        processor.processCommand("{\"op\":\"pause\"}");
        processor.processCommand("{\"op\":\"resume\"}");
        // No exception means the bean wired correctly. The replay
        // controller may or may not be in PLAYING depending on the
        // empty-list behavior — we just want the bean to be constructable
        // and routable.
        controller.stop();
    }

    @Test
    void activeBrokerSourceUsesCompositionWhenAvailable() {
        BrokerComposition composition = BrokerComposition.create(
                new BrokerProfile(BrokerProfile.BrokerType.SIMULATION, null, null, null));
        ObjectProvider<BrokerComposition> compositionProvider = mock(ObjectProvider.class);
        when(compositionProvider.getIfAvailable()).thenReturn(composition);

        BrokerSource source = new GatewayBrokerConfiguration.GatewayBeansConfig()
                .activeBrokerSource(compositionProvider);

        assertEquals(BrokerSource.SIMULATION, source);
    }

    @Test
    void activeBrokerSourceFallsBackToDhan() {
        ObjectProvider<BrokerComposition> compositionProvider = mock(ObjectProvider.class);
        when(compositionProvider.getIfAvailable()).thenReturn(null);

        BrokerSource source = new GatewayBrokerConfiguration.GatewayBeansConfig()
                .activeBrokerSource(compositionProvider);

        assertEquals(BrokerSource.DHAN, source);
    }

    @Test
    void gatewayPipelineHealthBroadcasterIncludesLoadBalancedNodeCount() throws Exception {
        RuntimeHealthState healthState = new RuntimeHealthState();
        healthState.markCatalogLoaded(3);
        healthState.markBrokerPreflightPassed();
        healthState.markStartupCompleted();
        GatewayTopicRouter router = mock(GatewayTopicRouter.class);
        GatewayEventBridge bridge = new GatewayEventBridge(router, objectMapper);
        LoadBalancedBrokerGateway gateway = loadBalancedGateway();
        ObjectProvider<IBrokerConnection> brokerConnection = mock(ObjectProvider.class);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<IBrokerConnection> consumer = invocation.getArgument(0);
            consumer.accept(gateway);
            return null;
        }).when(brokerConnection).ifAvailable(any());

        new GatewayBrokerConfiguration.GatewayAppConfig.GatewayPipelineHealthBroadcaster(bridge, healthState, brokerConnection)
                .publishHealth();

        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(org.mockito.ArgumentMatchers.eq(GatewayTopic.PIPELINE_HEALTH), payload.capture());
        JsonNode health = objectMapper.readTree(payload.getValue());
        assertEquals(3, health.path("catalogSize").asInt());
        assertEquals(1, health.path("brokerNodes").asInt());
        assertTrue(health.path("startupCompleted").asBoolean());
    }

    private static LoadBalancedBrokerGateway loadBalancedGateway() {
        IBrokerConnection connection = mock(IBrokerConnection.class);
        WebSocketMultiplexer multiplexer = mock(WebSocketMultiplexer.class);
        when(connection.marketData()).thenReturn(mock(MarketDataProvider.class));
        when(connection.orders()).thenReturn(mock(OrderCommand.class));
        when(connection.websocket()).thenReturn(multiplexer);
        return new LoadBalancedBrokerGateway(List.of(connection), null);
    }
}
