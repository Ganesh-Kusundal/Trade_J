package com.tradej.broker.dhan.constants;

import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class DhanApiUrlResolverUnitTest {
    @Test
    void liveBaseUrlBuildsV2Paths() {
        DhanConnectionSettings settings = DhanConnectionSettings.liveWithDefaults("client", "token");
        DhanApiUrlResolver resolver = new DhanApiUrlResolver(settings);

        assertEquals("https://api.dhan.co/v2/orders", resolver.ordersUrl());
        assertEquals("https://api.dhan.co/v2/orders/ord-1", resolver.orderUrl("ord-1"));
        assertEquals("https://api.dhan.co/v2/fundlimit", resolver.fundLimitUrl());
        assertEquals("https://api.dhan.co/v2/optionchain", resolver.optionChainUrl());
    }

    @Test
    void sandboxBaseUrlBuildsSandboxPaths() {
        DhanConnectionSettings settings = DhanConnectionSettings.sandboxWithDefaults("sandbox-client", "token");
        DhanApiUrlResolver resolver = new DhanApiUrlResolver(settings);

        assertEquals("https://sandbox.dhan.co/v2/orders", resolver.ordersUrl());
        assertEquals("https://sandbox.dhan.co/v2/super-order", resolver.superOrderUrl());
        assertEquals("https://sandbox.dhan.co/v2/forever-orders", resolver.foreverOrdersUrl());
    }

    @Test
    void trimsTrailingSlashFromCustomBaseUrl() {
        DhanApiUrlResolver resolver = new DhanApiUrlResolver("https://sandbox.dhan.co/v2/");
        assertEquals("https://sandbox.dhan.co/v2/charts/historical", resolver.historicalDailyUrl());
    }

    @Test
    void defaultBaseUrlMatchesEnvironment() {
        assertEquals("https://api.dhan.co/v2", DhanConnectionSettings.defaultBaseUrl(DhanApiEnvironment.LIVE));
        assertEquals("https://sandbox.dhan.co/v2", DhanConnectionSettings.defaultBaseUrl(DhanApiEnvironment.SANDBOX));
    }
}
