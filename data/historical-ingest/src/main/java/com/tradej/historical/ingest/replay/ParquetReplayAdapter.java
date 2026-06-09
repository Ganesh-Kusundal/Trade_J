package com.tradej.historical.ingest.replay;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.historical.ingest.canonical.HistoricalDataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Replays historical parquet candle data as MarketTickEvent and CandleClosed
 * events through the EventBus. Replaces the DuckDB-based HistoricalRangeService
 * for replay and backtesting.
 *
 * <p>Converts each parquet candle into:
 * <ol>
 *   <li>A {@link MarketTickEvent} at bar open (for real-time feed simulation)</li>
 *   <li>A {@link CandleClosed} event at bar close (for strategy signal generation)</li>
 * </ol>
 */
public final class ParquetReplayAdapter {

    private static final Logger log = LoggerFactory.getLogger(ParquetReplayAdapter.class);

    private final HistoricalDataStore dataStore;
    private final AtomicLong sequenceCounter = new AtomicLong();

    public ParquetReplayAdapter(HistoricalDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public int replayCandles(String symbol, String segment, String interval,
                              LocalDate from, LocalDate to, EventBus eventBus) {
        List<Candle> candles = dataStore.queryCandles(symbol, segment, interval, from, to);
        if (candles.isEmpty()) {
            log.warn("No candles found for {} {} {} from {} to {}", symbol, segment, interval, from, to);
            return 0;
        }
        ExchangeSegment exchangeSegment = ExchangeSegment.valueOf(segment);
        int published = 0;
        for (Candle candle : candles) {
            long seqId = sequenceCounter.incrementAndGet();
            MarketTickEvent tick = new MarketTickEvent(
                    EventMetadata.root(),
                    seqId,
                    candle.symbol(),
                    exchangeSegment,
                    FeedMode.TICKER,
                    candle.closePaisa(),
                    0L,
                    candle.volume(),
                    candle.startTimeMs(),
                    Optional.empty(),
                    candle.oi(),
                    candle.oi()
            );
            eventBus.publish(tick);

            CandleClosed closed = new CandleClosed(EventMetadata.root(), candle);
            eventBus.publish(closed);
            published++;
        }
        log.info("Replayed {} candles for {} {} {} from {} to {}",
                published, symbol, segment, interval, from, to);
        return published;
    }

    public int replayCandlesAsTicks(String symbol, String segment,
                                     LocalDate from, LocalDate to, EventBus eventBus) {
        return replayCandles(symbol, segment, "1m", from, to, eventBus);
    }
}
