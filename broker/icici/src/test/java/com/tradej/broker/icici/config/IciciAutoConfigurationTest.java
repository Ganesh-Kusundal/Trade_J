package com.tradej.broker.icici.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.broker.icici.IciciBrokerConnection;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class IciciAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IciciAutoConfiguration.class));

    private static final String[] ICICI_BASE_PROPERTIES = {
            "trade.broker-type=icici",
            "trade.icici.app-key=test-key",
            "trade.icici.secret-key=test-secret",
            "trade.icici.auth-mode=TOTP_GENERATED",
            "trade.icici.totp-secret-file=config/totp.txt",
            "trade.icici.username-file=config/user.txt",
            "trade.icici.password-file=config/pass.txt",
            "trade.icici.token-state-file=runtime/icici-token-state.json"
    };

    @Test
    void beansCreatedWhenBrokerTypeIsIcici() {
        contextRunner
                .withPropertyValues(ICICI_BASE_PROPERTIES)
                .run(context -> {
                    assertThat(context).hasSingleBean(BreezeConnectionSettings.class);
                    assertThat(context).hasSingleBean(IciciBrokerConnection.class);
                    assertThat(context).hasSingleBean(IBrokerConnection.class);
                    assertThat(context).hasSingleBean(BrokerLifecycleManager.class);
                    assertThat(context).getBean(IBrokerConnection.class)
                            .isInstanceOf(IciciBrokerConnection.class);
                });
    }

    @Test
    void beansNotCreatedWhenBrokerTypeIsDifferent() {
        contextRunner
                .withPropertyValues("trade.broker-type=dhan")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(BreezeConnectionSettings.class);
                    assertThat(context).doesNotHaveBean(IciciBrokerConnection.class);
                    assertThat(context).doesNotHaveBean(IBrokerConnection.class);
                });
    }

    @Test
    void beansNotCreatedWhenBrokerTypeIsUnset() {
        contextRunner
                .run(context -> {
                    assertThat(context).doesNotHaveBean(BreezeConnectionSettings.class);
                    assertThat(context).doesNotHaveBean(IciciBrokerConnection.class);
                    assertThat(context).doesNotHaveBean(IBrokerConnection.class);
                });
    }

    @Test
    void brokerLifecycleManagerAlwaysCreatedWhenBrokerTypeIsIcici() {
        contextRunner
                .withPropertyValues(ICICI_BASE_PROPERTIES)
                .run(context -> {
                    assertThat(context).hasSingleBean(BrokerLifecycleManager.class);
                });
    }
}
