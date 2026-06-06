package com.tradej.app.api;

import com.tradej.broker.core.depth.OrderBook;
import com.tradej.broker.core.depth.OrderBookEngine;
import com.tradej.execution.depth.DepthAnalyticsEvents;
import com.tradej.execution.depth.DepthAnalyticsPipeline;
import com.tradej.core.domain.value.ExchangeSegment;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market/depth")
public class DepthAnalyticsController {

    private final OrderBookEngine orderBookEngine;
    private final DepthAnalyticsPipeline analyticsPipeline;

    public DepthAnalyticsController(OrderBookEngine orderBookEngine, DepthAnalyticsPipeline analyticsPipeline) {
        this.orderBookEngine = orderBookEngine;
        this.analyticsPipeline = analyticsPipeline;
    }

    @GetMapping("/{symbol}")
    public OrderBook.OrderBookSnapshot getSnapshot(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "NSE_EQ") String segment,
            @RequestParam(defaultValue = "20") int levels) {
        OrderBook book = orderBookEngine.getBook(symbol, ExchangeSegment.valueOf(segment));
        if (book == null) {
            return new OrderBook.OrderBookSnapshot(symbol, segment, List.of(), List.of(),
                    0, 0, 0, 0, 0.0, System.currentTimeMillis());
        }
        return book.toSnapshot(levels);
    }

    @GetMapping("/{symbol}/heatmap")
    public DepthAnalyticsEvents.HeatmapChunk getHeatmap(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "NSE_EQ") String segment) {
        return analyticsPipeline.heatmapRecorder().getWindow(symbol, segment);
    }
}
