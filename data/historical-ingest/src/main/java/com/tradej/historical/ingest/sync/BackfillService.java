package com.tradej.historical.ingest.sync;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.historical.ingest.canonical.ParquetWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Backfill service: identifies gaps in historical data and re-downloads
 * missing candles from the broker API, writing them to canonical parquet.
 *
 * <p>Uses {@link GapDetector} to find missing bar timestamps, fetches
 * the missing data via {@link MarketDataProvider}, and writes via
 * {@link ParquetWriteService}.
 */
public final class BackfillService {

    private static final Logger log = LoggerFactory.getLogger(BackfillService.class);

    private final GapDetector gapDetector;
    private final MarketDataProvider marketDataProvider;
    private final ParquetWriteService writer;

    public BackfillService(GapDetector gapDetector,
                            MarketDataProvider marketDataProvider,
                            ParquetWriteService writer) {
        this.gapDetector = gapDetector;
        this.marketDataProvider = marketDataProvider;
        this.writer = writer;
    }

    public BackfillResult backfill(String symbol, ExchangeSegment segment,
                                    String interval, LocalDate from, LocalDate to,
                                    Set<Long> actualTimestamps) {
        Set<Long> gaps = gapDetector.detectGaps(segment, interval, from, to, actualTimestamps);
        if (gaps.isEmpty()) {
            return new BackfillResult(symbol, interval, from, to, 0, 0, BackfillStatus.COMPLETE);
        }
        log.info("Detected {} gaps for {} {} {} from {} to {}",
                gaps.size(), symbol, segment, interval, from, to);
        try {
            InstrumentKey key = InstrumentKey.of(symbol, segment);
            List<Candle> candles = marketDataProvider.getCandles(
                    new CandleHistoryRequest(key, interval, from, to));

            List<Candle> gapCandles = candles.stream()
                    .filter(c -> gaps.contains(c.startTimeMs()))
                    .toList();

            int written = 0;
            if (!gapCandles.isEmpty()) {
                written = writer.writeBars(segment.name(), symbol, interval, gapCandles);
            }

            log.info("Backfilled {} bars for {} {} {} ({} gaps detected)",
                    written, symbol, segment, interval, gaps.size());
            long remaining = gaps.size() - written;
            return new BackfillResult(symbol, interval, from, to,
                    written, (int) remaining,
                    remaining == 0 ? BackfillStatus.COMPLETE : BackfillStatus.PARTIAL);
        } catch (Exception ex) {
            log.warn("Backfill failed for {} {} {}: {}", symbol, segment, interval, ex.getMessage());
            return new BackfillResult(symbol, interval, from, to, 0, gaps.size(), BackfillStatus.FAILED);
        }
    }

    public List<BackfillResult> backfillAll(List<String> symbols, ExchangeSegment segment,
                                             String interval, LocalDate from, LocalDate to) {
        List<BackfillResult> results = new ArrayList<>();
        for (String symbol : symbols) {
            results.add(backfill(symbol, segment, interval, from, to, Set.of()));
        }
        return results;
    }

    public record BackfillResult(
            String symbol,
            String interval,
            LocalDate from,
            LocalDate to,
            int barsRecovered,
            int gapsRemaining,
            BackfillStatus status
    ) {}

    public enum BackfillStatus {
        COMPLETE,
        PARTIAL,
        FAILED
    }
}
