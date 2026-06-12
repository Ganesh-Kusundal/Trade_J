package com.tradej.broker.upstox.auth;

import com.tradej.broker.api.auth.BrokerTokenSource;
import com.tradej.broker.api.auth.BrokerTokenSourceContractTest;
import org.junit.jupiter.api.Tag;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;

/**
 * Concrete {@link BrokerTokenSource} contract test for the Upstox
 * implementation. Uses the static token holder (which implements
 * {@link BrokerTokenSource} via {@link UpstoxBearerTokenSource}) with
 * a JWT whose {@code exp} is far in the future.
 */
@Tag("unit")
class UpstoxBrokerTokenSourceContractTest extends BrokerTokenSourceContractTest {

    @Override
    protected BrokerTokenSource createSource() {
        // Hand-craft a JWT with a far-future expiry so ensureValid() is happy.
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        long expSeconds = Instant.parse("2099-01-01T00:00:00Z").getEpochSecond();
        String payload = base64Url("{\"sub\":\"test\",\"exp\":" + expSeconds + "}");
        String fakeJwt = header + "." + payload + ".sig";
        return new UpstoxStaticTokenHolder(
                fakeJwt,
                false,
                "Upstox contract test token",
                Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneId.of("UTC"))
        );
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
