package com.tradej.historical.ingest.sync;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.historical.ingest.calendar.TradingCalendarStore;
import com.tradej.historical.ingest.canonical.ParquetWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;

public final class IncrementalSyncService {

    private static final Logger log = LoggerFactory.getLogger(IncrementalSyncService.class);

    private final MarketDataProvider marketDataProvider;
    private final InstrumentResolver instrumentResolver;
    private final TradingCalendarStore calendar;
    private final ParquetWriteService writer;

    public IncrementalSyncService(
            MarketDataProvider marketDataProvider,
            InstrumentResolver instrumentResolver,
            TradingCalendarStore calendar,
            ParquetWriteService writer) {
        this.marketDataProvider = marketDataProvider;
        this.instrumentResolver = instrumentResolver;
        this.calendar = calendar;
        this.writer = writer;
    }

    public SyncResult syncSymbol(String symbol, ExchangeSegment segment,
                                  String interval, LocalDate date) {
        if (!calendar.isTradingDay(segment, date)) {
            return new SyncResult(symbol, interval, date, 0, SyncStatus.SKIPPED_NON_TRADING);
        }
        try {
            InstrumentKey key = InstrumentKey.of(symbol, segment);
            List<Candle> candles = marketDataProvider.getCandles(
                    new CandleHistoryRequest(key, interval, date, date));
            if (candles.isEmpty()) {
                return new SyncResult(symbol, interval, date, 0, SyncStatus.NO_DATA);
            }
            int written = writer.writeBars(segment.name(), symbol, interval, candles);
            log.info("Synced {} bars for {} {} {} on {}", written,
                    symbol, segment, interval, date);
            return new SyncResult(symbol, interval, date, written, SyncStatus.SUCCESS);
        } catch (Exception ex) {
            log.warn("Sync failed for {} {} {}: {}", symbol, segment, interval, ex.getMessage());
            return new SyncResult(symbol, interval, date, 0, SyncStatus.FAILED);
        }
    }

    public SyncResult syncAll(List<String> symbols, ExchangeSegment segment,
                               String interval, LocalDate date) {
        int total = 0;
        int failed = 0;
        for (String symbol : symbols) {
            SyncResult result = syncSymbol(symbol, segment, interval, date);
            total += result.barsWritten();
            if (result.status() == SyncStatus.FAILED) {
                failed++;
            }
        }
        return new SyncResult(symbols.size() + " symbols", interval, date, total,
                failed > 0 ? SyncStatus.PARTIAL : SyncStatus.SUCCESS);
    }

    public List<String> availableSymbols(ExchangeSegment segment) {
        return instrumentResolver.allInstruments().stream()
                .filter(i -> i.exchangeSegment() == segment)
                .map(i -> i.canonicalSymbol())
                .distinct()
                .sorted()
                .toList();
    }

    public record SyncResult(
            String symbol,
            String interval,
            LocalDate date,
            int barsWritten,
            SyncStatus status
    ) {}

    public enum SyncStatus {
        SUCCESS,
        SKIPPED_NON_TRADING,
        NO_DATA,
        FAILED,
        PARTIAL
    }
}
