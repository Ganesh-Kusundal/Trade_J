package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.RollingOptionHistoryRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("integration")
@Tag("broker-rest")
class DhanRollingOptionIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void fetchesRollingOptionHistory() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_ROLLING_OPTION_TEST_ENABLED", "dhan.rollingOptionTestEnabled", "false")));

        brokerConnection = DhanBrokerConnection.create(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-rolling-cache"), false);

        var data = brokerConnection.options().getExpiredOptionHistory(new RollingOptionHistoryRequest(
                "NIFTY",
                ExchangeSegment.IDX_I,
                5,
                "EXPIRED",
                1,
                "ATM",
                "CALL",
                LocalDate.now().minusDays(7),
                LocalDate.now().minusDays(1)
        ));
        assertFalse(data.isEmpty());
    }
}
