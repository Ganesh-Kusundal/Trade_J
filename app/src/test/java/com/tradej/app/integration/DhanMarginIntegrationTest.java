package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-rest")
class DhanMarginIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void estimatesMarginForLiveOrderShape() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_MARGIN_TEST_ENABLED", "dhan.marginTestEnabled", "false")));
        brokerConnection = DhanBrokerConnection.create(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-margin-cache"), false);

        MarginEstimate estimate = brokerConnection.margin().estimateMargin(new MarginEstimateRequest(
                LiveDhanTestSupport.value("DHAN_MARGIN_SYMBOL", "dhan.marginSymbol", "TCS"),
                ExchangeSegment.valueOf(LiveDhanTestSupport.value("DHAN_MARGIN_SEGMENT", "dhan.marginSegment", "NSE_EQ")),
                Side.BUY,
                1L,
                ProductType.INTRADAY,
                OrderType.MARKET,
                0L,
                0L
        ));

        assertTrue(estimate.totalMarginPaisa() >= 0L);
    }
}
