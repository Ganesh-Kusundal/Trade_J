package com.tradej.scanner.fetch;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.Quote;
import com.tradej.scanner.model.ScanAsset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SnapshotFetcher {
    private static final Logger log = LoggerFactory.getLogger(SnapshotFetcher.class);

    private final MarketDataProvider marketDataProvider;

    public SnapshotFetcher(MarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }

    public FetchResult fetch(List<ScanAsset> assets, int batchSize) {
        if (assets.isEmpty()) {
            return new FetchResult(Map.of(), 0);
        }
        int effectiveBatch = batchSize > 0 ? batchSize : 50;
        Map<InstrumentKey, Quote> quotes = new HashMap<>();
        int partialFailures = 0;
        List<InstrumentKey> keys = assets.stream().map(ScanAsset::key).toList();
        for (int offset = 0; offset < keys.size(); offset += effectiveBatch) {
            int end = Math.min(offset + effectiveBatch, keys.size());
            List<InstrumentKey> chunk = keys.subList(offset, end);
            try {
                Map<InstrumentKey, Quote> batch = marketDataProvider.getOhlcBatch(chunk);
                for (InstrumentKey key : chunk) {
                    Quote quote = batch.get(key);
                    if (quote == null || quote.ltpPaisa() <= 0L) {
                        partialFailures++;
                        log.warn("Missing or invalid quote for {}:{}", key.symbol(), key.exchangeSegment());
                        continue;
                    }
                    quotes.put(key, quote);
                }
            } catch (RuntimeException ex) {
                partialFailures += chunk.size();
                log.warn("Batch quote fetch failed for chunk starting at {}: {}", offset, ex.getMessage());
            }
        }
        return new FetchResult(Map.copyOf(quotes), partialFailures);
    }

    public record FetchResult(
            Map<InstrumentKey, Quote> quotes,
            int partialFailureCount
    ) {
    }
}
