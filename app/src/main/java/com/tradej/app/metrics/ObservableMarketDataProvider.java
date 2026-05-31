package com.tradej.app.metrics;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Quote;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A decorator around {@link MarketDataProvider} that records latency and call-count
 * metrics to a Micrometer {@link MeterRegistry}.
 *
 * <p>Each public method is timed under the {@code dhan.marketdata.latency} timer
 * with a {@code method} tag, and each invocation increments
 * {@code dhan.marketdata.calls} counter.
 */
public final class ObservableMarketDataProvider implements MarketDataProvider {

    private static final String METRIC_LATENCY = "dhan.marketdata.latency";
    private static final String METRIC_CALLS = "dhan.marketdata.calls";
    private static final String TAG_METHOD = "method";

    private final MarketDataProvider delegate;
    private final MeterRegistry registry;
    private final Map<String, Timer> timerCache = new ConcurrentHashMap<>();
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    public ObservableMarketDataProvider(MarketDataProvider delegate, MeterRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public long getLtpPaisa(InstrumentKey instrumentKey) {
        return recordCall("getLtpPaisa", () -> delegate.getLtpPaisa(instrumentKey));
    }

    @Override
    public Quote getQuote(InstrumentKey instrumentKey) {
        return recordCall("getQuote", () -> delegate.getQuote(instrumentKey));
    }

    @Override
    public MarketDepth getDepth(InstrumentKey instrumentKey) {
        return recordCall("getDepth", () -> delegate.getDepth(instrumentKey));
    }

    @Override
    public Quote getOhlcSnapshot(InstrumentKey instrumentKey) {
        return recordCall("getOhlcSnapshot", () -> delegate.getOhlcSnapshot(instrumentKey));
    }

    @Override
    public List<Candle> getCandles(CandleHistoryRequest request) {
        return recordCall("getCandles", () -> delegate.getCandles(request));
    }

    @Override
    public Map<InstrumentKey, Long> getLtpBatch(Collection<InstrumentKey> instrumentKeys) {
        return recordCall("getLtpBatch", () -> delegate.getLtpBatch(instrumentKeys));
    }

    @Override
    public Map<InstrumentKey, Quote> getQuoteBatch(Collection<InstrumentKey> instrumentKeys) {
        return recordCall("getQuoteBatch", () -> delegate.getQuoteBatch(instrumentKeys));
    }

    @Override
    public Map<InstrumentKey, Quote> getOhlcBatch(Collection<InstrumentKey> instrumentKeys) {
        return recordCall("getOhlcBatch", () -> delegate.getOhlcBatch(instrumentKeys));
    }

    private <T> T recordCall(String method, Callable<T> call) {
        counter(method).increment();
        Timer timer = timer(method);
        return timer.record(() -> {
            try {
                return call.call();
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private Counter counter(String method) {
        return counterCache.computeIfAbsent(method,
                m -> Counter.builder(METRIC_CALLS)
                        .tags(List.of(Tag.of(TAG_METHOD, m)))
                        .register(registry));
    }

    private Timer timer(String method) {
        return timerCache.computeIfAbsent(method,
                m -> Timer.builder(METRIC_LATENCY)
                        .tags(List.of(Tag.of(TAG_METHOD, m)))
                        .publishPercentileHistogram()
                        .register(registry));
    }
}
