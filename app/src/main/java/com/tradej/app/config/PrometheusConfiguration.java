package com.tradej.app.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Prometheus-specific meter configuration.
 *
 * <p>Registers common tags, custom counters for order and signal metrics,
 * and JVM metric bindings with application-level tags.
 */
@Configuration
public class PrometheusConfiguration {

    @Bean
    MeterFilter commonTags() {
        return MeterFilter.commonTags(List.of(
                Tag.of("application", "trade-j"),
                Tag.of("region", "local")
        ));
    }

    @Bean
    MeterBinder jvmMetricsWithTags() {
        return registry -> {
            Timer.builder("jvm.gc.pause")
                    .description("JVM GC pause time")
                    .register(registry);
        };
    }

    @Bean
    Counter ordersPlacedTotal(MeterRegistry registry) {
        return Counter.builder("orders_placed_total")
                .description("Total number of orders placed successfully")
                .tag("application", "trade-j")
                .register(registry);
    }

    @Bean
    Counter ordersRejectedTotal(MeterRegistry registry) {
        return Counter.builder("orders_rejected_total")
                .description("Total number of orders rejected")
                .tag("application", "trade-j")
                .register(registry);
    }

    @Bean
    Counter signalsGeneratedTotal(MeterRegistry registry) {
        return Counter.builder("signals_generated_total")
                .description("Total number of trading signals generated")
                .tag("application", "trade-j")
                .register(registry);
    }
}
