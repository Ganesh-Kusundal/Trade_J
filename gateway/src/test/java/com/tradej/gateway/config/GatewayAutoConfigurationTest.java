package com.tradej.gateway.config;

import com.tradej.gateway.bridge.GatewayEventBridge;
import com.tradej.gateway.health.GatewayHealthIndicator;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.gateway.websocket.GatewayWebSocketHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class GatewayAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GatewayAutoConfiguration.class))
            .withBean(com.fasterxml.jackson.databind.ObjectMapper.class)
            .withBean(com.tradej.core.domain.port.EventBus.class, () -> {
                return new com.tradej.core.domain.port.EventBus() {
                    @Override
                    public <T extends com.tradej.core.domain.event.DomainEvent> void subscribe(Class<T> type, com.tradej.core.domain.port.DomainEventHandler<T> handler) {}
                    @Override
                    public <T extends com.tradej.core.domain.event.DomainEvent> void unsubscribe(Class<T> type, com.tradej.core.domain.port.DomainEventHandler<T> handler) {}
                    @Override
                    public void publish(com.tradej.core.domain.event.DomainEvent event) {}
                    @Override
                    public void start() {}
                    @Override
                    public void stop() {}
                };
            });

    @Test
    void beansCreatedWhenEnabled() {
        contextRunner
                .withPropertyValues("tradej.gateway.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(GatewayTopicRouter.class);
                    assertThat(context).hasSingleBean(GatewayWebSocketHandler.class);
                    assertThat(context).hasSingleBean(GatewayEventBridge.class);
                    assertThat(context).hasSingleBean(GatewayHealthIndicator.class);
                });
    }

    @Test
    void beansNotCreatedWhenDisabled() {
        contextRunner
                .withPropertyValues("tradej.gateway.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(GatewayTopicRouter.class);
                    assertThat(context).doesNotHaveBean(GatewayWebSocketHandler.class);
                    assertThat(context).doesNotHaveBean(GatewayEventBridge.class);
                    assertThat(context).doesNotHaveBean(GatewayHealthIndicator.class);
                });
    }

    @Test
    void beansCreatedByDefault() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(GatewayTopicRouter.class);
                    assertThat(context).hasSingleBean(GatewayWebSocketHandler.class);
                    assertThat(context).hasSingleBean(GatewayEventBridge.class);
                    assertThat(context).hasSingleBean(GatewayHealthIndicator.class);
                });
    }

    @Test
    void propertiesBoundCorrectly() {
        contextRunner
                .withPropertyValues(
                        "tradej.gateway.enabled=true",
                        "tradej.gateway.websocket-path=/ws/custom",
                        "tradej.gateway.session-timeout-ms=60000",
                        "tradej.gateway.max-binary-message-size=32768"
                )
                .run(context -> {
                    GatewayProperties props = context.getBean(GatewayProperties.class);
                    assertThat(props.websocketPath()).isEqualTo("/ws/custom");
                    assertThat(props.sessionTimeoutMs()).isEqualTo(60000L);
                    assertThat(props.maxBinaryMessageSize()).isEqualTo(32768);
                });
    }

    @Test
    void healthIndicatorWiredToRouter() {
        contextRunner
                .withPropertyValues("tradej.gateway.enabled=true")
                .run(context -> {
                    GatewayHealthIndicator indicator = context.getBean(GatewayHealthIndicator.class);
                    assertThat(indicator).isNotNull();
                });
    }
}
