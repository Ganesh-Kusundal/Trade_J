package com.tradej.brokergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.brokergateway.result.ResultMetadata;
import com.tradej.core.domain.instrument.IndexSymbols;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.tracing.SpanFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Shared infrastructure for broker handle classes.
 * Provides timed call wrapping, instrument resolution, and result building.
 */
public abstract class BaseBrokerHandle {

    private static final ObjectMapper SHARED_MAPPER = new ObjectMapper();

    protected final BrokerSource source;
    protected final IBrokerConnection connection;
    protected final InstrumentResolver instruments;
    protected final ObjectMapper objectMapper;

    protected BaseBrokerHandle(BrokerSource source, IBrokerConnection connection) {
        this.source = source;
        this.connection = connection;
        this.instruments = connection.instruments();
        this.objectMapper = SHARED_MAPPER;
    }

    protected <T> T requireCapability(Class<T> capabilityClass, String featureName) {
        return connection.getCapability(capabilityClass)
                .orElseThrow(() -> new UnsupportedOperationException(
                        source + " does not support " + featureName));
    }

    protected InstrumentKey resolveKey(String symbol, ExchangeSegment segment) {
        Instrument instrument = instruments.resolveNormalized(symbol, segment);
        return instrument.key();
    }

    protected ExchangeSegment defaultSegment(String symbol) {
        return IndexSymbols.defaultSegment(symbol);
    }

    @FunctionalInterface
    protected interface TimedCall<T> {
        T call();
    }

    protected <T> GatewayResult<T> timed(TimedCall<T> call) {
        try (var span = SpanFactory.startSpan("broker." + source.name().toLowerCase())) {
            span.setAttribute("broker.source", source.name());
            Instant start = Instant.now();
            T data = call.call();
            Duration latency = Duration.between(start, Instant.now());
            span.setAttribute("latency.ms", latency.toMillis());
            ResultMetadata metadata = new ResultMetadata(latency, Instant.now(), UUID.randomUUID().toString(), Map.of());
            return GatewayResult.success(data, source, metadata);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Broker call failed for " + source, e);
        }
    }

    protected <T> GatewayResult<T> result(T data) {
        return GatewayResult.success(data, source,
                new ResultMetadata(Duration.ZERO, Instant.now(), UUID.randomUUID().toString(), Map.of()));
    }

    protected static ExchangeSegment parseSegment(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val instanceof ExchangeSegment seg) return seg;
        if (val instanceof String s) return ExchangeSegment.valueOf(s.toUpperCase());
        return ExchangeSegment.NSE_EQ;
    }

    protected static int parseLevels(Map<String, Object> params) {
        Object raw = params.get("levels");
        if (raw instanceof Number n) return Math.max(1, n.intValue());
        if (raw instanceof String s) {
            try { return Math.max(1, Integer.parseInt(s.trim())); }
            catch (NumberFormatException ex) { return 20; }
        }
        return 20;
    }
}
