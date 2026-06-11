package com.tradej.broker.dhan;

import com.tradej.broker.api.spi.BrokerDescriptor;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DhanBrokerProviderTest {

    private final DhanBrokerProvider provider = new DhanBrokerProvider();

    @Test
    void sourceIsDhan() {
        assertEquals(BrokerSource.DHAN, provider.source());
    }

    @Test
    void displayNameIsDhanHQ() {
        assertEquals("DhanHQ", provider.displayName());
    }

    @Test
    void descriptorHasCorrectCapabilities() {
        BrokerDescriptor desc = provider.descriptor();
        assertNotNull(desc);
        assertEquals(BrokerSource.DHAN, desc.source());
        assertTrue(desc.capabilities().get("MarketDataProvider"));
        assertTrue(desc.capabilities().get("OptionsProvider"));
        assertTrue(desc.capabilities().get("OrderCommand"));
        assertTrue(desc.capabilities().get("SessionRiskProvider"));
        assertFalse(desc.capabilities().get("NewsProvider"));
    }

    @Test
    void descriptorHasSegments() {
        BrokerDescriptor desc = provider.descriptor();
        assertTrue(desc.supportedSegments().contains("NSE_EQ"));
        assertTrue(desc.supportedSegments().contains("NSE_FNO"));
    }

    @Test
    void descriptorHasRateLimits() {
        BrokerDescriptor desc = provider.descriptor();
        assertNotNull(desc.rateLimitInfo());
        assertFalse(desc.rateLimitInfo().isBlank());
    }

    @Test
    void inMemoryIdempotencyCacheStoresAndRemovesOrders() {
        DhanBrokerProvider.InMemoryIdempotencyCache cache = new DhanBrokerProvider.InMemoryIdempotencyCache();
        Order order = new Order(
                "order-1",
                "client-order-1",
                "RELIANCE",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                ProductType.INTRADAY,
                OrderType.LIMIT,
                OrderStatus.OPEN,
                1L,
                0L,
                250_000L,
                0L,
                System.currentTimeMillis(),
                null
        );

        Assertions.assertThat(cache.get("client-order-1")).isEmpty();

        cache.put("client-order-1", order);

        Assertions.assertThat(cache.get("client-order-1")).contains(order);

        cache.remove("client-order-1");

        Assertions.assertThat(cache.get("client-order-1")).isEmpty();
    }
}
