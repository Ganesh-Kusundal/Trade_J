package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.PnlExitPolicy;
import com.tradej.core.domain.model.PnlExitResult;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("integration")
@Tag("broker-order")
class DhanSessionRiskIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        LiveDhanTestSupport.cancelTrackedSandboxOrders(brokerConnection);
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void enablesPnlExitWhenEnabled() {
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_PNL_EXIT_TEST_ENABLED", "dhan.pnlExitTestEnabled", "false")));
        brokerConnection = new DhanBrokerConnection(
                LiveDhanTestSupport.sandboxConnectionSettingsOrSkip(),
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );
        PnlExitResult result = LiveDhanTestSupport.assumeSandboxSupported(
                () -> brokerConnection.sessionRisk().enablePnlExit(new PnlExitPolicy(
                        10_000L,
                        10_000L,
                        false
                )),
                "pnl-exit");
        assertNotNull(result);
    }
}
