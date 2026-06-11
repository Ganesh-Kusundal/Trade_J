package com.tradej.app.admin;

import com.tradej.app.service.AdminApplicationService;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.runtime.RuntimeBusHolder;
import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.disruptor.DisruptorBusMetrics;
import com.tradej.replay.engine.ReplayOrchestrator;
import com.tradej.execution.reconcile.OrderReconciler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.hotpath.MarketDataPipeline;
import com.tradej.hotpath.OrderPipeline;
import com.tradej.persistence.replay.HistoricalRangeService;
import com.tradej.app.config.WebConfiguration.RateLimitFilter;
import com.tradej.strategy.service.GraphStrategySandbox;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final AdminApplicationService adminService;
    private final OrderReconciler orderReconciler;
    private final EventBus eventBus;
    private final DisruptorBusMetrics disruptorBusMetrics;
    private final RuntimeHealthState runtimeHealthState;
    private final RateLimitFilter rateLimitFilter;
    private final ExecutionHandler executionHandler;
    private final MarketDataPipeline marketDataPipeline;
    private final OrderPipeline orderPipeline;
    private final GraphStrategySandbox graphStrategySandbox;
    private final HistoricalRangeService historicalRangeService;
    private final RuntimeModeHolder runtimeModeHolder;
    private final RuntimeBusHolder runtimeBusHolder;
    private final ReplayOrchestrator replayOrchestrator;

    public AdminController(
            AdminApplicationService adminService,
            OrderReconciler orderReconciler,
            EventBus eventBus,
            DisruptorBusMetrics disruptorBusMetrics,
            RuntimeHealthState runtimeHealthState,
            RateLimitFilter rateLimitFilter,
            ExecutionHandler executionHandler,
            MarketDataPipeline marketDataPipeline,
            OrderPipeline orderPipeline,
            GraphStrategySandbox graphStrategySandbox,
            @Qualifier("localHistoricalRangeService") HistoricalRangeService historicalRangeService,
            RuntimeModeHolder runtimeModeHolder,
            RuntimeBusHolder runtimeBusHolder,
            ReplayOrchestrator replayOrchestrator
    ) {
        this.adminService = adminService;
        this.orderReconciler = orderReconciler;
        this.eventBus = eventBus;
        this.disruptorBusMetrics = disruptorBusMetrics;
        this.runtimeHealthState = runtimeHealthState;
        this.rateLimitFilter = rateLimitFilter;
        this.executionHandler = executionHandler;
        this.marketDataPipeline = marketDataPipeline;
        this.orderPipeline = orderPipeline;
        this.graphStrategySandbox = graphStrategySandbox;
        this.historicalRangeService = historicalRangeService;
        this.runtimeModeHolder = runtimeModeHolder;
        this.runtimeBusHolder = runtimeBusHolder;
        this.replayOrchestrator = replayOrchestrator;
    }

    private Optional<ResponseEntity<Map<String, Object>>> rejectIfLiveReplay() {
        if (runtimeModeHolder.mode() == RuntimeMode.LIVE) {
            return Optional.of(ResponseEntity.status(409).body(Map.of(
                    "error", "Historical replay is blocked in LIVE mode",
                    "hint", "Set trade.runtime.mode=REPLAY before invoking replay endpoints"
            )));
        }
        return Optional.empty();
    }

    @GetMapping("/runtime")
    ResponseEntity<Map<String, Object>> runtime() {
        return ResponseEntity.ok(adminService.getRuntimeStatus(
                runtimeHealthState.catalogLoaded(),
                runtimeHealthState.catalogSize(),
                runtimeHealthState.brokerPreflightPassed(),
                runtimeHealthState.startupCompleted(),
                runtimeModeHolder.mode(),
                runtimeBusHolder.mode()
        ));
    }

    @PostMapping("/risk/kill-switch/{enabled}")
    ResponseEntity<Map<String, Object>> setKillSwitch(@PathVariable boolean enabled) {
        return ResponseEntity.ok(adminService.setKillSwitch(enabled));
    }

    @GetMapping("/pipeline")
    ResponseEntity<Map<String, Object>> pipeline() {
        Map<String, Object> result = new LinkedHashMap<>();

        var dis = disruptorBusMetrics;
        result.put("shardCount", dis.shardCount());
        int ringBufferSize = dis.ringBufferSize();
        result.put("eventBusMode", runtimeBusHolder.mode().name());
        result.put("ringBufferRemainingCapacity", dis.ringBufferRemainingCapacity());
        result.put("ringBufferSize", ringBufferSize);
        result.put("ringBufferUtilization",
                ringBufferSize > 0
                        ? (double) (ringBufferSize - dis.ringBufferRemainingCapacity()) / ringBufferSize * 100.0
                        : 0.0);
        result.put("dispatchQueueDepth", dis.dispatchQueueDepth());
        result.put("dispatchDroppedEventCount", dis.dispatchDroppedEventCount());
        result.put("subscriberCount", dis.subscriberCount());
        result.put("started", dis.isStarted());

        result.put("executionQueueDepth", executionHandler.queueDepth());
        result.put("executionQueueRemainingCapacity", executionHandler.queueRemainingCapacity());
        result.put("executionQueueUtilization",
                (double) (executionHandler.queueDepth())
                        / (executionHandler.queueDepth() + executionHandler.queueRemainingCapacity()) * 100.0);

        result.put("totalTicksProcessed", marketDataPipeline.totalTicksProcessed());
        result.put("tickRate", Math.round(marketDataPipeline.tickRate() * 100.0) / 100.0);
        result.put("lastTickTimestampMs", marketDataPipeline.lastTickTimestampMs());

        result.put("totalOrdersAccepted", orderPipeline.totalOrdersAccepted());
        result.put("orderRate", Math.round(orderPipeline.orderRate() * 100.0) / 100.0);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/strategies")
    ResponseEntity<Map<String, Object>> strategies() {
        List<String> plugins = graphStrategySandbox.pluginNames();
        return ResponseEntity.ok(Map.of(
                "plugins", plugins,
                "pluginCount", plugins.size()
        ));
    }

    @GetMapping("/summary")
    ResponseEntity<Map<String, Object>> summary() {
        Map<String, Object> result = new LinkedHashMap<>();

        // Runtime state
        result.put("startupCompleted", runtimeHealthState.startupCompleted());
        result.put("brokerPreflightPassed", runtimeHealthState.brokerPreflightPassed());
        result.put("catalogLoaded", runtimeHealthState.catalogLoaded());
        result.put("catalogSize", runtimeHealthState.catalogSize());

        // Broker
        Map<String, Object> brokerStatus = adminService.getRuntimeStatus(false, 0, false, false, runtimeModeHolder.mode(), runtimeBusHolder.mode());
        result.put("websocketConnected", brokerStatus.get("websocketConnected"));
        result.put("subscriptions", brokerStatus.get("subscriptions"));
        result.put("circuitBreakerOpen", brokerStatus.get("circuitBreakerOpen"));

        // Pipeline
        var dis = disruptorBusMetrics;
        int ringBufferSize = dis.ringBufferSize();
        result.put("shardCount", dis.shardCount());
        result.put("runtimeBus", runtimeBusHolder.mode().name());
        result.put("ringBufferUtilizationPct",
                ringBufferSize > 0
                        ? Math.round((double) (ringBufferSize - dis.ringBufferRemainingCapacity()) / ringBufferSize * 1000.0) / 10.0
                        : 0.0);
        result.put("dispatchQueueDepth", dis.dispatchQueueDepth());
        result.put("dispatchDroppedEvents", dis.dispatchDroppedEventCount());
        result.put("executionQueueDepth", executionHandler.queueDepth());

        // Market data
        result.put("totalTicks", marketDataPipeline.totalTicksProcessed());
        result.put("tickRate", Math.round(marketDataPipeline.tickRate() * 100.0) / 100.0);

        // Orders
        result.put("totalOrders", orderPipeline.totalOrdersAccepted());
        result.put("orderRate", Math.round(orderPipeline.orderRate() * 100.0) / 100.0);

        // Strategies
        result.put("strategyPlugins", graphStrategySandbox.pluginNames());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/rate-limit")
    ResponseEntity<Map<String, Object>> rateLimitMetrics() {
        return ResponseEntity.ok(rateLimitFilter.getMetrics());
    }

    @PostMapping("/reconcile")
    ResponseEntity<Map<String, Object>> reconcile(@org.springframework.web.bind.annotation.RequestBody(required = false) Map<String, Long> expectedNetPositions) {
        if (expectedNetPositions == null || expectedNetPositions.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "rejected",
                    "reason", "explicit expectedNetPositions payload is required"
            ));
        }
        orderReconciler.reconcile(expectedNetPositions, eventBus::publish);
        return ResponseEntity.ok(Map.of("status", "triggered"));
    }

    // ── Historical / Replay endpoints ──

    @GetMapping("/historical/candles")
    ResponseEntity<Map<String, Object>> historicalCandles(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "5m") String interval,
            @RequestParam long from,
            @RequestParam long to,
            @RequestParam(defaultValue = "1000") int limit
    ) {
        var candles = historicalRangeService.queryCandles(symbol, interval, from, to, limit);
        return ResponseEntity.ok(Map.of(
                "symbol", symbol,
                "interval", interval,
                "from", from,
                "to", to,
                "count", candles.size(),
                "candles", candles.stream().map(c -> Map.of(
                        "startTimeMs", c.startTimeMs(),
                        "endTimeMs", c.endTimeMs(),
                        "openPaisa", c.openPaisa(),
                        "highPaisa", c.highPaisa(),
                        "lowPaisa", c.lowPaisa(),
                        "closePaisa", c.closePaisa(),
                        "volume", c.volume()
                )).toList()
        ));
    }

    @GetMapping("/historical/ticks")
    ResponseEntity<Map<String, Object>> historicalTicks(
            @RequestParam String symbol,
            @RequestParam long from,
            @RequestParam long to,
            @RequestParam(defaultValue = "5000") int limit
    ) {
        var ticks = historicalRangeService.queryTicks(symbol, from, to, limit);
        return ResponseEntity.ok(Map.of(
                "symbol", symbol,
                "from", from,
                "to", to,
                "count", ticks.size(),
                "ticks", ticks.stream().map(t -> Map.of(
                        "exchangeTimestampMs", t.exchangeTimestampEpochMs(),
                        "ltpPaisa", t.ltpPaisa(),
                        "lastTradeQuantity", t.lastTradeQuantity(),
                        "cumulativeVolume", t.cumulativeVolume(),
                        "interval", ""
                )).toList()
        ));
    }

    @GetMapping("/historical/orders")
    ResponseEntity<Map<String, Object>> historicalOrders(
            @RequestParam(defaultValue = "") String symbol,
            @RequestParam long from,
            @RequestParam long to,
            @RequestParam(defaultValue = "500") int limit
    ) {
        var orders = historicalRangeService.queryOrders(
                symbol.isBlank() ? null : symbol, from, to, limit);
        return ResponseEntity.ok(Map.of(
                "symbol", symbol.isBlank() ? "*" : symbol,
                "from", from,
                "to", to,
                "count", orders.size(),
                "orders", orders.stream().map(o -> Map.of(
                        "eventId", o.eventId(),
                        "orderId", o.orderId(),
                        "correlationId", o.correlationId(),
                        "symbol", o.symbol(),
                        "status", o.status(),
                        "quantity", o.quantity(),
                        "pricePaisa", o.pricePaisa()
                )).toList()
        ));
    }

    @GetMapping("/historical/fills")
    ResponseEntity<Map<String, Object>> historicalFills(
            @RequestParam(defaultValue = "") String symbol,
            @RequestParam long from,
            @RequestParam long to,
            @RequestParam(defaultValue = "500") int limit
    ) {
        var fills = historicalRangeService.queryFills(
                symbol.isBlank() ? null : symbol, from, to, limit);
        return ResponseEntity.ok(Map.of(
                "symbol", symbol.isBlank() ? "*" : symbol,
                "from", from,
                "to", to,
                "count", fills.size(),
                "fills", fills.stream().map(f -> Map.of(
                        "eventId", f.eventId(),
                        "orderId", f.orderId(),
                        "tradeId", f.tradeId(),
                        "symbol", f.symbol(),
                        "quantity", f.quantity(),
                        "pricePaisa", f.pricePaisa()
                )).toList()
        ));
    }

    @GetMapping("/historical/fill-events")
    ResponseEntity<Map<String, Object>> historicalFillEvents(
            @RequestParam(defaultValue = "") String symbol,
            @RequestParam long from,
            @RequestParam long to,
            @RequestParam(defaultValue = "500") int limit
    ) {
        var events = historicalRangeService.queryFillEvents(
                symbol.isBlank() ? null : symbol, from, to, limit);
        return ResponseEntity.ok(Map.of(
                "symbol", symbol.isBlank() ? "*" : symbol,
                "from", from,
                "to", to,
                "count", events.size(),
                "fillEvents", events.stream().map(e -> Map.of(
                        "eventId", e.eventId(),
                        "eventType", e.eventType(),
                        "orderId", e.orderId(),
                        "correlationId", e.correlationId(),
                        "symbol", e.symbol(),
                        "quantity", e.quantity(),
                        "pricePaisa", e.pricePaisa(),
                        "fillCount", e.fillCount()
                )).toList()
        ));
    }

    @GetMapping("/historical/stats")
    ResponseEntity<Map<String, Object>> historicalStats(
            @RequestParam String symbol,
            @RequestParam long from,
            @RequestParam long to
    ) {
        var stats = historicalRangeService.rangeStats(symbol, from, to);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("symbol", stats.symbol());
        body.put("from", stats.fromMs());
        body.put("to", stats.toMs());
        body.put("tickCount", stats.tickCount());
        body.put("candleCount", stats.candleCount());
        body.put("orderCount", stats.orderCount());
        body.put("fillCount", stats.fillCount());
        body.put("fillEventCount", stats.fillEventCount());
        body.put("firstTickMs", stats.firstTickMs());
        body.put("lastTickMs", stats.lastTickMs());
        body.put("firstCandleMs", stats.firstCandleMs());
        body.put("lastCandleMs", stats.lastCandleMs());
        body.put("hasData", stats.hasData());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/historical/replay/ticks")
    ResponseEntity<Map<String, Object>> replayTicks(
            @RequestParam String symbol,
            @RequestParam long from,
            @RequestParam long to,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50000") int batchSize
    ) {
        var replayCheck = rejectIfLiveReplay();
        if (replayCheck.isPresent()) {
            return replayCheck.get();
        }
        var result = replayOrchestrator.replayTicks(symbol, from, to, eventBus, offset, batchSize);
        return ResponseEntity.ok(Map.of(
                "mode", "ticks",
                "symbol", symbol,
                "from", from,
                "to", to,
                "offset", offset,
                "batchSize", batchSize,
                "totalRead", result.totalRead(),
                "replayed", result.replayed(),
                "failed", result.failed(),
                "complete", result.isComplete()
        ));
    }

    @PostMapping("/historical/replay/candles")
    ResponseEntity<Map<String, Object>> replayCandles(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "5m") String interval,
            @RequestParam long from,
            @RequestParam long to
    ) {
        var replayCheck = rejectIfLiveReplay();
        if (replayCheck.isPresent()) {
            return replayCheck.get();
        }
        var result = replayOrchestrator.replayCandles(symbol, interval, from, to, eventBus);
        return ResponseEntity.ok(Map.of(
                "mode", "candles",
                "symbol", symbol,
                "interval", interval,
                "from", from,
                "to", to,
                "totalRead", result.totalRead(),
                "replayed", result.replayed(),
                "failed", result.failed(),
                "complete", result.isComplete()
        ));
    }

    @PostMapping("/historical/replay/fills")
    ResponseEntity<Map<String, Object>> replayFillEvents(
            @RequestParam(defaultValue = "") String symbol,
            @RequestParam long from,
            @RequestParam long to
    ) {
        var replayCheck = rejectIfLiveReplay();
        if (replayCheck.isPresent()) {
            return replayCheck.get();
        }
        var result = replayOrchestrator.replayFillEvents(
                symbol.isBlank() ? null : symbol, from, to, eventBus);
        return ResponseEntity.ok(Map.of(
                "mode", "fill-events",
                "symbol", symbol.isBlank() ? "*" : symbol,
                "from", from,
                "to", to,
                "totalRead", result.totalRead(),
                "replayed", result.replayed(),
                "failed", result.failed(),
                "complete", result.isComplete()
        ));
    }

    @PostMapping("/historical/replay/orders")
    ResponseEntity<Map<String, Object>> replayOrders(
            @RequestParam(defaultValue = "") String symbol,
            @RequestParam long from,
            @RequestParam long to
    ) {
        var replayCheck = rejectIfLiveReplay();
        if (replayCheck.isPresent()) {
            return replayCheck.get();
        }
        var result = replayOrchestrator.replayOrders(
                symbol.isBlank() ? null : symbol, from, to, eventBus);
        return ResponseEntity.ok(Map.of(
                "mode", "orders",
                "symbol", symbol.isBlank() ? "*" : symbol,
                "from", from,
                "to", to,
                "totalRead", result.totalRead(),
                "replayed", result.replayed(),
                "failed", result.failed(),
                "complete", result.isComplete()
        ));
    }

    @PostMapping("/chronicle/replay")
    ResponseEntity<Map<String, Object>> replayChronicleAudit(
            @RequestParam String eventType
    ) {
        var replayCheck = rejectIfLiveReplay();
        if (replayCheck.isPresent()) {
            return replayCheck.get();
        }
        try {
            Class<?> clazz = Class.forName(eventType);
            if (!com.tradej.core.domain.event.DomainEvent.class.isAssignableFrom(clazz)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Type must implement DomainEvent: " + eventType));
            }
            @SuppressWarnings("unchecked")
            Class<? extends com.tradej.core.domain.event.DomainEvent> domainType =
                    (Class<? extends com.tradej.core.domain.event.DomainEvent>) clazz;
            var result = replayOrchestrator.replayChronicle(domainType);
            return ResponseEntity.ok(Map.of(
                    "eventType", eventType,
                    "totalRead", result.totalRead(),
                    "replayed", result.replayed(),
                    "failed", result.failed(),
                    "complete", result.isComplete()
            ));
        } catch (ClassNotFoundException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown event type: " + eventType));
        }
    }
}
