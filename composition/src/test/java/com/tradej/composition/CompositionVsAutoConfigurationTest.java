package com.tradej.composition;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.broker.dhan.config.DhanAutoConfiguration;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.icici.IciciBrokerConnection;
import com.tradej.broker.icici.config.IciciAutoConfiguration;
import com.tradej.broker.upstox.UpstoxBrokerConnection;
import com.tradej.broker.upstox.config.UpstoxAutoConfiguration;
import com.tradej.composition.config.BrokerProfile;
import com.tradej.composition.config.RiskProfile;
import com.tradej.composition.config.StorageProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test verifying that the composition layer and Spring auto-configuration
 * produce structurally equivalent bean graphs for each broker type.
 *
 * <p>For each broker this test:
 * <ul>
 *   <li>Builds a {@link FullComposition} via the programmatic API</li>
 *   <li>Boots a minimal Spring context with the corresponding auto-configuration</li>
 *   <li>Asserts both produce the same {@link IBrokerConnection} implementation type</li>
 * </ul>
 */
@Tag("unit")
class CompositionVsAutoConfigurationTest {

    // ── Dhan ──────────────────────────────────────────────────────

    @Test
    void dhanCompositionMatchesAutoConfigurationBeanType() {
        BrokerProfile profile = new BrokerProfile(
                BrokerProfile.BrokerType.DHAN,
                new BrokerProfile.DhanConfig(
                        "test-client", "test-token",
                        DhanApiEnvironment.SANDBOX, null,
                        DhanAuthMode.STATIC, null, null, null,
                        30, false, null
                ),
                null, null
        );

        FullComposition composition = FullComposition.brokerOnly(profile);
        IBrokerConnection compositionConnection = composition.brokerConnection();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DhanAutoConfiguration.class))
                .withPropertyValues(
                        "trade.broker-type=dhan",
                        "trade.dhan.client-id=test-client",
                        "trade.dhan.access-token=test-token",
                        "trade.dhan.auth-mode=STATIC"
                )
                .run(context -> {
                    IBrokerConnection autoConfigConnection = context.getBean(IBrokerConnection.class);

                    assertThat(compositionConnection.getClass())
                            .as("Composition and auto-configuration must produce the same connection type")
                            .isEqualTo(autoConfigConnection.getClass());

                    assertThat(compositionConnection)
                            .isInstanceOf(DhanBrokerConnection.class);
                    assertThat(autoConfigConnection)
                            .isInstanceOf(DhanBrokerConnection.class);
                });
    }

    // ── Upstox ────────────────────────────────────────────────────

    @Test
    void upstoxCompositionMatchesAutoConfigurationBeanType() {
        BrokerProfile profile = new BrokerProfile(
                BrokerProfile.BrokerType.UPSTOX,
                null,
                new BrokerProfile.UpstoxConfig(
                        "test-client", "test-secret",
                        "http://localhost:18080/callback",
                        null, null, null, null,
                        false, false, 18080, 5000, 300000
                ),
                null
        );

        FullComposition composition = FullComposition.brokerOnly(profile);
        IBrokerConnection compositionConnection = composition.brokerConnection();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(UpstoxAutoConfiguration.class))
                .withPropertyValues(
                        "trade.broker-type=upstox",
                        "trade.upstox.client-id=test-client",
                        "trade.upstox.client-secret=test-secret",
                        "trade.upstox.redirect-uri=http://localhost:18080/callback"
                )
                .run(context -> {
                    IBrokerConnection autoConfigConnection = context.getBean(IBrokerConnection.class);

                    assertThat(compositionConnection.getClass())
                            .as("Composition and auto-configuration must produce the same connection type")
                            .isEqualTo(autoConfigConnection.getClass());

                    assertThat(compositionConnection)
                            .isInstanceOf(UpstoxBrokerConnection.class);
                    assertThat(autoConfigConnection)
                            .isInstanceOf(UpstoxBrokerConnection.class);
                });
    }

    // ── FullComposition with execution ────────────────────────────

    @Test
    void fullCompositionProducesSameBrokerConnectionAsAutoConfiguration() {
        BrokerProfile profile = new BrokerProfile(
                BrokerProfile.BrokerType.DHAN,
                new BrokerProfile.DhanConfig(
                        "test-client", "test-token",
                        DhanApiEnvironment.SANDBOX, null,
                        DhanAuthMode.STATIC, null, null, null,
                        30, false, null
                ),
                null, null
        );

        FullComposition fullComposition = FullComposition.createFull(
                profile, StorageProfile.defaults(), RiskProfile.defaults());

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DhanAutoConfiguration.class))
                .withPropertyValues(
                        "trade.broker-type=dhan",
                        "trade.dhan.client-id=test-client",
                        "trade.dhan.access-token=test-token",
                        "trade.dhan.auth-mode=STATIC"
                )
                .run(context -> {
                    IBrokerConnection autoConfigConnection = context.getBean(IBrokerConnection.class);

                    assertThat(fullComposition.brokerConnection().getClass())
                            .isEqualTo(autoConfigConnection.getClass());
                });
    }

    // ── Both paths provide lifecycle manager ───────────────────────

    @Test
    void compositionAndAutoConfigurationBothProvideLifecycleManager() {
        BrokerProfile profile = new BrokerProfile(
                BrokerProfile.BrokerType.DHAN,
                new BrokerProfile.DhanConfig(
                        "c", "t", DhanApiEnvironment.SANDBOX, null,
                        DhanAuthMode.STATIC, null, null, null, 30, false, null
                ),
                null, null
        );

        FullComposition composition = FullComposition.brokerOnly(profile);
        assertThat(composition.lifecycleManager()).isNotNull();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DhanAutoConfiguration.class))
                .withPropertyValues(
                        "trade.broker-type=dhan",
                        "trade.dhan.client-id=c",
                        "trade.dhan.access-token=t",
                        "trade.dhan.auth-mode=STATIC"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            com.tradej.broker.core.startup.BrokerLifecycleManager.class);
                });
    }
}
