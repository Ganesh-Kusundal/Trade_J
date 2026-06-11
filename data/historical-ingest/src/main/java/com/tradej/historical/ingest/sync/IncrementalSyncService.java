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
import java.util.ArrayList;
import java.util.List;

public final class IncrementalSyncService {

    private static final Logger log = LoggerFactory.getLogger(IncrementalSyncService.class);
    private static final int MAX_DAYS_PER_CALL = 90;

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
            InstrumentKey key = new InstrumentKey(symbol, segment);
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

    public SyncResult syncSymbolRange(String symbol, ExchangeSegment segment,
                                       String interval, LocalDate from, LocalDate to) {
        try {
            InstrumentKey key = new InstrumentKey(symbol, segment);
            List<Candle> candles = marketDataProvider.getCandles(
                    new CandleHistoryRequest(key, interval, from, to));
            if (candles.isEmpty()) {
                return new SyncResult(symbol, interval, from, 0, SyncStatus.NO_DATA);
            }
            int written = writer.writeBars(segment.name(), symbol, interval, candles);
            log.info("Synced {} bars for {} {} {} from {} to {} ({} days in 1 call)",
                    written, symbol, segment, interval, from, to,
                    to.toEpochDay() - from.toEpochDay() + 1);
            return new SyncResult(symbol, interval, from, written, SyncStatus.SUCCESS);
        } catch (Exception ex) {
            log.warn("Sync range failed for {} {} {} from {} to {}: {}",
                    symbol, segment, interval, from, to, ex.getMessage());
            return new SyncResult(symbol, interval, from, 0, SyncStatus.FAILED);
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

    public BulkSyncResult syncAllRanges(List<String> symbols, ExchangeSegment segment,
                                         String interval, List<LocalDate> gapDates) {
        if (gapDates.isEmpty()) {
            return new BulkSyncResult(0, 0, 0, 0);
        }

        LocalDate minDate = gapDates.stream().min(LocalDate::compareTo).orElse(gapDates.getFirst());
        LocalDate maxDate = gapDates.stream().max(LocalDate::compareTo).orElse(gapDates.getFirst());

        List<DateWindow> windows = splitIntoWindows(minDate, maxDate, MAX_DAYS_PER_CALL);

        int totalBars = 0;
        int totalSymbols = 0;
        int totalCalls = 0;
        int totalFailed = 0;

        for (String symbol : symbols) {
            for (DateWindow window : windows) {
                SyncResult result = syncSymbolRange(symbol, segment, interval,
                        window.from(), window.to());
                totalBars += result.barsWritten();
                totalCalls++;
                if (result.status() == SyncStatus.FAILED) {
                    totalFailed++;
                }
            }
            totalSymbols++;
            if (totalSymbols % 50 == 0) {
                log.info("Bulk sync progress: {}/{} symbols, {} bars, {} calls ({} failed)",
                        totalSymbols, symbols.size(), totalBars, totalCalls, totalFailed);
            }
        }

        log.info("Bulk sync complete: {} symbols × {} windows = {} calls, {} bars written ({} failed)",
                symbols.size(), windows.size(), totalCalls, totalBars, totalFailed);
        return new BulkSyncResult(totalSymbols, totalBars, totalCalls, totalFailed);
    }

    static List<DateWindow> splitIntoWindows(LocalDate from, LocalDate to, int maxDays) {
        List<DateWindow> windows = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            LocalDate windowEnd = cursor.plusDays(maxDays - 1L);
            if (windowEnd.isAfter(to)) {
                windowEnd = to;
            }
            windows.add(new DateWindow(cursor, windowEnd));
            cursor = windowEnd.plusDays(1L);
        }
        return windows;
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

    public record BulkSyncResult(
            int symbolsSynced,
            int totalBarsWritten,
            int apiCalls,
            int failedCalls
    ) {}

    public record DateWindow(LocalDate from, LocalDate to) {}

    public enum SyncStatus {
        SUCCESS,
        SKIPPED_NON_TRADING,
        NO_DATA,
        FAILED,
        PARTIAL
    }
}
