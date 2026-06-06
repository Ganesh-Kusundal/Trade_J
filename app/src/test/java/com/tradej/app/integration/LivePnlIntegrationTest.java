package com.tradej.app.integration;

import com.tradej.execution.marketdata.LivePnlService;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.LivePnlSnapshot;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("integration")
@Tag("broker-rest")
class LivePnlIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void computesLivePnlSnapshot() throws Exception {
        brokerConnection = DhanBrokerConnection.create(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-livepnl-cache"), false);

        LivePnlService service = new LivePnlService(
                brokerConnection.portfolio(),
                brokerConnection.marketData(),
                brokerConnection.instruments()
        );
        LivePnlSnapshot snapshot = service.getLivePnl();

        assertNotNull(snapshot);
    }
}
