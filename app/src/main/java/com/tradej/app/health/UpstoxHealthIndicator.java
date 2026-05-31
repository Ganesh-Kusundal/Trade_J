package com.tradej.app.health;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerTransportCapabilities;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.broker.upstox.auth.UpstoxBearerTokenSource;
import com.tradej.broker.upstox.http.UpstoxApiException;
import com.tradej.core.domain.value.ExchangeSegment;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component("upstox")
@ConditionalOnBean(name = "upstoxBrokerConnection")
public class UpstoxHealthIndicator implements HealthIndicator {

    private final IBrokerConnection brokerConnection;
    private final UpstoxBearerTokenSource tokenSource;
    private final BrokerTransportCapabilities transportCapabilities;
    private final MarketDataProvider marketDataProvider;

    public UpstoxHealthIndicator(
            IBrokerConnection brokerConnection,
            UpstoxBearerTokenSource tokenSource,
            BrokerTransportCapabilities transportCapabilities,
            MarketDataProvider marketDataProvider
    ) {
        this.brokerConnection = brokerConnection;
        this.tokenSource = tokenSource;
        this.transportCapabilities = transportCapabilities;
        this.marketDataProvider = marketDataProvider;
    }

    @Override
    public Health health() {
        boolean analyticsOnly = transportCapabilities.analyticsOnly();
        boolean wsConnected = brokerConnection.websocket() != null && brokerConnection.websocket().isConnected();
        long expiryEpochMs = tokenSource.expiryEpochMs();
        boolean tokenValid = expiryEpochMs <= 0 || expiryEpochMs > System.currentTimeMillis();
        boolean restOk = probeRestMarketData();
        boolean healthy = analyticsOnly ? (tokenValid && restOk) : wsConnected;

        Health.Builder builder = healthy ? Health.up() : Health.down();
        builder
                .withDetail("broker", "upstox")
                .withDetail("analyticsOnly", analyticsOnly)
                .withDetail("tokenValid", tokenValid)
                .withDetail("restMarketDataOk", restOk)
                .withDetail("websocketExpected", transportCapabilities.supportsWebSocket())
                .withDetail("websocketConnected", wsConnected)
                .withDetail("subscriptions",
                        brokerConnection.websocket() != null
                                ? brokerConnection.websocket().subscriptions().size()
                                : 0);
        if (expiryEpochMs > 0) {
            builder.withDetail("tokenExpiresAt", Instant.ofEpochMilli(expiryEpochMs).toString());
        }
        return builder.build();
    }

    private boolean probeRestMarketData() {
        if (!transportCapabilities.supportsRestMarketData()) {
            return false;
        }
        try {
            tokenSource.ensureValid();
            long ltp = marketDataProvider.getLtpPaisa(new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ));
            return ltp > 0;
        } catch (UpstoxApiException ex) {
            return false;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
