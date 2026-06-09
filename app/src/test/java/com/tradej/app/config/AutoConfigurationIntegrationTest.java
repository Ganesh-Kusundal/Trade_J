package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.broker.dhan.config.DhanAutoConfiguration;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.broker.icici.config.IciciAutoConfiguration;
import com.tradej.broker.icici.IciciBrokerConnection;
import com.tradej.broker.upstox.config.UpstoxAutoConfiguration;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import com.tradej.broker.upstox.UpstoxBrokerConnection;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-cutting integration tests that verify all broker auto-configurations
 * play nicely together in a shared ApplicationContext.
 *
 * <p>Uses the {@link ApplicationContextRunner} pattern to simulate the full
 * auto-configuration classpath and confirm mutual exclusivity.
 */
@Tag("unit")
class AutoConfigurationIntegrationTest {

    private final ApplicationContextRunner fullContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DhanAutoConfiguration.class,
                    UpstoxAutoConfiguration.class,
                    IciciAutoConfiguration.class
            ));

    // ── Dhan broker type ──────────────────────────────────────────

    @Test
    void withDhanBrokerType_onlyDhanBeansAreCreated() {
        fullContextRunner
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

                    assertThat(context).doesNotHaveBean(UpstoxConnectionSettings.class);
                    assertThat(context).doesNotHaveBean(UpstoxBrokerConnection.class);
                    assertThat(context).doesNotHaveBean(BreezeConnectionSettings.class);
                    assertThat(context).doesNotHaveBean(IciciBrokerConnection.class);

                    assertThat(context.getBean(IBrokerConnection.class))
                            .isInstanceOf(DhanBrokerConnection.class);
                });
    }

    // ── Upstox broker type ───────────────────────────────────────

    @Test
    void withUpstoxBrokerType_onlyUpstoxBeansAreCreated() {
        fullContextRunner
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

                    assertThat(context).doesNotHaveBean(DhanConnectionSettings.class);
                    assertThat(context).doesNotHaveBean(DhanBrokerConnection.class);
                    assertThat(context).doesNotHaveBean(BreezeConnectionSettings.class);
                    assertThat(context).doesNotHaveBean(IciciBrokerConnection.class);

                    assertThat(context.getBean(IBrokerConnection.class))
                            .isInstanceOf(UpstoxBrokerConnection.class);
                });
    }

    // ── Icici broker type ────────────────────────────────────────

    @Test
    void withIciciBrokerType_onlyIciciBeansAreCreated() {
        fullContextRunner
                .withPropertyValues(
                        "trade.broker-type=icici",
                        "trade.icici.app-key=test-key",
                        "trade.icici.secret-key=test-secret",
                        "trade.icici.auth-mode=TOTP_GENERATED",
                        "trade.icici.totp-secret-file=config/totp.txt",
                        "trade.icici.username-file=config/user.txt",
                        "trade.icici.password-file=config/pass.txt",
                        "trade.icici.token-state-file=runtime/icici-token-state.json"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(BreezeConnectionSettings.class);
                    assertThat(context).hasSingleBean(IciciBrokerConnection.class);
                    assertThat(context).hasSingleBean(BrokerLifecycleManager.class);
                    assertThat(context).hasSingleBean(IBrokerConnection.class);

                    assertThat(context).doesNotHaveBean(DhanConnectionSettings.class);
                    assertThat(context).doesNotHaveBean(DhanBrokerConnection.class);
                    assertThat(context).doesNotHaveBean(UpstoxConnectionSettings.class);
                    assertThat(context).doesNotHaveBean(UpstoxBrokerConnection.class);

                    assertThat(context.getBean(IBrokerConnection.class))
                            .isInstanceOf(IciciBrokerConnection.class);
                });
    }

    // ── IBrokerConnection always present ──────────────────────────

    @Test
    void iBrokerConnectionIsAlwaysPresentWhenBrokerTypeSet() {
        for (String brokerType : new String[]{"dhan", "upstox", "icici"}) {
            String prefix = switch (brokerType) {
                case "dhan" -> "trade.dhan";
                case "upstox" -> "trade.upstox";
                case "icici" -> "trade.icici";
                default -> throw new IllegalArgumentException();
            };
            String[] properties = switch (brokerType) {
                case "dhan" -> new String[]{
                        "trade.broker-type=dhan",
                        "trade.dhan.client-id=c", "trade.dhan.access-token=t", "trade.dhan.auth-mode=STATIC"
                };
                case "upstox" -> new String[]{
                        "trade.broker-type=upstox",
                        "trade.upstox.client-id=c", "trade.upstox.client-secret=s",
                        "trade.upstox.redirect-uri=http://localhost/callback"
                };
                case "icici" -> new String[]{
                        "trade.broker-type=icici",
                        "trade.icici.app-key=k", "trade.icici.secret-key=s",
                        "trade.icici.auth-mode=TOTP_GENERATED",
                        "trade.icici.totp-secret-file=f", "trade.icici.username-file=f",
                        "trade.icici.password-file=f", "trade.icici.token-state-file=f"
                };
                default -> new String[0];
            };

            fullContextRunner
                    .withPropertyValues(properties)
                    .run(context -> {
                        assertThat(context).hasSingleBean(IBrokerConnection.class);
                        assertThat(context).hasSingleBean(BrokerLifecycleManager.class);
                    });
        }
    }

    // ── No duplicate beans ───────────────────────────────────────

    @Test
    void noDuplicateBrokerBeansWhenDhanSelected() {
        fullContextRunner
                .withPropertyValues(
                        "trade.broker-type=dhan",
                        "trade.dhan.client-id=c",
                        "trade.dhan.access-token=t",
                        "trade.dhan.auth-mode=STATIC"
                )
                .run(context -> {
                    assertThat(context.getBeansOfType(IBrokerConnection.class)).hasSize(1);
                    assertThat(context.getBeansOfType(BrokerLifecycleManager.class)).hasSize(1);
                });
    }

    @Test
    void noDuplicateBrokerBeansWhenUpstoxSelected() {
        fullContextRunner
                .withPropertyValues(
                        "trade.broker-type=upstox",
                        "trade.upstox.client-id=c",
                        "trade.upstox.client-secret=s",
                        "trade.upstox.redirect-uri=http://localhost/cb"
                )
                .run(context -> {
                    assertThat(context.getBeansOfType(IBrokerConnection.class)).hasSize(1);
                    assertThat(context.getBeansOfType(BrokerLifecycleManager.class)).hasSize(1);
                });
    }

    // ── No beans when broker type unset ───────────────────────────

    @Test
    void noBrokerBeansCreatedWhenBrokerTypeNotSet() {
        fullContextRunner
                .run(context -> {
                    assertThat(context).doesNotHaveBean(IBrokerConnection.class);
                    assertThat(context).doesNotHaveBean(BrokerLifecycleManager.class);
                    assertThat(context).doesNotHaveBean(DhanConnectionSettings.class);
                    assertThat(context).doesNotHaveBean(UpstoxConnectionSettings.class);
                    assertThat(context).doesNotHaveBean(BreezeConnectionSettings.class);
                });
    }

    // ── Existing bean not overridden ──────────────────────────────

    @Test
    void existingIBrokerConnectionNotOverriddenByAutoConfiguration() {
        fullContextRunner
                .withPropertyValues(
                        "trade.broker-type=dhan",
                        "trade.dhan.client-id=c",
                        "trade.dhan.access-token=t",
                        "trade.dhan.auth-mode=STATIC"
                )
                .withBean(IBrokerConnection.class, () -> org.mockito.Mockito.mock(IBrokerConnection.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(IBrokerConnection.class);
                    assertThat(context.getBean(IBrokerConnection.class))
                            .isNotInstanceOf(DhanBrokerConnection.class);
                });
    }
}
