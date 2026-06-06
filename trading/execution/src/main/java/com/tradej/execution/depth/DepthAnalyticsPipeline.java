package com.tradej.execution.depth;

import com.tradej.broker.core.depth.OrderBook;
import com.tradej.broker.core.depth.OrderBookEngine;
import com.tradej.core.domain.event.DepthUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class DepthAnalyticsPipeline {

    private static final Logger log = LoggerFactory.getLogger(DepthAnalyticsPipeline.class);

    private final OrderBookEngine orderBookEngine;
    private final OrderBookImbalanceService imbalanceService;
    private final HeatmapRecorder heatmapRecorder;
    private final RestingOrderAnalyzer restingOrderAnalyzer;
    private final IcebergDetector icebergDetector;
    private final AbsorptionAnalyzer absorptionAnalyzer;

    private final CopyOnWriteArrayList<Consumer<Object>> consumers = new CopyOnWriteArrayList<>();
    private volatile int updateCount = 0;

    public DepthAnalyticsPipeline(
            OrderBookEngine orderBookEngine,
            OrderBookImbalanceService imbalanceService,
            HeatmapRecorder heatmapRecorder,
            RestingOrderAnalyzer restingOrderAnalyzer,
            IcebergDetector icebergDetector,
            AbsorptionAnalyzer absorptionAnalyzer
    ) {
        this.orderBookEngine = orderBookEngine;
        this.imbalanceService = imbalanceService;
        this.heatmapRecorder = heatmapRecorder;
        this.restingOrderAnalyzer = restingOrderAnalyzer;
        this.icebergDetector = icebergDetector;
        this.absorptionAnalyzer = absorptionAnalyzer;
    }

    public void onDepthUpdate(DepthUpdateEvent event) {
        OrderBook book = orderBookEngine.getBook(event.symbol(), event.segment());
        if (book == null) return;
        processBook(book);
    }

    public void processBook(OrderBook book) {
        updateCount++;

        DepthAnalyticsEvents.DepthImbalanceSnapshot imbalance = imbalanceService.compute(book);
        publish(imbalance);

        heatmapRecorder.record(book);

        if (updateCount % 5 == 0) {
            DepthAnalyticsEvents.SRLevelsUpdate srLevels = restingOrderAnalyzer.analyze(book);
            publish(srLevels);
        }

        List<DepthAnalyticsEvents.IcebergSignal> icebergs = icebergDetector.detect(book);
        for (var signal : icebergs) publish(signal);

        List<DepthAnalyticsEvents.AbsorptionSignal> absorptions = absorptionAnalyzer.analyze(book);
        for (var signal : absorptions) publish(signal);
    }

    public void addConsumer(Consumer<Object> consumer) { consumers.add(consumer); }
    public void removeConsumer(Consumer<Object> consumer) { consumers.remove(consumer); }
    public HeatmapRecorder heatmapRecorder() { return heatmapRecorder; }
    public int updateCount() { return updateCount; }

    private void publish(Object event) {
        for (Consumer<Object> consumer : consumers) {
            try { consumer.accept(event); }
            catch (Exception ex) { log.warn("Analytics consumer failed: {}", ex.getMessage()); }
        }
    }
}
