package com.tradej.app.startup;

import com.tradej.app.config.TradingProperties;
import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.core.domain.runtime.RuntimeMode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BrokerStartupValidatorTest {

    private static TradingProperties.DhanProperties dhan(String clientId, String accessToken) {
        return new TradingProperties.DhanProperties(
                clientId, accessToken, DhanApiEnvironment.LIVE, null, false,
                3, 5, true, true, DhanAuthMode.STATIC,
                null, null, null, 10L, 5L
        );
    }

    private TradingProperties propsWithBroker(String apiKey, String accessToken) {
        return new TradingProperties(
                dhan(apiKey, accessToken),
                null, null, null, null, null,
                new TradingProperties.InstrumentProperties(null, null, false),
                null,
                new TradingProperties.RuntimeProperties(RuntimeMode.LIVE),
                null, null, null, null, null, null, null, Map.of()
        );
    }

    private TradingProperties propsWithMode(RuntimeMode mode) {
        return new TradingProperties(
                null, null, null, null, null, null, null, null,
                new TradingProperties.RuntimeProperties(mode),
                null, null, null, null, null, null, null, Map.of()
        );
    }

    @Test
    void validLiveConfigPasses() {
        var props = propsWithBroker("test-api-key", "test-token");
        var validator = new BrokerStartupValidatorAdapter(props);
        assertDoesNotThrow(() -> validator.run(null));
    }

    @Test
    void liveModeMissingBrokerThrows() {
        var props = propsWithMode(RuntimeMode.LIVE);
        var validator = new BrokerStartupValidatorAdapter(props);
        var ex = assertThrows(IllegalStateException.class, () -> validator.run(null));
        assertTrue(ex.getMessage().contains("client-id"));
    }

    @Test
    void liveModeMissingClientIdThrows() {
        var props = propsWithBroker(null, "test-token");
        var validator = new BrokerStartupValidatorAdapter(props);
        var ex = assertThrows(IllegalStateException.class, () -> validator.run(null));
        assertTrue(ex.getMessage().contains("client-id"));
    }

    @Test
    void liveModeBlankClientIdThrows() {
        var props = propsWithBroker("", "test-token");
        var validator = new BrokerStartupValidatorAdapter(props);
        var ex = assertThrows(IllegalStateException.class, () -> validator.run(null));
        assertTrue(ex.getMessage().contains("client-id"));
    }

    @Test
    void backtestModePassesWithoutBrokerConfig() {
        var validator = new BrokerStartupValidatorAdapter(propsWithMode(RuntimeMode.BACKTEST));
        assertDoesNotThrow(() -> validator.run(null));
    }

    @Test
    void replayModePassesWithoutBrokerConfig() {
        var validator = new BrokerStartupValidatorAdapter(propsWithMode(RuntimeMode.REPLAY));
        assertDoesNotThrow(() -> validator.run(null));
    }

    @Test
    void missingInstrumentCsvWarnsButDoesNotThrow() {
        var props = new TradingProperties(
                null, null, null, null, null, null, null, null,
                new TradingProperties.RuntimeProperties(RuntimeMode.BACKTEST),
                null, null, null, null, null, null, null, Map.of()
        );
        var validator = new BrokerStartupValidatorAdapter(props);
        assertDoesNotThrow(() -> validator.run(null));
    }
}
