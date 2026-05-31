package com.tradej.historical.ingest.query;

import com.tradej.core.domain.market.CandleBucketPolicy;
import com.tradej.core.domain.market.CandleIntervalSpec;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.historical.ingest.universe.HistoricalEquityPaths;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
class ParquetHistoricalBarRepositoryComponentTest {

    @Test
    void resamplesRealParquetOneMinuteBarsToSessionAlignedFiveMinute() throws Exception {
        Path root = HistoricalEquityPaths.root(Path.of(HistoricalEquityPaths.DEFAULT_ROOT));
        assumeTrue(Files.exists(HistoricalEquityPaths.symbolsParquet(root)),
                "Parquet warehouse not present at " + root);

        try (ParquetHistoricalBarRepository repository = new ParquetHistoricalBarRepository(root)) {
            LocalDate scanDate = repository.latestAvailableTradingDay(30)
                    .orElseThrow(() -> new IllegalStateException("No trading day"));
            InstrumentKey sbin = new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ);

            List<Candle> oneMinute = repository.queryCandles(sbin, "1m", scanDate, scanDate);
            assumeTrue(oneMinute.size() >= 10, "Need at least 10 one-minute bars for SBIN on " + scanDate);

            List<Candle> fiveMinute = repository.queryCandles(sbin, "5m", scanDate, scanDate);

            assertFalse(fiveMinute.isEmpty());
            assertEquals(
                    CandleBucketPolicy.bucketStartMs(
                            oneMinute.getFirst().startTimeMs(), CandleIntervalSpec.parse("5m")),
                    fiveMinute.getFirst().startTimeMs()
            );
            assertTrue(fiveMinute.size() < oneMinute.size(), "5m bars should aggregate 1m bars");
        }
    }
}
