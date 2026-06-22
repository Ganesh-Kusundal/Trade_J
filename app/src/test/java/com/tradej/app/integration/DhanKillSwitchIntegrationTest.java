package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-rest")
class DhanKillSwitchIntegrationTest {
    private DhanBrokerConnection brokerConnection;
    private boolean killSwitchEnabled;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null && killSwitchEnabled) {
            try {
                brokerConnection.orders().setKillSwitch(false);
            } catch (Exception ignored) {
                // Best-effort restore; account may require manual reset.
            }
            brokerConnection.disconnect();
        } else if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void togglesLiveKillSwitchWhenEnabled() {
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_KILL_SWITCH_TEST_ENABLED", "dhan.killSwitchTestEnabled", "false")));

        brokerConnection = new DhanBrokerConnection(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );

        assertTrue(brokerConnection.orders().setKillSwitch(true));
        killSwitchEnabled = true;
        assertTrue(brokerConnection.orders().getKillSwitchStatus().isPresent(),
                "Kill-switch status should be broker-confirmed after enabling.");
        assertTrue(brokerConnection.orders().setKillSwitch(false));
        killSwitchEnabled = false;
        assertTrue(brokerConnection.orders().getKillSwitchStatus().isPresent(),
                "Kill-switch status should be broker-confirmed after disabling.");
    }
}
