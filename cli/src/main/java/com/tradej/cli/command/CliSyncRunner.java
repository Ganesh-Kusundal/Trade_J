package com.tradej.cli.command;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.historical.ingest.canonical.ParquetWriteService;
import com.tradej.historical.ingest.canonical.CanonicalBarWriter;
import com.tradej.historical.ingest.canonical.CanonicalPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Standalone sync runner — fetches missing equity data from Dhan and writes to parquet.
 * Uses 90-day bulk windows for efficient API usage.
 */
public final class CliSyncRunner {

    private static final int MAX_DAYS_PER_CALL = 90;

    public static void run(MarketDataProvider marketData, InstrumentResolver resolver,
                           Path dataRoot, LocalDate from, LocalDate to) {
        ExchangeSegment segment = ExchangeSegment.NSE_EQ;
        Path barsRoot = dataRoot.resolve("bars");

        List<String> symbols;
        try {
            symbols = listSymbolsFromDisk(barsRoot);
        } catch (Exception ex) {
            System.out.println("ERROR: Failed to list symbols from disk: " + ex.getMessage());
            return;
        }

        if (symbols.isEmpty()) {
            System.out.println("No symbols found in " + barsRoot);
            return;
        }

        System.out.println("=== Bulk Sync ===");
        System.out.println("  Symbols:  " + symbols.size());
        System.out.println("  Range:    " + from + " → " + to);
        System.out.println("  Windows:  " + countWindows(from, to, MAX_DAYS_PER_CALL));
        System.out.println("  API calls: " + (symbols.size() * countWindows(from, to, MAX_DAYS_PER_CALL)));
        System.out.println();

        ParquetWriteService writer = new CanonicalBarWriter(dataRoot);
        int totalBars = 0;
        int totalCalls = 0;
        int totalFailed = 0;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < symbols.size(); i++) {
            String symbol = symbols.get(i);
            try {
                InstrumentKey key = new InstrumentKey(symbol, segment);
                List<Candle> candles = marketData.getCandles(
                        new CandleHistoryRequest(key, "1m", from, to));
                if (!candles.isEmpty()) {
                    int written = writer.writeBars(segment.name(), symbol, "1m", candles);
                    totalBars += written;
                }
                totalCalls++;
            } catch (Exception ex) {
                totalFailed++;
                if (totalFailed <= 5) {
                    System.out.println("  WARN: " + symbol + " failed: " + ex.getMessage());
                }
            }

            if ((i + 1) % 25 == 0 || i == symbols.size() - 1) {
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                double rate = elapsed > 0 ? (double) (i + 1) / elapsed : 0;
                int remaining = symbols.size() - i - 1;
                int etaSec = rate > 0 ? (int) (remaining / rate) : 0;
                System.out.printf("  Progress: %d/%d symbols | %d bars | %d failed | %ds elapsed | ETA %ds%n",
                        i + 1, symbols.size(), totalBars, totalFailed, elapsed, etaSec);
            }
        }

        long totalSec = (System.currentTimeMillis() - startTime) / 1000;
        System.out.println();
        System.out.println("=== Sync Complete ===");
        System.out.println("  Symbols synced: " + symbols.size());
        System.out.println("  API calls:      " + totalCalls);
        System.out.println("  Bars written:   " + totalBars);
        System.out.println("  Failed:         " + totalFailed);
        System.out.println("  Time:           " + totalSec + "s");
    }

    private static List<String> listSymbolsFromDisk(Path barsRoot) throws Exception {
        Path intervalDir = barsRoot.resolve("interval=1m");
        if (!Files.isDirectory(intervalDir)) {
            return List.of();
        }
        try (var stream = Files.list(intervalDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith("symbol="))
                    .map(name -> name.substring("symbol=".length()))
                    .sorted()
                    .toList();
        }
    }

    private static int countWindows(LocalDate from, LocalDate to, int maxDays) {
        int count = 0;
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            cursor = cursor.plusDays(maxDays);
            count++;
        }
        return count;
    }
}
