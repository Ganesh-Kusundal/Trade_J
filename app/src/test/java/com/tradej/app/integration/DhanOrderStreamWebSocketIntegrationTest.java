package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.event.StreamHealthChanged;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-ws")
class DhanOrderStreamWebSocketIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void connectsAuthenticatedOrderStreamWhenEnabled() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_ORDER_STREAM_TEST_ENABLED", "dhan.orderStreamTestEnabled", "false")));

        brokerConnection = new DhanBrokerConnection(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );

        CountDownLatch connected = new CountDownLatch(1);
        brokerConnection.websocket().onOrderUpdate(event -> {
            if (event instanceof StreamHealthChanged health
                    && "dhan-order".equals(health.broker())
                    && "CONNECTED".equals(health.status())) {
                connected.countDown();
            }
        });

        brokerConnection.connect();

        assertTrue(connected.await(20, TimeUnit.SECONDS),
                "Expected authenticated Dhan order stream to report CONNECTED.");
    }
}
