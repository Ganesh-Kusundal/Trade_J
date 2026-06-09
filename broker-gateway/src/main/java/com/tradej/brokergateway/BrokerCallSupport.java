package com.tradej.brokergateway;

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
import java.util.function.Supplier;

/**
 * Shared infrastructure for broker handle classes.
 * Provides timed call wrapping, instrument resolution, and result building.
 * Replaces the former BaseBrokerHandle abstract class with composition.
 */
public final class BrokerCallSupport {

    private final BrokerSource source;
    private final IBrokerConnection connection;
    private final InstrumentResolver instruments;

    public BrokerCallSupport(BrokerSource source, IBrokerConnection connection) {
        this.source = source;
        this.connection = connection;
        this.instruments = connection.instruments();
    }

    public BrokerSource source() { return source; }
    public IBrokerConnection connection() { return connection; }
    public InstrumentResolver instruments() { return instruments; }

    public <T> T requireCapability(Class<T> capabilityClass, String featureName) {
        return connection.getCapability(capabilityClass)
                .orElseThrow(() -> new UnsupportedOperationException(
                        source + " does not support " + featureName));
    }

    public InstrumentKey resolveKey(String symbol, ExchangeSegment segment) {
        Instrument instrument = instruments.resolveNormalized(symbol, segment);
        return instrument.key();
    }

    public ExchangeSegment defaultSegment(String symbol) {
        return IndexSymbols.defaultSegment(symbol);
    }

    public <T> GatewayResult<T> timed(Supplier<T> call) {
        try (var span = SpanFactory.startSpan("broker." + source.name().toLowerCase())) {
            span.setAttribute("broker.source", source.name());
            Instant start = Instant.now();
            T data = call.get();
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

    public <T> GatewayResult<T> result(T data) {
        return GatewayResult.success(data, source,
                new ResultMetadata(Duration.ZERO, Instant.now(), UUID.randomUUID().toString(), Map.of()));
    }

    public static ExchangeSegment parseSegment(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val instanceof ExchangeSegment seg) return seg;
        if (val instanceof String s) return ExchangeSegment.valueOf(s.toUpperCase());
        return ExchangeSegment.NSE_EQ;
    }

    public static int parseLevels(Map<String, Object> params) {
        Object raw = params.get("levels");
        if (raw instanceof Number n) return Math.max(1, n.intValue());
        if (raw instanceof String s) {
            try { return Math.max(1, Integer.parseInt(s.trim())); }
            catch (NumberFormatException ex) { return 20; }
        }
        return 20;
    }
}
