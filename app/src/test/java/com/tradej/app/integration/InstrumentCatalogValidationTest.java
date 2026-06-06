package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("live")
@Isolated
class InstrumentCatalogValidationTest {

    private DhanBrokerConnection connection;

    @BeforeEach
    void setUp() throws Exception {
        connection = DhanBrokerConnection.create(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                new CaffeineIdempotencyCache());
        connection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-catalog-val"), false);
    }

    @AfterEach
    void tearDown() {
        if (connection != null) connection.disconnect();
    }

    @Test
    void catalogIsLoaded() {
        int size = connection.instruments().catalogSize();
        assertTrue(size > 1000, "Catalog must have >1000 instruments, got: " + size);
    }

    @Test
    void resolveRelianceNseEq() {
        Instrument inst = connection.instruments().resolveNormalized("RELIANCE", ExchangeSegment.NSE_EQ);
        assertNotNull(inst, "Must resolve RELIANCE on NSE_EQ");
        assertEquals("RELIANCE", inst.symbol());
    }

    @Test
    void resolveNiftyIndex() {
        Instrument inst = connection.instruments().resolveNormalized("NIFTY", ExchangeSegment.IDX_I);
        assertNotNull(inst, "Must resolve NIFTY on IDX_I");
    }

    @Test
    void optionExpiriesAvailable() {
        List<LocalDate> expiries = connection.options().getExpiries("NIFTY", ExchangeSegment.IDX_I);
        assertNotNull(expiries, "Expiries must not be null");
        assertFalse(expiries.isEmpty(), "Must have at least one expiry");
    }

    @Test
    void optionChainHasStrikes() {
        List<LocalDate> expiries = connection.options().getExpiries("NIFTY", ExchangeSegment.IDX_I);
        if (!expiries.isEmpty()) {
            OptionChainSnapshot chain = connection.options().getOptionChain(
                    "NIFTY", ExchangeSegment.IDX_I, expiries.getFirst());
            assertNotNull(chain, "Option chain must not be null");
        }
    }

    @Test
    void optionChainStrikesArePositive() {
        List<LocalDate> expiries = connection.options().getExpiries("NIFTY", ExchangeSegment.IDX_I);
        if (!expiries.isEmpty()) {
            OptionChainSnapshot chain = connection.options().getOptionChain(
                    "NIFTY", ExchangeSegment.IDX_I, expiries.getFirst());
            if (chain != null && chain.strikes() != null) {
                for (var strike : chain.strikes()) {
                    assertTrue(strike.strikePricePaisa() > 0,
                            "Strike price must be positive, got: " + strike.strikePricePaisa());
                }
            }
        }
    }
}
