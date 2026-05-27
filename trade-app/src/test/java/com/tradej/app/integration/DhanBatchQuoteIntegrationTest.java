package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

        InstrumentKey key = new InstrumentKey(
                LiveDhanTestSupport.value("DHAN_RUNTIME_SYMBOL", "dhan.runtimeSymbol", "NIFTY"),
                ExchangeSegment.valueOf(LiveDhanTestSupport.value("DHAN_RUNTIME_SEGMENT", "dhan.runtimeSegment", "IDX_I"))
        );
        List<InstrumentKey> keys = List.of(key);
        Map<InstrumentKey, Long> ltp = brokerConnection.marketData().getLtpBatch(keys);
        Map<InstrumentKey, ?> quote = brokerConnection.marketData().getQuoteBatch(keys);

        assertFalse(ltp.isEmpty());
        assertTrue(ltp.getOrDefault(key, 0L) > 0L);
        assertFalse(quote.isEmpty());
    }
}
