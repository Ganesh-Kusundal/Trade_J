package com.tradej.broker.dhan.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.broker.dhan.DhanBrokerConnection;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class DhanAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DhanAutoConfiguration.class));

    @Test
    void beansCreatedWhenDhanBrokerType() {
        contextRunner
                .withPropertyValues(
                        "trade.broker-type=dhan",
                        "trade.dhan.client-id=test-client",
                        "trade.dhan.access-token=test-token",
                        "trade.dhan.auth-mode=STATIC"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(DhanConnectionSettings.class);
                    assertThat(context).hasSingleBean(DhanBrokerConnection.class);
                    assertThat(context).hasSingleBean(BrokerLifecycleManager.class);
                    assertThat(context).hasSingleBean(IBrokerConnection.class);

                    assertThat(context.getBean(IBrokerConnection.class))
                            .isInstanceOf(DhanBrokerConnection.class);
                });
    }

    @Test
    void beansNotCreatedWhenDifferentBrokerType() {
        contextRunner
                .withPropertyValues("trade.broker-type=upstox")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DhanConnectionSettings.class);
                    assertThat(context).doesNotHaveBean(DhanBrokerConnection.class);
                    assertThat(context).doesNotHaveBean(BrokerLifecycleManager.class);
                });
    }

    @Test
    void beansNotCreatedWhenBrokerTypeNotSet() {
        contextRunner
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DhanConnectionSettings.class);
                    assertThat(context).doesNotHaveBean(DhanBrokerConnection.class);
                });
    }

    @Test
    void existingBeanNotOverridden() {
        contextRunner
                .withPropertyValues(
                        "trade.broker-type=dhan",
                        "trade.dhan.client-id=test-client",
                        "trade.dhan.access-token=test-token",
                        "trade.dhan.auth-mode=STATIC"
                )
                .withBean(IBrokerConnection.class, () -> {
                    IBrokerConnection existing = org.mockito.Mockito.mock(IBrokerConnection.class);
                    return existing;
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(IBrokerConnection.class);
                    assertThat(context.getBean(IBrokerConnection.class))
                            .isNotInstanceOf(DhanBrokerConnection.class);
                });
    }
}
