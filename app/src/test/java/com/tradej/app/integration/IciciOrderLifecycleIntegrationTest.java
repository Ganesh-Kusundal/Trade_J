package com.tradej.app.integration;

import com.tradej.broker.icici.adapter.IciciOrderCommandAdapter;
import com.tradej.broker.icici.auth.BreezeTokenManager;
import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.broker.icici.http.BreezeAuthenticatedHttpClient;
import com.tradej.broker.icici.instrument.BreezeInstrumentLoader;
import com.tradej.broker.icici.instrument.BreezeInstrumentResolver;
import com.tradej.broker.icici.mapper.BreezeDomainMapper;
import com.tradej.broker.icici.rest.BreezeOrderRestClient;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
@Tag("broker-order")
class IciciOrderLifecycleIntegrationTest {

    @Test
    void rejectsMarketOrdersWithoutCallingBroker() {
        BreezeConnectionSettings settings = LiveIciciTestSupport.connectionSettingsOrSkip();
        Assumptions.assumeTrue(settings.ordersEnabled(),
                "Set ICICI_ORDER_TEST_ENABLED=true and register static IP before live order tests.");
        LiveIciciTestSupport.preflightSessionOrSkip(settings);

        BreezeTokenManager tokenManager = new BreezeTokenManager(settings);
        BreezeInstrumentResolver resolver = new BreezeInstrumentResolver(new BreezeInstrumentLoader());
        resolver.loadFromRemote();
        IciciOrderCommandAdapter adapter = new IciciOrderCommandAdapter(
                new BreezeOrderRestClient(new BreezeAuthenticatedHttpClient(tokenManager)),
                new BreezeDomainMapper(),
                resolver,
                settings
        );

        OrderRequest marketOrder = new OrderRequest(
                "RELIANCE",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                1L,
                OrderType.MARKET,
                0L,
                0L,
                ProductType.CNC,
                Validity.DAY,
                "test"
        );
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> adapter.placeOrder(marketOrder)
        );
    }
}
