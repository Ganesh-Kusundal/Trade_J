package com.tradej.app.integration;

import com.tradej.historical.service.BrokerHistoricalQueryService;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("integration")
@Tag("broker-rest")
class HistoricalRangeIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void fetchesChunkedHistoricalRange() throws Exception {
        brokerConnection = DhanBrokerConnection.create(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-histrange-cache"), false);

        BrokerHistoricalQueryService service = new BrokerHistoricalQueryService(brokerConnection.marketData());
        InstrumentKey key = new InstrumentKey(
                LiveDhanTestSupport.value("DHAN_HISTORICAL_SYMBOL", "dhan.historicalSymbol", "NIFTY"),
                ExchangeSegment.valueOf(LiveDhanTestSupport.value("DHAN_HISTORICAL_SEGMENT", "dhan.historicalSegment", "IDX_I"))
        );
        var candles = service.getCandlesChunked(new CandleHistoryRequest(
                key,
                "1d",
                LocalDate.now().minusDays(30),
                LocalDate.now().minusDays(1)
        ));
        assertFalse(candles.isEmpty());
    }
}
