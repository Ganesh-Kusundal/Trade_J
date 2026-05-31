package com.tradej.app.metrics;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.disruptor.DisruptorBusMetrics;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.hotpath.MarketDataPipeline;
import com.tradej.hotpath.OrderPipeline;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Registers Micrometer gauge metrics that track runtime state of the trading system.
 *
 * <p>Gauges are sampled each time Prometheus scrapes the {@code /actuator/prometheus}
 * endpoint, so they reflect near-real-time values without adding overhead to the hot path.
 *
 * <p>Metrics registered:
 * <ul>
 *   <li>{@code dhan.websocket.connected} — 1 if the broker WebSocket is connected, 0 otherwise</li>
 *   <li>{@code dhan.websocket.subscriptions} — number of active market data subscriptions</li>
 *   <li>{@code instruments.catalog.size} — number of instruments in the loaded catalog</li>
 *   <li>{@code execution.queue.depth} — number of pending execution commands in the handler queue</li>
 *   <li>{@code execution.queue.remaining_capacity} — remaining capacity of the execution queue</li>
 *   <li>{@code disruptor.ring.buffer.remaining_capacity} — remaining capacity of the Disruptor ring buffer</li>
 *   <li>{@code disruptor.ring.buffer.size} — total ring buffer size</li>
 *   <li>{@code disruptor.dispatch.queue.depth} — number of events waiting in the async dispatch queue</li>
 *   <li>{@code disruptor.dispatch.dropped_events} — total events dropped by the dispatch queue</li>
 *   <li>{@code disruptor.subscribers.count} — number of registered event subscribers</li>
 *   <li>{@code hotpath.ticks.total} — cumulative tick count</li>
 *   <li>{@code hotpath.ticks.rate} — exponential moving average ticks/second</li>
 *   <li>{@code hotpath.orders.accepted.total} — cumulative accepted order count</li>
 *   <li>{@code hotpath.orders.rate} — exponential moving average orders/second</li>
 * </ul>
 */

@Configuration
public class MicrometerConfiguration {

    private final IBrokerConnection brokerConnection;
    private final ExecutionHandler executionHandler;
    private final DisruptorBusMetrics disruptorBusMetrics;
    private final MarketDataPipeline marketDataPipeline;
    private final OrderPipeline orderPipeline;
    private final MeterRegistry meterRegistry;

    public MicrometerConfiguration(
            IBrokerConnection brokerConnection,
            ExecutionHandler executionHandler,
            DisruptorBusMetrics disruptorBusMetrics,
            MarketDataPipeline marketDataPipeline,
            OrderPipeline orderPipeline,
            MeterRegistry meterRegistry
    ) {
        this.brokerConnection = brokerConnection;
        this.executionHandler = executionHandler;
        this.disruptorBusMetrics = disruptorBusMetrics;
        this.marketDataPipeline = marketDataPipeline;
        this.orderPipeline = orderPipeline;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void registerGauges() {
        // ── Broker health ──
        Gauge.builder("dhan.websocket.connected",
                        brokerConnection, conn -> conn.websocket().isConnected() ? 1.0 : 0.0)
                .description("Broker WebSocket connection status (1 = connected, 0 = disconnected)")
                .register(meterRegistry);

        Gauge.builder("dhan.websocket.subscriptions",
                        brokerConnection, conn -> conn.websocket().subscriptions().size())
                .description("Number of active market data subscriptions")
                .register(meterRegistry);

        Gauge.builder("instruments.catalog.size",
                        brokerConnection, conn -> conn.instruments().allInstruments().size())
                .description("Number of instruments in the loaded catalog")
                .register(meterRegistry);

        // ── Execution queue ──
        Gauge.builder("execution.queue.depth",
                        executionHandler, ExecutionHandler::queueDepth)
                .description("Number of pending execution commands")
                .register(meterRegistry);

        Gauge.builder("execution.queue.remaining_capacity",
                        executionHandler, ExecutionHandler::queueRemainingCapacity)
                .description("Remaining capacity of the execution command queue")
                .register(meterRegistry);

        // ── Disruptor ring buffer ──
        Gauge.builder("disruptor.ring.buffer.remaining_capacity",
                        disruptorBusMetrics, DisruptorBusMetrics::ringBufferRemainingCapacity)
                .description("Remaining capacity of the Disruptor ring buffer")
                .register(meterRegistry);

        Gauge.builder("disruptor.ring.buffer.size",
                        disruptorBusMetrics, DisruptorBusMetrics::ringBufferSize)
                .description("Total size of the Disruptor ring buffer")
                .register(meterRegistry);

        // ── Market data pipeline ──
        Gauge.builder("hotpath.ticks.total",
                        marketDataPipeline, MarketDataPipeline::totalTicksProcessed)
                .description("Cumulative number of ticks processed by the market data pipeline")
                .register(meterRegistry);

        Gauge.builder("hotpath.ticks.rate",
                        marketDataPipeline, MarketDataPipeline::tickRate)
                .description("Exponential moving average tick rate (ticks/second)")
                .register(meterRegistry);

        // ── Order pipeline (order lifecycle only — signals bypass OrderPipeline) ──
        Gauge.builder("hotpath.orders.accepted.total",
                        orderPipeline, OrderPipeline::totalOrdersAccepted)
                .description("Cumulative number of orders accepted through the order pipeline")
                .register(meterRegistry);

        Gauge.builder("hotpath.orders.rate",
                        orderPipeline, OrderPipeline::orderRate)
                .description("Exponential moving average order rate (orders/second)")
                .register(meterRegistry);

        // ── Disruptor dispatch queue ──
        Gauge.builder("disruptor.dispatch.queue.depth",
                        disruptorBusMetrics, DisruptorBusMetrics::dispatchQueueDepth)
                .description("Number of events waiting in the async dispatch queue")
                .register(meterRegistry);

        Gauge.builder("disruptor.dispatch.dropped_events",
                        disruptorBusMetrics, DisruptorBusMetrics::dispatchDroppedEventCount)
                .description("Total number of events dropped by the dispatch queue")
                .register(meterRegistry);

        Gauge.builder("disruptor.subscribers.count",
                        disruptorBusMetrics, DisruptorBusMetrics::subscriberCount)
                .description("Number of registered event subscribers")
                .register(meterRegistry);
    }
}
