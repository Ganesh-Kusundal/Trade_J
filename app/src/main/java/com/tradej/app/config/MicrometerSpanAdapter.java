package com.tradej.app.config;

import com.tradej.core.tracing.SpanFactory;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter that bridges {@link SpanFactory} spans to Micrometer {@link Observation}s.
 * When registered via {@link SpanFactory#registerNamed(SpanFactory.NamedSupplier)},
 * every {@code SpanFactory.startSpan("broker.quote")} call creates a Micrometer
 * observation that is exported to Prometheus and any attached OpenTelemetry agent.
 */
final class MicrometerSpanAdapter implements SpanFactory.NamedSupplier {

    private static final Logger log = LoggerFactory.getLogger(MicrometerSpanAdapter.class);

    private final ObservationRegistry registry;

    MicrometerSpanAdapter(ObservationRegistry registry) {
        this.registry = registry;
    }

    @Override
    public SpanFactory.Span start(String name) {
        if (registry.isNoop()) {
            return SpanFactory.NoOpSpan.INSTANCE;
        }
        try {
            Observation observation = Observation.start(name, registry);
            return new MicrometerSpan(observation, name);
        } catch (Exception e) {
            log.debug("Failed to start observation '{}': {}", name, e.getMessage());
            return SpanFactory.NoOpSpan.INSTANCE;
        }
    }

    private static final class MicrometerSpan implements SpanFactory.Span {
        private final Observation observation;
        private final String name;

        MicrometerSpan(Observation observation, String name) {
            this.observation = observation;
            this.name = name;
        }

        @Override
        public String spanName() {
            return name;
        }

        @Override
        public void setAttribute(String key, String value) {
            observation.lowCardinalityKeyValue(key, value);
        }

        @Override
        public void setAttribute(String key, long value) {
            observation.lowCardinalityKeyValue(key, Long.toString(value));
        }

        @Override
        public void recordException(Throwable throwable) {
            observation.error(throwable);
        }

        @Override
        public void close() {
            observation.stop();
        }
    }
}
