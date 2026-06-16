package com.tradej.app.config;

import com.tradej.app.health.AlertChannel;
import com.tradej.app.health.AlertManager;
import com.tradej.app.health.LoggingAlertChannel;
import com.tradej.app.health.PagerDutyAlertChannel;
import com.tradej.app.health.SlackAlertChannel;
import com.tradej.app.health.WebhookAlertChannel;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.runtime.RuntimeBus;
import com.tradej.core.domain.runtime.RuntimeBusHolder;
import com.tradej.core.tracing.SpanFactory;
import com.tradej.disruptor.DisruptorBusMetrics;
import com.tradej.disruptor.DisruptorEventBus;
import com.tradej.disruptor.config.StageTiming;
import com.tradej.disruptor.config.StageTimings;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.feature.store.AsyncDuckDbWriter;
import com.tradej.gateway.bridge.GatewayEventBridge;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.hotpath.MarketDataPipeline;
import com.tradej.hotpath.OrderPipeline;
import com.tradej.persistence.chronicle.ChronicleDeadLetterQueue;
import com.tradej.persistence.duckdb.AsyncDuckDbEventStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified observability configuration consolidating tracing,
 * alerting, and stage timing beans.
 */
@Configuration
public class ObservabilityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityConfiguration.class);

    // ── Alerting ──

    @Bean
    public AlertManager alertManager(
            @Value("${tradej.alerts.webhook-url:}") String webhookUrl,
            @Value("${tradej.alerts.slack-webhook-url:}") String slackUrl,
            @Value("${tradej.alerts.pagerduty-routing-key:}") String pagerDutyKey,
            @Value("${tradej.alerts.enabled:true}") boolean enabled,
            @Value("${tradej.alerts.cooldown-seconds:300}") int cooldownSeconds
    ) {
        if (!enabled) {
            log.info("Alerting disabled — using no-op AlertManager");
            return new AlertManager(List.of(AlertChannel.noop()), Duration.ofSeconds(cooldownSeconds));
        }

        List<AlertChannel> channels = new ArrayList<>();
        channels.add(new LoggingAlertChannel());

        if (webhookUrl != null && !webhookUrl.isBlank()) {
            channels.add(new WebhookAlertChannel(webhookUrl));
            log.info("Alert channel: webhook");
        }
        if (slackUrl != null && !slackUrl.isBlank()) {
            channels.add(new SlackAlertChannel(slackUrl));
            log.info("Alert channel: Slack");
        }
        if (pagerDutyKey != null && !pagerDutyKey.isBlank()) {
            channels.add(new PagerDutyAlertChannel(pagerDutyKey));
            log.info("Alert channel: PagerDuty");
        }

        if (channels.size() == 1) {
            log.info("Alerting enabled (log only) — no external channels configured");
        } else {
            log.info("Alerting enabled with {} channels", channels.size());
        }

        return new AlertManager(channels, Duration.ofSeconds(cooldownSeconds));
    }

    // ── Tracing (conditional) ──

    /**
     * Enables Micrometer observations for hot-path tracing when {@code trade.tracing.enabled=true}.
     * Registers a {@link MicrometerSpanAdapter} with {@link SpanFactory} so that
     * {@code SpanFactory.startSpan("broker.quote")} creates real Micrometer observations
     * exported to Prometheus and any attached OpenTelemetry agent.
     */
    @Bean
    @ConditionalOnProperty(name = "trade.tracing.enabled", havingValue = "true")
    ObservationRegistry observationRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        SpanFactory.registerNamed(new MicrometerSpanAdapter(registry));
        log.info("Distributed tracing enabled — Micrometer observations registered with SpanFactory");
        return registry;
    }

    @Bean
    MeterFilter traceCorrelationTags() {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                return id.withTag(Tag.of("trace.id", ""))
                         .withTag(Tag.of("span.id", ""));
            }
        };
    }

    // ── Stage timing ──

    private static final double[] STAGE_PERCENTILES = {0.5, 0.95, 0.99, 0.999};

    @Bean
    StageTimings disruptorStageTimings(MeterRegistry meterRegistry) {
        return new StageTimings(
                stageTimer(meterRegistry, "risk"),
                stageTimer(meterRegistry, "candle"),
                stageTimer(meterRegistry, "strategy"),
                stageTimer(meterRegistry, "execution"),
                stageTimer(meterRegistry, "dispatch")
        );
    }

    private static StageTiming stageTimer(MeterRegistry registry, String stageName) {
        Timer timer = Timer.builder("disruptor.stage.latency")
                .tag("stage", stageName)
                .description("Event processing latency for the " + stageName + " Disruptor stage")
                .publishPercentileHistogram()
                .publishPercentiles(STAGE_PERCENTILES)
                .register(registry);
        return nanos -> timer.record(Duration.ofNanos(nanos));
    }

    // ── Micrometer gauge metrics ──

    /**
     * Registers Micrometer gauge metrics that track runtime state of the trading system.
     * Also wires the AlertManager into DisruptorEventBus for critical queue saturation paging.
     * Replaces the former MicrometerConfiguration class.
     */
    @Bean
    Object micrometerGauges(
            IBrokerConnection brokerConnection,
            ExecutionHandler executionHandler,
            DisruptorBusMetrics disruptorBusMetrics,
            RuntimeBusHolder runtimeBusHolder,
            EventBus eventBus,
            MarketDataPipeline marketDataPipeline,
            OrderPipeline orderPipeline,
            AlertManager alertManager,
            DeadLetterQueue deadLetterQueue,
            MeterRegistry meterRegistry,
            ObjectProvider<GatewayEventBridge> gatewayEventBridge,
            ObjectProvider<GatewayTopicRouter> gatewayTopicRouter,
            ObjectProvider<AsyncDuckDbEventStore> asyncDuckDbEventStore,
            ObjectProvider<AsyncDuckDbWriter> asyncDuckDbWriter
    ) {
        // Wire AlertManager into DisruptorEventBus for queue saturation / DLQ paging
        if (eventBus instanceof DisruptorEventBus disruptor) {
            disruptor.setAlertCallback(
                    msg -> alertManager.critical("disruptor-event-bus", msg),
                    msg -> alertManager.warning("disruptor-event-bus", msg)
            );
            log.info("AlertManager wired into DisruptorEventBus for critical queue/DLQ paging");
        }
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

        Gauge.builder("event.bus.started",
                        disruptorBusMetrics, metrics -> metrics.isStarted() ? 1.0 : 0.0)
                .description("Whether the configured event bus is started")
                .register(meterRegistry);

        Gauge.builder("event.bus.runtime",
                        runtimeBusHolder, holder -> holder.mode() == RuntimeBus.DISRUPTOR ? 1.0 : 0.0)
                .description("Configured event bus runtime (1 = disruptor, 0 = simple)")
                .register(meterRegistry);

        Gauge.builder("event.bus.subscribers",
                        disruptorBusMetrics, DisruptorBusMetrics::subscriberCount)
                .description("Number of registered event subscribers")
                .register(meterRegistry);

        Gauge.builder("event.bus.dispatch.queue.depth",
                        disruptorBusMetrics, DisruptorBusMetrics::dispatchQueueDepth)
                .description("Configured event bus async dispatch queue depth")
                .register(meterRegistry);

        Gauge.builder("event.bus.dispatch.dropped_events",
                        disruptorBusMetrics, DisruptorBusMetrics::dispatchDroppedEventCount)
                .description("Configured event bus dispatch dropped events")
                .register(meterRegistry);

        Gauge.builder("event.bus.ring.buffer.remaining_capacity",
                        disruptorBusMetrics, DisruptorBusMetrics::ringBufferRemainingCapacity)
                .description("Disruptor ring-buffer remaining capacity")
                .register(meterRegistry);

        Gauge.builder("event.bus.ring.buffer.size",
                        disruptorBusMetrics, DisruptorBusMetrics::ringBufferSize)
                .description("Disruptor ring-buffer size")
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

        Gauge.builder("execution.dropped.fill.count",
                        executionHandler, ExecutionHandler::droppedFillCount)
                .description("Cumulative number of fills dropped due to identity resolution failure")
                .register(meterRegistry);

        // ── Disruptor downstream queue (re-entrant event storm detection) ──
        Gauge.builder("disruptor.downstream.queue.depth",
                        disruptorBusMetrics, DisruptorBusMetrics::downstreamQueueDepth)
                .description("Number of events waiting in the downstream re-entrant queue")
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

        Gauge.builder("hotpath.ticks.rate_limited",
                        marketDataPipeline, MarketDataPipeline::tickRateLimitedCount)
                .description("Ticks dropped by hot-path rate limiter")
                .register(meterRegistry);

        gatewayEventBridge.ifAvailable(bridge -> Gauge.builder("gateway.events.sent", bridge, GatewayEventBridge::eventCount)
                .register(meterRegistry));
        gatewayTopicRouter.ifAvailable(router -> Gauge.builder("gateway.events.dropped", router, GatewayTopicRouter::droppedEventCount)
                .register(meterRegistry));
        asyncDuckDbEventStore.ifAvailable(store -> Gauge.builder("duckdb.events.dropped", store, AsyncDuckDbEventStore::droppedEventCount)
                .register(meterRegistry));
        asyncDuckDbWriter.ifAvailable(writer -> Gauge.builder("featurestore.events.dropped", writer, AsyncDuckDbWriter::droppedEventCount)
                .register(meterRegistry));

        // ── Chronicle DLQ append count (silent failure detection) ──
        if (deadLetterQueue instanceof ChronicleDeadLetterQueue chronicleDlq) {
            Gauge.builder("chronicle.dlq.append.count", chronicleDlq, ChronicleDeadLetterQueue::appendCount)
                    .description("Cumulative events written to the Chronicle Dead Letter Queue")
                    .register(meterRegistry);
        }

        log.info("Micrometer gauge metrics registered");
        return new Object();
    }
}
