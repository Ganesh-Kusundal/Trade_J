package com.tradej.historical.ingest.canonical;

import com.tradej.core.domain.model.Candle;
import com.tradej.historical.ingest.resample.CandleResampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;

public final class MultiIntervalGenerator {

    private static final Logger log = LoggerFactory.getLogger(MultiIntervalGenerator.class);
    private static final String[] TARGET_INTERVALS = {"5m", "15m", "1d"};

    private final ParquetHistoricalDataStore dataStore;
    private final ParquetWriteService writer;

    public MultiIntervalGenerator(ParquetHistoricalDataStore dataStore, ParquetWriteService writer) {
        this.dataStore = dataStore;
        this.writer = writer;
    }

    public GenerateResult generateForSymbol(String symbol, String segment,
                                             LocalDate from, LocalDate to) {
        List<Candle> bars1m = dataStore.queryCandles(symbol, segment, "1m", from, to);
        if (bars1m.isEmpty()) {
            log.warn("No 1m data for {} {} from {} to {}", symbol, segment, from, to);
            return new GenerateResult(symbol, 0, 0);
        }

        int totalGenerated = 0;
        for (String interval : TARGET_INTERVALS) {
            List<Candle> resampled = CandleResampler.resample(bars1m, interval);
            if (!resampled.isEmpty()) {
                int written = writer.writeBars(segment, symbol, interval, resampled);
                totalGenerated += written;
                log.info("Generated {} bars of {} for {} {}", written, interval, symbol, segment);
            }
        }

        return new GenerateResult(symbol, bars1m.size(), totalGenerated);
    }

    public void generateForAll(List<String> symbols, String segment,
                                LocalDate from, LocalDate to) {
        int processed = 0;
        int totalBars = 0;
        for (String symbol : symbols) {
            try {
                GenerateResult result = generateForSymbol(symbol, segment, from, to);
                totalBars += result.derivedBarsWritten();
                processed++;
            } catch (Exception ex) {
                log.warn("Failed to generate intervals for {}: {}", symbol, ex.getMessage());
            }
            if (processed % 50 == 0) {
                log.info("Multi-interval generation: {}/{} symbols processed, {} derived bars",
                        processed, symbols.size(), totalBars);
            }
        }
        log.info("Multi-interval generation complete: {} symbols, {} derived bars", processed, totalBars);
    }

    public record GenerateResult(String symbol, int sourceBarsRead, int derivedBarsWritten) {}
}
