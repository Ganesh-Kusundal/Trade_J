package com.tradej.app.health;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerTransportCapabilities;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.broker.upstox.auth.UpstoxBearerTokenSource;
import com.tradej.broker.upstox.http.UpstoxApiException;
import com.tradej.core.domain.value.ExchangeSegment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component("upstox")
@ConditionalOnBean(name = "upstoxBrokerConnection")
public class UpstoxHealthIndicator implements HealthIndicator {

    private final IBrokerConnection brokerConnection;
    private final UpstoxBearerTokenSource tokenSource;
    private final BrokerTransportCapabilities transportCapabilities;
    private final MarketDataProvider marketDataProvider;
    private final Clock clock;
    private final AlertManager alertManager;

    public UpstoxHealthIndicator(
            IBrokerConnection brokerConnection,
            UpstoxBearerTokenSource tokenSource,
            ObjectProvider<BrokerTransportCapabilities> transportCapabilitiesProvider,
            MarketDataProvider marketDataProvider,
            AlertManager alertManager
    ) {
        this.brokerConnection = brokerConnection;
        this.tokenSource = tokenSource;
        this.transportCapabilities = transportCapabilitiesProvider.getIfUnique();
        this.marketDataProvider = marketDataProvider;
        this.clock = Clock.systemUTC();
        this.alertManager = alertManager;
    }

    @Override
    public Health health() {
        boolean wsConnected = brokerConnection.websocket() != null && brokerConnection.websocket().isConnected();
        long expiryEpochMs = tokenSource.expiryEpochMs();
        boolean tokenValid = expiryEpochMs <= 0 || expiryEpochMs > clock.millis();
        boolean restOk = probeRestMarketData();
        boolean analyticsOnly = transportCapabilities != null && transportCapabilities.analyticsOnly();
        boolean healthy = analyticsOnly ? (tokenValid && restOk) : wsConnected;

        if (!healthy && alertManager != null) {
            alertManager.critical("broker-upstox", "Upstox unhealthy: ws=" + wsConnected + " token=" + tokenValid + " rest=" + restOk);
        }

        Health.Builder builder = healthy ? Health.up() : Health.down();
        builder
                .withDetail("broker", "upstox")
                .withDetail("analyticsOnly", analyticsOnly)
                .withDetail("tokenValid", tokenValid)
                .withDetail("restMarketDataOk", restOk)
                .withDetail("websocketExpected",
                        transportCapabilities != null && transportCapabilities.supportsWebSocket())
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
        if (transportCapabilities == null || !transportCapabilities.supportsRestMarketData()) {
            return false;
        }
        try {
            tokenSource.ensureValid();
            long ltp = marketDataProvider.getLtpPaisa(InstrumentKey.of("SBIN", ExchangeSegment.NSE_EQ));
            return ltp > 0;
        } catch (UpstoxApiException ex) {
            return false;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
