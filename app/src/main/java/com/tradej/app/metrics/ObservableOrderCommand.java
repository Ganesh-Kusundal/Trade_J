package com.tradej.app.metrics;

import com.tradej.broker.api.port.OrderCommand;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * A decorator around {@link OrderCommand} that records latency and call-count
 * metrics to a Micrometer {@link MeterRegistry}.
 *
 * <p>Each method is timed under {@code dhan.order.latency} with an {@code action} tag,
 * and each invocation increments {@code dhan.order.calls}.
 */
public final class ObservableOrderCommand implements OrderCommand {

    private static final String METRIC_LATENCY = "dhan.order.latency";
    private static final String METRIC_CALLS = "dhan.order.calls";
    private static final String TAG_ACTION = "action";

    private final OrderCommand delegate;
    private final MeterRegistry registry;
    private final Map<String, Timer> timerCache = new ConcurrentHashMap<>();
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    public ObservableOrderCommand(OrderCommand delegate, MeterRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public Order placeOrder(OrderRequest request) {
        return recordCall("placeOrder", () -> delegate.placeOrder(request));
    }

    @Override
    public Order modifyOrder(ModifyOrderRequest request) {
        return recordCall("modifyOrder", () -> delegate.modifyOrder(request));
    }

    @Override
    public boolean cancelOrder(String orderId) {
        return recordCall("cancelOrder", () -> delegate.cancelOrder(orderId));
    }

    @Override
    public List<String> cancelAllOpenOrders() {
        return recordCall("cancelAllOpenOrders", () -> delegate.cancelAllOpenOrders());
    }

    @Override
    public List<String> cancelAndSquareOffIntradayPositions() {
        return recordCall("cancelAndSquareOffIntradayPositions",
                () -> delegate.cancelAndSquareOffIntradayPositions());
    }

    @Override
    public boolean setKillSwitch(boolean enabled) {
        return recordCall("setKillSwitch", () -> delegate.setKillSwitch(enabled));
    }

    private <T> T recordCall(String action, Callable<T> call) {
        counter(action).increment();
        Timer timer = timer(action);
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

    private Counter counter(String action) {
        return counterCache.computeIfAbsent(action,
                a -> Counter.builder(METRIC_CALLS)
                        .tags(List.of(Tag.of(TAG_ACTION, a)))
                        .register(registry));
    }

    private Timer timer(String action) {
        return timerCache.computeIfAbsent(action,
                a -> Timer.builder(METRIC_LATENCY)
                        .tags(List.of(Tag.of(TAG_ACTION, a)))
                        .publishPercentileHistogram()
                        .register(registry));
    }
}
