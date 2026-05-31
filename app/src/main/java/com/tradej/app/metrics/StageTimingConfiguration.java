package com.tradej.app.metrics;

import com.tradej.disruptor.config.StageTiming;
import com.tradej.disruptor.config.StageTimings;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Creates Micrometer-backed {@link StageTimings} for the Disruptor pipeline.
 *
 * <p>Each stage in the Disruptor pipeline gets a dedicated {@link Timer} with
 * percentile histograms enabled, tagged by stage name. These timers measure
 * the elapsed time per event on the Disruptor consumer thread, providing
 * visibility into which stage is the bottleneck.
 *
 * <p>Prometheus metric: {@code disruptor_stage_latency_seconds}
 * with tags: {@code stage=[risk|candle|strategy|execution|dispatch]}
 *
 * <p>Only one timing bean exists for the single JVM deployment; symbol-sharded
 * pipelines (Phase 3) would require a shard tag.
 */
@Configuration
public class StageTimingConfiguration {

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
