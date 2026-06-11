package com.tradej.app.api;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.core.depth.OrderBook;
import com.tradej.broker.core.depth.OrderBookEngine;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.execution.depth.DepthAnalyticsEvents;
import com.tradej.execution.depth.DepthAnalyticsPipeline;
import com.tradej.core.domain.value.ExchangeSegment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market/depth")
public class DepthAnalyticsController {

    private final OrderBookEngine orderBookEngine;
    private final DepthAnalyticsPipeline analyticsPipeline;
    private final MarketDataProvider marketDataProvider;

    public DepthAnalyticsController(
            OrderBookEngine orderBookEngine,
            DepthAnalyticsPipeline analyticsPipeline,
            @Autowired(required = false) MarketDataProvider marketDataProvider
    ) {
        this.orderBookEngine = orderBookEngine;
        this.analyticsPipeline = analyticsPipeline;
        this.marketDataProvider = marketDataProvider;
    }

    @GetMapping("/{symbol}")
    public Object getSnapshot(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "NSE_EQ") String segment,
            @RequestParam(defaultValue = "20") int levels) {
        OrderBook book = orderBookEngine.getBook(symbol, ExchangeSegment.valueOf(segment));
        if (book != null) {
            OrderBook.OrderBookSnapshot snap = book.toSnapshot(levels);
            if (!snap.bids().isEmpty() || !snap.asks().isEmpty()) {
                return snap;
            }
        }
        if (marketDataProvider != null) {
            InstrumentKey key = InstrumentKey.of(symbol, ExchangeSegment.valueOf(segment));
            MarketDepth depth = marketDataProvider.getDepth(key);
            return depth;
        }
        return new OrderBook.OrderBookSnapshot(symbol, segment, List.of(), List.of(),
                0, 0, 0, 0, 0.0, System.currentTimeMillis());
    }

    @GetMapping("/{symbol}/heatmap")
    public DepthAnalyticsEvents.HeatmapChunk getHeatmap(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "NSE_EQ") String segment) {
        return analyticsPipeline.heatmapRecorder().getWindow(symbol, segment);
    }
}
