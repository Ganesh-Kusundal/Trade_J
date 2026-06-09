package com.tradej.broker.upstox.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.broker.upstox.UpstoxBrokerConnection;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class UpstoxAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(UpstoxAutoConfiguration.class));

    @Test
    void beansCreatedWhenUpstoxBrokerType() {
        contextRunner
                .withPropertyValues(
                        "trade.broker-type=upstox",
                        "trade.upstox.client-id=test-client",
                        "trade.upstox.client-secret=test-secret",
                        "trade.upstox.redirect-uri=http://localhost:18080/callback"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(UpstoxConnectionSettings.class);
                    assertThat(context).hasSingleBean(UpstoxBrokerConnection.class);
                    assertThat(context).hasSingleBean(BrokerLifecycleManager.class);
                    assertThat(context).hasSingleBean(IBrokerConnection.class);

                    assertThat(context.getBean(IBrokerConnection.class))
                            .isInstanceOf(UpstoxBrokerConnection.class);
                });
    }

    @Test
    void beansNotCreatedWhenDifferentBrokerType() {
        contextRunner
                .withPropertyValues("trade.broker-type=dhan")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(UpstoxConnectionSettings.class);
                    assertThat(context).doesNotHaveBean(UpstoxBrokerConnection.class);
                    assertThat(context).doesNotHaveBean(BrokerLifecycleManager.class);
                });
    }

    @Test
    void beansNotCreatedWhenBrokerTypeNotSet() {
        contextRunner
                .run(context -> {
                    assertThat(context).doesNotHaveBean(UpstoxConnectionSettings.class);
                    assertThat(context).doesNotHaveBean(UpstoxBrokerConnection.class);
                });
    }

    @Test
    void existingBeanNotOverridden() {
        contextRunner
                .withPropertyValues(
                        "trade.broker-type=upstox",
                        "trade.upstox.client-id=test-client",
                        "trade.upstox.client-secret=test-secret",
                        "trade.upstox.redirect-uri=http://localhost:18080/callback"
                )
                .withBean(IBrokerConnection.class, () -> {
                    IBrokerConnection existing = org.mockito.Mockito.mock(IBrokerConnection.class);
                    return existing;
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(IBrokerConnection.class);
                    assertThat(context.getBean(IBrokerConnection.class))
                            .isNotInstanceOf(UpstoxBrokerConnection.class);
                });
    }

    @Test
    void propertiesBoundCorrectly() {
        contextRunner
                .withPropertyValues(
                        "trade.broker-type=upstox",
                        "trade.upstox.client-id=my-client",
                        "trade.upstox.client-secret=my-secret",
                        "trade.upstox.redirect-uri=http://localhost:9090/cb",
                        "trade.upstox.access-token=at-123",
                        "trade.upstox.refresh-token=rt-456",
                        "trade.upstox.sandbox=true",
                        "trade.upstox.analytics-only=false"
                )
                .run(context -> {
                    UpstoxConnectionSettings settings = context.getBean(UpstoxConnectionSettings.class);
                    assertThat(settings.clientId()).isEqualTo("my-client");
                    assertThat(settings.clientSecret()).isEqualTo("my-secret");
                    assertThat(settings.redirectUri()).isEqualTo("http://localhost:9090/cb");
                    assertThat(settings.accessToken()).isEqualTo("at-123");
                    assertThat(settings.refreshToken()).isEqualTo("rt-456");
                    assertThat(settings.isSandbox()).isTrue();
                    assertThat(settings.analyticsOnly()).isFalse();
                });
    }
}
