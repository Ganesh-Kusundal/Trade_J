package com.tradej.brokergateway.spi.impl;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.brokergateway.BrokerHandle;
import com.tradej.brokergateway.simulation.PaperBrokerConnection;
import com.tradej.brokergateway.spi.BrokerHealthCheck.HealthStatus;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class BrokerHealthCheckTest {

    // ── Healthy path ────────────────────────────────────────────────

    @Test
    void checkReturnsHealthyWhenLtpSucceeds() {
        PaperBrokerConnection conn = new PaperBrokerConnection();
        BrokerHandle handle = new BrokerHandle(BrokerSource.SIMULATION, conn);
        DhanHealthCheck check = new DhanHealthCheck();

        HealthStatus status = check.check(handle);

        assertTrue(status.healthy());
        assertEquals(BrokerSource.SIMULATION, status.source());
        assertEquals("OK", status.message());
        assertNotNull(status.latency());
        assertTrue(status.timestampMs() > 0);
    }

    // ── Unhealthy: LTP returns zero ─────────────────────────────────

    @Test
    void checkReturnsUnhealthyWhenLtpFails() {
        IBrokerConnection zeroLtpConn = new StubBrokerConnection(
                key -> 0L  // LTP returns 0 → data > 0 is false → unhealthy
        );
        BrokerHandle handle = new BrokerHandle(BrokerSource.DHAN, zeroLtpConn);
        DhanHealthCheck check = new DhanHealthCheck();

        HealthStatus status = check.check(handle);

        assertFalse(status.healthy());
        assertEquals(BrokerSource.DHAN, status.source());
        assertNotNull(status.message());
        assertFalse(status.message().isBlank());
    }

    // ── Unhealthy: Exception thrown ─────────────────────────────────

    @Test
    void checkReturnsUnhealthyWhenExceptionThrown() {
        IBrokerConnection throwingConn = new StubBrokerConnection(
                key -> { throw new RuntimeException("Connection refused"); }
        );
        BrokerHandle handle = new BrokerHandle(BrokerSource.UPSTOX, throwingConn);
        DhanHealthCheck check = new DhanHealthCheck();

        HealthStatus status = check.check(handle);

        assertFalse(status.healthy());
        assertEquals(BrokerSource.UPSTOX, status.source());
        assertTrue(status.message().contains("Connection refused"));
    }

    // ── HealthStatus factory: healthy ───────────────────────────────

    @Test
    void healthStatusHealthyFactory() {
        Duration latency = Duration.ofMillis(42);
        HealthStatus status = HealthStatus.healthy(BrokerSource.DHAN, latency);

        assertTrue(status.healthy());
        assertEquals(BrokerSource.DHAN, status.source());
        assertEquals("OK", status.message());
        assertEquals(latency, status.latency());
        assertTrue(status.timestampMs() > 0);
    }

    // ── HealthStatus factory: unhealthy ─────────────────────────────

    @Test
    void healthStatusUnhealthyFactory() {
        Duration latency = Duration.ofMillis(100);
        HealthStatus status = HealthStatus.unhealthy(BrokerSource.ICICI, "timeout", latency);

        assertFalse(status.healthy());
        assertEquals(BrokerSource.ICICI, status.source());
        assertEquals("timeout", status.message());
        assertEquals(latency, status.latency());
        assertTrue(status.timestampMs() > 0);
    }

    // ── Stub connection for failure scenarios ───────────────────────

    @FunctionalInterface
    private interface LtpFunction {
        long getLtp(InstrumentKey key);
    }

    /**
     * MarketDataProvider stub that delegates LTP to a function.
     * All other methods throw UnsupportedOperationException.
     */
    private static final class StubMarketDataProvider implements MarketDataProvider {
        private final LtpFunction ltpFunction;

        StubMarketDataProvider(LtpFunction ltpFunction) {
            this.ltpFunction = ltpFunction;
        }

        @Override
        public long getLtpPaisa(InstrumentKey key) {
            return ltpFunction.getLtp(key);
        }

        @Override public com.tradej.core.domain.model.Quote getQuote(InstrumentKey key) { throw new UnsupportedOperationException(); }
        @Override public com.tradej.core.domain.model.MarketDepth getDepth(InstrumentKey key) { throw new UnsupportedOperationException(); }
        @Override public com.tradej.core.domain.model.Quote getOhlcSnapshot(InstrumentKey key) { throw new UnsupportedOperationException(); }
        @Override public java.util.List<com.tradej.core.domain.model.Candle> getCandles(com.tradej.core.domain.model.CandleHistoryRequest req) { throw new UnsupportedOperationException(); }
        @Override public java.util.Map<InstrumentKey, Long> getLtpBatch(java.util.Collection<InstrumentKey> keys) { throw new UnsupportedOperationException(); }
        @Override public java.util.Map<InstrumentKey, com.tradej.core.domain.model.Quote> getQuoteBatch(java.util.Collection<InstrumentKey> keys) { throw new UnsupportedOperationException(); }
        @Override public java.util.Map<InstrumentKey, com.tradej.core.domain.model.Quote> getOhlcBatch(java.util.Collection<InstrumentKey> keys) { throw new UnsupportedOperationException(); }
    }

    /**
     * Minimal IBrokerConnection stub that delegates LTP to a function.
     * All other capabilities throw UnsupportedOperationException.
     */
    private static final class StubBrokerConnection implements IBrokerConnection {

        private final LtpFunction ltpFunction;

        StubBrokerConnection(LtpFunction ltpFunction) {
            this.ltpFunction = ltpFunction;
        }

        @Override
        public com.tradej.broker.api.spi.BrokerSource source() {
            return com.tradej.broker.api.spi.BrokerSource.SIMULATION;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> getCapability(Class<T> capabilityClass) {
            if (capabilityClass == MarketDataProvider.class) {
                return Optional.of((T) new StubMarketDataProvider(ltpFunction));
            }
            if (capabilityClass == InstrumentResolver.class) {
                return Optional.of((T) new PaperInstrumentResolver());
            }
            return Optional.empty();
        }

        @Override public void connect() { }
        @Override public void disconnect() { }
        @Override public void loadInstrumentCatalog(java.nio.file.Path catalogPath) { }
    }

    /**
     * Minimal InstrumentResolver that resolves any symbol to a valid Instrument.
     */
    private static final class PaperInstrumentResolver implements InstrumentResolver {
        @Override
        public com.tradej.core.domain.model.Instrument resolve(InstrumentKey key) {
            return toInstrument(key);
        }

        @Override
        public com.tradej.core.domain.model.Instrument getBySymbol(InstrumentKey key) {
            return toInstrument(key);
        }

        @Override
        public com.tradej.core.domain.model.Instrument resolveNormalized(String symbol, ExchangeSegment segment) {
            return toInstrument(new InstrumentKey(symbol, segment));
        }

        @Override
        public java.util.List<com.tradej.core.domain.model.Instrument> allInstruments() {
            return java.util.List.of();
        }

        @Override
        public com.tradej.core.domain.model.Instrument resolveBySecurityId(String securityId) {
            return toInstrument(new InstrumentKey(securityId, ExchangeSegment.NSE_EQ));
        }

        @Override
        public com.tradej.core.domain.model.Instrument requireDefinition(InstrumentKey key) {
            return toInstrument(key);
        }

        @Override
        public com.tradej.core.domain.model.Instrument resolvePayload(Object payload) {
            throw new UnsupportedOperationException();
        }

        @Override public boolean isLoaded() { return true; }
        @Override public int catalogSize() { return 1; }

        private static com.tradej.core.domain.model.Instrument toInstrument(InstrumentKey key) {
            return new com.tradej.core.domain.model.Instrument(
                    key.symbol(), key.symbol(),
                    key.exchangeSegment().exchange(),
                    key.exchangeSegment(),
                    "EQ", null, null, null, null, 1L, 5L);
        }
    }
}
