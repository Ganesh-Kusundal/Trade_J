package com.tradej.app.startup;

import com.tradej.app.config.BrokerTransportProfile;
import com.tradej.app.config.TradingProperties;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.broker.upstox.http.UpstoxApiException;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

@Component
public final class UpstoxStartupStrategy implements BrokerStartupStrategy {

    private static final Logger log = LoggerFactory.getLogger(UpstoxStartupStrategy.class);

    @Override
    public boolean matches(BrokerTransportProfile profile) {
        return profile.isUpstox();
    }

    @Override
    public Path loadCatalog(
            TradingProperties properties,
            IBrokerConnection brokerConnection,
            BrokerLifecycleManager lifecycleManager,
            BrokerTransportProfile profile
    ) {
        TradingProperties.InstrumentProperties instruments = properties.instruments();
        String cacheDirectory = instruments != null ? instruments.cacheDirectory() : null;
        if (cacheDirectory == null || cacheDirectory.isBlank()) {
            cacheDirectory = "runtime-dev/upstox-instruments";
        }
        Path loadedPath = Path.of(cacheDirectory);
        lifecycleManager.loadInstrumentCatalog(brokerConnection, loadedPath);
        return loadedPath;
    }

    @Override
    public void validateSubscriptions(
            List<TradingProperties.SubscriptionProperties> configured,
            IBrokerConnection brokerConnection,
            BrokerCapabilities brokerCapabilities,
            boolean scanEnabled,
            BrokerTransportProfile profile
    ) {
        if (configured == null || configured.isEmpty()) {
            if (!scanEnabled) {
                throw new IllegalStateException(
                        "Runtime requires at least one explicit market subscription, or enable trade.scan");
            }
            return;
        }
    }

    @Override
    public void verifyPreflight(
            IBrokerConnection brokerConnection,
            InstrumentKey seedInstrument,
            BrokerLifecycleManager lifecycleManager,
            BrokerTransportProfile profile
    ) {
        try {
            long ltp = brokerConnection.marketData().getLtpPaisa(seedInstrument);
            if (ltp <= 0) {
                throw new IllegalStateException("Upstox preflight LTP must be positive for " + seedInstrument);
            }
        } catch (RuntimeException ex) {
            if (isAuthFailure(ex)) {
                throw ex;
            }
            log.warn("Upstox preflight LTP check failed for {} (expected outside market hours): {}",
                    seedInstrument, ex.getMessage());
        }
        LocalDate latestTradingDate = BrokerLifecycleManager.latestTradingDate();
        try {
            var candles = brokerConnection.marketData().getCandles(new CandleHistoryRequest(
                    seedInstrument, "1d",
                    latestTradingDate.minusDays(7), latestTradingDate
            ));
            if (candles.isEmpty()) {
                log.warn("Upstox preflight historical request returned zero candles for {} (expected outside market hours)",
                        seedInstrument);
            }
        } catch (RuntimeException ex) {
            if (isAuthFailure(ex)) {
                throw ex;
            }
            log.warn("Upstox preflight candle check failed for {} (expected outside market hours): {}",
                    seedInstrument, ex.getMessage());
        }
    }

    private static boolean isAuthFailure(RuntimeException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof UpstoxApiException api && api.isAuthFailure()) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
