package com.tradej.execution.depth;

import com.tradej.broker.core.depth.OrderBook;
import com.tradej.broker.core.depth.OrderBookEngine;
import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class DepthAnalyticsTest {

    private OrderBook book;

    @BeforeEach
    void setUp() {
        book = new OrderBook("RELIANCE", ExchangeSegment.NSE_EQ);
        book.update(
                List.of(new DepthLevel(250000, 1000, 5), new DepthLevel(249500, 500, 3)),
                List.of(new DepthLevel(250100, 200, 2), new DepthLevel(250500, 300, 4))
        );
    }

    @Test
    void imbalanceServiceComputesSnapshot() {
        OrderBookImbalanceService service = new OrderBookImbalanceService();
        var snapshot = service.compute(book);
        assertEquals("RELIANCE", snapshot.symbol());
        assertNotNull(snapshot.trend());
    }

    @Test
    void imbalanceTrendStrongBid() {
        OrderBook bidHeavy = new OrderBook("NIFTY", ExchangeSegment.IDX_I);
        bidHeavy.update(
                List.of(new DepthLevel(24000_00, 5000, 10)),
                List.of(new DepthLevel(24001_00, 100, 1))
        );
        OrderBookImbalanceService service = new OrderBookImbalanceService();
        var snapshot = service.compute(bidHeavy);
        assertTrue(snapshot.topOfBookImbalance() > 0.3);
        assertEquals("STRONG_BID", snapshot.trend());
    }

    @Test
    void imbalanceTrendStrongAsk() {
        OrderBook askHeavy = new OrderBook("NIFTY", ExchangeSegment.IDX_I);
        askHeavy.update(
                List.of(new DepthLevel(24000_00, 100, 1)),
                List.of(new DepthLevel(24001_00, 5000, 10))
        );
        OrderBookImbalanceService service = new OrderBookImbalanceService();
        var snapshot = service.compute(askHeavy);
        assertTrue(snapshot.topOfBookImbalance() < -0.3);
        assertEquals("STRONG_ASK", snapshot.trend());
    }

    @Test
    void imbalanceTrendNeutral() {
        OrderBook balanced = new OrderBook("NIFTY", ExchangeSegment.IDX_I);
        balanced.update(
                List.of(new DepthLevel(24000_00, 1000, 5)),
                List.of(new DepthLevel(24001_00, 1000, 5))
        );
        OrderBookImbalanceService service = new OrderBookImbalanceService();
        var snapshot = service.compute(balanced);
        assertEquals("NEUTRAL", snapshot.trend());
    }

    @Test
    void heatmapRecorderRecordsAndReturnsWindow() {
        HeatmapRecorder recorder = new HeatmapRecorder();
        recorder.record(book);
        var chunk = recorder.getWindow("RELIANCE", "NSE_EQ");
        assertEquals("RELIANCE", chunk.symbol());
        assertFalse(chunk.priceBuckets().isEmpty());
    }

    @Test
    void heatmapRecorderHandlesEmptySymbol() {
        HeatmapRecorder recorder = new HeatmapRecorder();
        var chunk = recorder.getWindow("UNKNOWN", "NSE_EQ");
        assertTrue(chunk.priceBuckets().isEmpty());
    }

    @Test
    void restingOrderAnalyzerReturnsEmptyForNewBook() {
        RestingOrderAnalyzer analyzer = new RestingOrderAnalyzer();
        var result = analyzer.analyze(book);
        assertTrue(result.levels().isEmpty());
    }

    @Test
    void icebergDetectorReturnsEmptyForNewBook() {
        IcebergDetector detector = new IcebergDetector();
        var signals = detector.detect(book);
        assertTrue(signals.isEmpty());
    }

    @Test
    void absorptionAnalyzerReturnsEmptyForNewBook() {
        AbsorptionAnalyzer analyzer = new AbsorptionAnalyzer();
        var signals = analyzer.analyze(book);
        assertTrue(signals.isEmpty());
    }

    @Test
    void pipelinePublishesImbalance() {
        OrderBookEngine engine = new OrderBookEngine();
        var pipeline = createPipeline(engine);
        List<Object> received = new ArrayList<>();
        pipeline.addConsumer(received::add);

        pipeline.processBook(book);

        assertFalse(received.isEmpty());
        assertTrue(received.getFirst() instanceof DepthAnalyticsEvents.DepthImbalanceSnapshot);
    }

    @Test
    void pipelineRecordsHeatmap() {
        OrderBookEngine engine = new OrderBookEngine();
        var pipeline = createPipeline(engine);
        pipeline.processBook(book);
        var chunk = pipeline.heatmapRecorder().getWindow("RELIANCE", "NSE_EQ");
        assertFalse(chunk.priceBuckets().isEmpty());
    }

    private DepthAnalyticsPipeline createPipeline(OrderBookEngine engine) {
        return new DepthAnalyticsPipeline(
                engine,
                new OrderBookImbalanceService(),
                new HeatmapRecorder(),
                new RestingOrderAnalyzer(),
                new IcebergDetector(),
                new AbsorptionAnalyzer()
        );
    }
}
