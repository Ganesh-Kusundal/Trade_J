package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-rest")
class DhanBatchQuoteIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void fetchesBatchLtpAndQuote() throws Exception {
        brokerConnection = DhanBrokerConnection.create(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-batch-cache"), false);

        // Use a reliable liquid equity (SBIN) rather than NIFTY index — index quote data
        // may not be available via the batch quote endpoint outside market hours.
        InstrumentKey key = new InstrumentKey(
                "SBIN",
                ExchangeSegment.NSE_EQ
        );
        List<InstrumentKey> keys = List.of(key);
        Map<InstrumentKey, Long> ltp = brokerConnection.marketData().getLtpBatch(keys);
        Map<InstrumentKey, ?> quote = brokerConnection.marketData().getQuoteBatch(keys);

        Map<InstrumentKey, Quote> ohlc = brokerConnection.marketData().getOhlcBatch(keys);

        // If no LTP data is available (e.g. outside market hours), skip data assertions
        Assumptions.assumeFalse(ltp.isEmpty(), "Skipping: no LTP data available (market may be closed)");
        assertTrue(ltp.getOrDefault(key, 0L) > 0L);
        Assumptions.assumeFalse(quote.isEmpty(), "Skipping: no quote data available (market may be closed)");
        Assumptions.assumeFalse(ohlc.isEmpty(), "Skipping: no OHLC data available (market may be closed)");
        assertTrue(ohlc.containsKey(key));
        Quote ohlcQuote = ohlc.get(key);
        assertNotNull(ohlcQuote);
        assertTrue(ohlcQuote.ltpPaisa() > 0L, "OHLC batch should expose a positive last price.");
    }
}
