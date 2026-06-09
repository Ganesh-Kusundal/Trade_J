package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.brokergateway.BrokerGateway;
import com.tradej.brokergateway.BrokerHandle;
import com.tradej.brokergateway.explorer.BrokerExplorer;
import com.tradej.brokergateway.explorer.BrokerInspectionReport;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.core.domain.instrument.IndexSymbols;
import com.tradej.core.domain.instrument.Instruments;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.OptionQuote;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live smoke test for the broker-gateway dynamic-dispatch surface.
 *
 * <p>Wires a real {@link DhanBrokerConnection} through {@link BrokerGateway} and exercises
 * the new {@code optionGreeks} API on {@link BrokerHandle} and its {@code BrokerExtras} escape
 * hatch. Skips silently when no live Dhan credentials are available.
 */
@Tag("integration")
@Tag("broker-rest")
class BrokerGatewayLiveConnectionTest {

    private DhanBrokerConnection brokerConnection;
    private BrokerGateway gateway;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void capabilitiesMapReportsOptionsProvider() throws Exception {
        connect();

        BrokerHandle handle = gateway.broker("dhan");
        Map<String, Boolean> capabilities = handle.capabilities();

        assertFalse(capabilities.isEmpty(), "Capability map should not be empty for a connected Dhan broker.");
        assertEquals(Boolean.TRUE, capabilities.get("OptionsProvider"),
                "Dhan must expose OptionsProvider for the optionGreeks API to work.");
    }

    @Test
    void inspectionReportShowsResolvedGreeksThroughLiveProbe() throws Exception {
        connect();

        BrokerInspectionReport report = BrokerExplorer.inspect(gateway.broker("dhan"));

        assertTrue(report.totalCount() > 0);
        assertTrue(report.supportedCount() >= 1,
                "At least one capability should be supported (OptionsProvider is always present on Dhan).");
    }

    @Test
    void greeksHandleMethodReturnsSuccessfulResult() throws Exception {
        connect();
        InstrumentKeyRef key = resolveNiftyOption();

        GatewayResult<OptionQuote> result = gateway.broker("dhan").greeks(key.value);

        assertNotNull(result, "handle.greeks(key) must not return null.");
        assertTrue(result.isSuccess(), "Live NIFTY option greeks should succeed but data was null.");
        assertNotNull(result.data(), "Successful greeks call should carry an OptionQuote payload.");
        assertNotNull(result.data().greeks(), "Live OptionQuote should carry greeks (delta/theta/gamma/vega/iv).");
    }

    // ── End-to-end canonical index name resolution (RP-100) ─────────

    @Test
    void ltpByCanonicalIndexNameResolvesViaDhanSecurityId() throws Exception {
        connect();

        // Caller uses the canonical NSE name from Instruments.bankNifty() — the
        // public broker-gateway surface. The Dhan instrument catalog must accept
        // "NIFTY BANK" (not just "BANKNIFTY") and return a real LTP from the wire.
        BrokerHandle handle = gateway.broker("dhan");
        GatewayResult<Long> result = handle.ltp(
                Instruments.bankNifty().symbol(), ExchangeSegment.IDX_I);

        assertTrue(result.isSuccess(),
                "Canonical NSE name (NIFTY BANK) must resolve end-to-end and return a live LTP, but data was null.");
        assertNotNull(result.data());
        assertTrue(result.data() > 0L, "Live LTP must be positive for NIFTY BANK index, was: " + result.data());
    }

    @Test
    void ltpByBrokerAliasResolvesToSameDhanSecurityId() throws Exception {
        connect();

        // Caller uses the legacy broker alias "BANKNIFTY" — the surface still works.
        // Both the canonical path and the alias path must reach the same Dhan securityId
        // and return comparable LTPs.
        BrokerHandle handle = gateway.broker("dhan");
        GatewayResult<Long> viaAlias = handle.ltp("BANKNIFTY");
        GatewayResult<Long> viaCanonical = handle.ltp(
                Instruments.bankNifty().symbol(), ExchangeSegment.IDX_I);

        assertTrue(viaAlias.isSuccess(), "Broker alias BANKNIFTY must resolve, was: " + viaAlias);
        assertTrue(viaCanonical.isSuccess(), "Canonical NIFTY BANK must resolve, was: " + viaCanonical);
        assertNotNull(viaAlias.data());
        assertNotNull(viaCanonical.data());
        // Same underlying index — LTPs should be within 1% of each other.
        long diff = Math.abs(viaAlias.data() - viaCanonical.data());
        long max = Math.max(viaAlias.data(), viaCanonical.data());
        assertTrue(diff * 100 < max,
                "Alias and canonical LTPs diverged by more than 1%: alias=" + viaAlias.data()
                        + " canonical=" + viaCanonical.data());
    }

    @Test
    void quoteByCanonicalNiftyIndexResolves() throws Exception {
        connect();

        BrokerHandle handle = gateway.broker("dhan");
        GatewayResult<com.tradej.core.domain.model.Quote> result = handle.quote(
                Instruments.nifty().symbol(), ExchangeSegment.IDX_I);

        assertTrue(result.isSuccess(), "Canonical NIFTY must resolve, was: " + result);
        assertNotNull(result.data());
        assertTrue(result.data().ltpPaisa() > 0L,
                "Live NIFTY quote LTP must be positive, was: " + result.data().ltpPaisa());
    }

    @Test
    void ohlcByCanonicalFinNiftyIndexResolves() throws Exception {
        connect();

        BrokerHandle handle = gateway.broker("dhan");
        GatewayResult<com.tradej.core.domain.model.Quote> result = handle.ohlc(
                IndexSymbols.NIFTY_FIN_SERVICE, ExchangeSegment.IDX_I);

        assertTrue(result.isSuccess(), "Canonical NIFTY FIN SERVICE must resolve, was: " + result);
        assertNotNull(result.data());
        assertTrue(result.data().ltpPaisa() > 0L,
                "Live NIFTY FIN SERVICE OHLC LTP must be positive, was: " + result.data().ltpPaisa());
    }


    // ── Helpers ─────────────────────────────────────────────────────

    private void connect() throws Exception {
        DhanConnectionSettings settings = LiveDhanTestSupport.connectionSettingsOrSkip();
        brokerConnection = DhanBrokerConnection.create(settings, new CaffeineIdempotencyCache());
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-master-cache"), false);
        gateway = BrokerGateway.of(BrokerSource.DHAN, brokerConnection);
    }

    private InstrumentKeyRef resolveNiftyOption() {
        List<LocalDate> expiries = brokerConnection.options().getExpiries("NIFTY", ExchangeSegment.IDX_I);
        assertFalse(expiries.isEmpty(), "Expected at least one NIFTY option expiry for live smoke test.");
        LocalDate nearestExpiry = expiries.getFirst();
        List<Instrument> contracts = brokerConnection.options()
                .getOptionContracts("NIFTY", ExchangeSegment.IDX_I, nearestExpiry);
        assertFalse(contracts.isEmpty(), "Expected tradable NIFTY option contracts from the instrument master.");
        Instrument first = contracts.getFirst();
        return new InstrumentKeyRef(first.key());
    }

    private record InstrumentKeyRef(com.tradej.core.domain.model.InstrumentKey value) {
    }
}
