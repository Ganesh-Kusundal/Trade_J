package com.tradej.app.config;

import com.tradej.app.health.AlertChannel;
import com.tradej.app.health.AlertManager;
import com.tradej.app.health.LoggingAlertChannel;
import com.tradej.app.health.PagerDutyAlertChannel;
import com.tradej.app.health.SlackAlertChannel;
import com.tradej.app.health.WebhookAlertChannel;
import com.tradej.core.tracing.SpanFactory;
import com.tradej.disruptor.config.StageTiming;
import com.tradej.disruptor.config.StageTimings;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
}
