package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.OptionQuote;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-rest")
class DhanDerivativesIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void resolvesNearestMcxFutureFromDailyInstrumentMaster() throws Exception {
        connectWithDailyInstrumentMaster();

        List<LocalDate> expiries = brokerConnection.futures().getExpiries("CRUDEOIL", ExchangeSegment.MCX_COMM);
        Instrument nearest = brokerConnection.futures().getNearestContract("CRUDEOIL", ExchangeSegment.MCX_COMM);

        assertFalse(expiries.isEmpty(), "Expected at least one MCX futures expiry for CRUDEOIL.");
        assertNotNull(nearest.expiry(), "Nearest MCX futures contract should expose an expiry.");
        assertTrue(nearest.isFuture(), "Nearest MCX contract should normalize as a future.");
    }

    @Test
    void resolvesNearestNfoFutureFromDailyInstrumentMaster() throws Exception {
        connectWithDailyInstrumentMaster();

        Instrument nearest = brokerConnection.futures().getNearestContract("NIFTY", ExchangeSegment.NSE_FNO);

        assertNotNull(nearest.expiry(), "Nearest NFO futures contract should expose an expiry.");
        assertTrue(nearest.isFuture(), "Nearest NFO contract should normalize as a future.");
        assertEquals(ExchangeSegment.NSE_FNO, nearest.exchangeSegment(), "Nearest NFO contract should stay on the derivatives venue.");
    }

    @Test
    void fetchesLiveNiftyOptionChainThroughBrokerBoundary() throws Exception {
        connectWithDailyInstrumentMaster();

        // Get expiries from live API
        List<LocalDate> expiries = brokerConnection.options().getExpiries("NIFTY", ExchangeSegment.IDX_I);
        assertFalse(expiries.isEmpty(), "Expected at least one option expiry for NIFTY.");
        assertEquals(expiries, expiries.stream().sorted().toList(), "Expiries from Dhan expirylist should be sorted.");

        LocalDate nearestExpiry = expiries.getFirst();
        
        // Get option chain directly from live API (includes all contracts with live data)
        OptionChainSnapshot chain = brokerConnection.options().getOptionChain("NIFTY", ExchangeSegment.IDX_I, nearestExpiry);
        
        // Verify chain has strikes with live data
        assertTrue(chain.spotPricePaisa() > 0L, "Option chain should expose the underlying spot price.");
        assertFalse(chain.strikes().isEmpty(), "Expected the live option chain to contain strikes.");
        assertTrue(chain.strikes().stream().anyMatch(entry -> entry.call() != null || entry.put() != null),
                "Expected at least one resolved option leg in the live option chain.");
        
        // Extract contracts from the live chain (no need for separate getOptionContracts call)
        long contractCount = chain.strikes().stream()
                .mapToLong(entry -> (entry.call() != null ? 1 : 0) + (entry.put() != null ? 1 : 0))
                .sum();
        assertTrue(contractCount > 0, "Expected option contracts in the live chain.");
        
        // Get greeks from live data
        OptionQuote firstLeg = chain.strikes().stream()
                .map(entry -> entry.call() != null ? entry.call() : entry.put())
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow();
        OptionQuote greeks = brokerConnection.options().getGreeks(firstLeg.instrument().key());
        
        assertNotNull(greeks.greeks(), "Resolved option greeks should be available through the broker boundary.");
    }

    @Test
    void fetchesStockOptionExpiriesAndChainForTcs() throws Exception {
        connectWithDailyInstrumentMaster();

        List<LocalDate> expiries = brokerConnection.options().getExpiries("TCS", ExchangeSegment.NSE_EQ);
        assertFalse(expiries.isEmpty(), "Expected at least one TCS option expiry from Dhan expirylist.");
        assertEquals(expiries, expiries.stream().sorted(Comparator.naturalOrder()).toList());

        LocalDate nearestExpiry = expiries.getFirst();
        OptionChainSnapshot chain = brokerConnection.options().getOptionChain("TCS", ExchangeSegment.NSE_EQ, nearestExpiry);

        assertTrue(chain.spotPricePaisa() > 0L, "TCS option chain should expose underlying spot.");
        assertFalse(chain.strikes().isEmpty(), "Expected TCS option chain strikes for " + nearestExpiry + ".");
    }

    private void connectWithDailyInstrumentMaster() throws Exception {
        brokerConnection = new DhanBrokerConnection(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-master-cache"), false);
    }
}
