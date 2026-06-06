package com.tradej.execution.depth;

import com.tradej.broker.core.depth.OrderBook;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class HeatmapRecorder {

    private static final int MAX_BUCKETS = 300;
    private static final long PRICE_BUCKET_SIZE_PAISA = 5_000L;

    private final ConcurrentHashMap<String, RingBuffer> buffers = new ConcurrentHashMap<>();

    public void record(OrderBook book) {
        RingBuffer buffer = buffers.computeIfAbsent(book.symbol(), k -> new RingBuffer(MAX_BUCKETS));
        long midPrice = book.midPricePaisa();
        long priceBucket = (midPrice / PRICE_BUCKET_SIZE_PAISA) * PRICE_BUCKET_SIZE_PAISA;
        buffer.add(new DepthAnalyticsEvents.HeatmapChunk.PriceBucket(
                priceBucket, book.totalBidVolume(), book.totalAskVolume()), book.lastUpdateMs());
    }

    public DepthAnalyticsEvents.HeatmapChunk getWindow(String symbol, String segment) {
        RingBuffer buffer = buffers.get(symbol);
        if (buffer == null) {
            return new DepthAnalyticsEvents.HeatmapChunk(symbol, segment, List.of(), System.currentTimeMillis());
        }
        return new DepthAnalyticsEvents.HeatmapChunk(symbol, segment, buffer.snapshot(), buffer.windowStartMs());
    }

    private static final class RingBuffer {
        private final List<DepthAnalyticsEvents.HeatmapChunk.PriceBucket> buckets;
        private final int maxSize;
        private volatile long windowStartMs;

        RingBuffer(int maxSize) {
            this.maxSize = maxSize;
            this.buckets = new ArrayList<>();
            this.windowStartMs = System.currentTimeMillis();
        }

        synchronized void add(DepthAnalyticsEvents.HeatmapChunk.PriceBucket bucket, long timestampMs) {
            buckets.add(bucket);
            while (buckets.size() > maxSize) {
                buckets.removeFirst();
            }
            windowStartMs = timestampMs - (buckets.size() * 1_000L);
        }

        synchronized List<DepthAnalyticsEvents.HeatmapChunk.PriceBucket> snapshot() {
            return List.copyOf(buckets);
        }

        long windowStartMs() { return windowStartMs; }
    }
}
