package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.ConditionalAlertRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("integration")
@Tag("broker-order")
class DhanAlertIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        LiveDhanTestSupport.cancelTrackedSandboxOrders(brokerConnection);
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void createsAndDeletesAlertWhenEnabled() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_ALERT_TEST_ENABLED", "dhan.alertTestEnabled", "false")));
        brokerConnection = new DhanBrokerConnection(
                LiveDhanTestSupport.sandboxConnectionSettingsOrSkip(),
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-alert-cache"), false);

        String alertId = brokerConnection.alerts().placeAlert(new ConditionalAlertRequest(
                LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_ALERT_SYMBOL", "dhan.alertSymbol", "TCS"),
                ExchangeSegment.valueOf(LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_ALERT_SEGMENT", "dhan.alertSegment", "NSE_EQ")),
                Side.BUY,
                1L,
                OrderType.LIMIT,
                ProductType.INTRADAY,
                Long.parseLong(LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_ALERT_PRICE_PAISE", "dhan.alertPricePaise", "10000")),
                0L,
                Validity.DAY,
                "PRICE_WITH_VALUE",
                "GREATER_THAN",
                "DAY",
                1.0,
                null,
                null,
                "ONCE",
                null,
                "trade-j parity test"
        ));
        assertNotNull(alertId);
        assertFalse(alertId.isBlank());
        brokerConnection.alerts().deleteAlert(alertId);
    }
}
