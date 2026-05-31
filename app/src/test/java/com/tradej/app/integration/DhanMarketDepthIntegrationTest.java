package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.dhan.exceptions.DhanHttpException;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-rest")
class DhanMarketDepthIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void fetchesRestMarketDepthForConfiguredSymbol() throws Exception {
        brokerConnection = DhanBrokerConnection.create(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-depth-cache"), false);

        InstrumentKey key = new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ);
        MarketDepth depth;
        try {
            depth = brokerConnection.marketData().getDepth(key);
        } catch (DhanHttpException ex) {
            Assumptions.assumeTrue(false,
                    "Skipping: Dhan depth API unavailable for SBIN/NSE_EQ (likely outside market hours): "
                            + ex.getMessage());
            throw new AssertionError("unreachable");
        }
        assertNotNull(depth);
        assertNotNull(depth.instrument());
        assertEquals(key.symbol(), depth.instrument().canonicalSymbol());
        assertEquals(key.exchangeSegment(), depth.instrument().exchangeSegment());

        boolean hasBid = depth.bids().stream().anyMatch(level -> level.pricePaisa() > 0L);
        boolean hasAsk = depth.asks().stream().anyMatch(level -> level.pricePaisa() > 0L);
        if (!depth.bids().isEmpty() || !depth.asks().isEmpty()) {
            assertTrue(hasBid || hasAsk, "Depth book should expose at least one priced bid or ask level.");
            for (DepthLevel level : depth.bids()) {
                assertTrue(level.pricePaisa() >= 0L);
            }
            for (DepthLevel level : depth.asks()) {
                assertTrue(level.pricePaisa() >= 0L);
            }
        }
    }
}
