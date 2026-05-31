package com.tradej.broker.upstox.adapter;

import com.tradej.broker.upstox.auth.UpstoxAnalyticsTokenHolder;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import com.tradej.core.domain.model.MarginEstimateRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("upstox-preflight")
class UpstoxAnalyticsPortsIntegrationTest {

    @Test
    void analyticsPortsRejectTradingOperations() {
        assertThrows(UnsupportedOperationException.class, () -> UpstoxUnsupportedPorts.ORDERS.placeOrder(null));
        assertThrows(UnsupportedOperationException.class, () -> UpstoxUnsupportedPorts.ORDER_QUERY.getOrder("1"));
        assertThrows(UnsupportedOperationException.class, () -> UpstoxUnsupportedPorts.PORTFOLIO.getBalance());
        assertThrows(UnsupportedOperationException.class, () -> UpstoxUnsupportedPorts.MARGIN.estimateMargin(
                new MarginEstimateRequest(
                        "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 1L,
                        ProductType.INTRADAY, OrderType.MARKET, 10000L, 0L)));
    }

    @Test
    void analyticsTokenHolderRequiresAnalyticsOnly() {
        assertThrows(IllegalArgumentException.class, () -> new UpstoxAnalyticsTokenHolder(
                new UpstoxConnectionSettings(
                        "id", "secret", "http://localhost/cb",
                        null, null, "token", false, false,
                        18080, 1_800_000L, 600_000L)));
    }
}
