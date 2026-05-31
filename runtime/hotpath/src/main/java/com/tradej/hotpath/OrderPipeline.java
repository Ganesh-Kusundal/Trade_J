package com.tradej.hotpath;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.event.TradeUpdated;
import com.tradej.core.support.MdcHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Pure Java orchestrator for the order lifecycle hot path.
 *
 * <p>This class manages the flow of order-related events through the
 * Disruptor event pipeline. It accepts broker order lifecycle callbacks
 * (accepted, filled, rejected, trades) and forwards them to the event bus.
 *
 * <p>Signals do <b>not</b> flow through this pipeline — they travel from
 * {@code StrategySandbox} → {@code PortfolioEngine} → downstream queue →
 * drainer thread → {@code DisruptorEventBus.publish()}.
 *
 * <p>Intentionally free of Spring annotations or proxies so that the
 * order pipeline can be tested and deployed without container overhead.
 *
 * <p>Thread safety: all public methods are safe for multi-threaded access.
 *
 * <p>Metrics:
 * <ul>
 *   <li>{@link #totalOrdersAccepted()} — cumulative order count</li>
 *   <li>{@link #orderRate()} — exponential moving average orders/second</li>
 * </ul>
 */
public final class OrderPipeline {

    private static final Logger log = LoggerFactory.getLogger(OrderPipeline.class);

    /** Smoothing factor for the EMA rate. Higher = more responsive to spikes. */
    static final double RATE_ALPHA = 0.3;

    private final Consumer<DomainEvent> downstream;
    private final AtomicLong orderCount;
    private final AtomicReference<RateState> orderRateState;

    /**
     * Creates an order lifecycle pipeline.
     *
     * @param downstream consumer that forwards events into the event bus
     *                    (typically a reference to {@code DisruptorEventBus::publish})
     */
    public OrderPipeline(Consumer<DomainEvent> downstream) {
        this.downstream = Objects.requireNonNull(downstream, "downstream must not be null");
        this.orderCount = new AtomicLong(0);
        this.orderRateState = new AtomicReference<>(new RateState(0.0, 0L));
    }

    /**
     * Process an order acceptance notification from the broker.
     *
     * @param acceptance the order acceptance event
     */
    public void onOrderAccepted(OrderAccepted acceptance) {
        if (acceptance == null) return;
        MdcHelper.enrich(acceptance, "order");
        try {
            orderCount.incrementAndGet();
            updateRate(orderRateState);
            downstream.accept(acceptance);
        } finally {
            MdcHelper.clear();
        }
    }

    /**
     * Process a fill notification from the broker.
     *
     * @param fill the order fill event
     */
    public void onOrderFilled(OrderFilled fill) {
        if (fill == null) return;
        MdcHelper.enrich(fill, "order");
        try {
            downstream.accept(fill);
        } finally {
            MdcHelper.clear();
        }
    }

    /**
     * Process an order rejection from the broker.
     *
     * @param rejection the order rejection event
     */
    public void onOrderRejected(OrderRejected rejection) {
        if (rejection == null) return;
        MdcHelper.enrich(rejection, "order");
        try {
            downstream.accept(rejection);
        } finally {
            MdcHelper.clear();
        }
    }

    /**
     * Process a trade open notification.
     *
     * @param tradeOpened the trade opened event
     */
    public void onTradeOpened(TradeOpened tradeOpened) {
        if (tradeOpened == null) return;
        MdcHelper.enrich(tradeOpened, "order");
        try {
            downstream.accept(tradeOpened);
        } finally {
            MdcHelper.clear();
        }
    }

    /**
     * Process a trade update (partial fill, stop-loss adjustment, etc.).
     *
     * @param tradeUpdated the trade update event
     */
    public void onTradeUpdated(TradeUpdated tradeUpdated) {
        if (tradeUpdated == null) return;
        MdcHelper.enrich(tradeUpdated, "order");
        try {
            downstream.accept(tradeUpdated);
        } finally {
            MdcHelper.clear();
        }
    }

    /**
     * Process a trade close notification.
     *
     * @param tradeClosed the trade closed event
     */
    public void onTradeClosed(TradeClosed tradeClosed) {
        if (tradeClosed == null) return;
        MdcHelper.enrich(tradeClosed, "order");
        try {
            downstream.accept(tradeClosed);
        } finally {
            MdcHelper.clear();
        }
    }

    /**
     * Returns the total number of orders accepted through the pipeline.
     */
    public long totalOrdersAccepted() {
        return orderCount.get();
    }

    /**
     * Returns the exponential moving average order rate (orders per second).
     *
     * <p>Returns 0.0 until at least two orders have been accepted.
     */
    public double orderRate() {
        return orderRateState.get().smoothedRate;
    }

    /**
     * Updates the EMA rate for the given state using the wall clock.
     */
    private void updateRate(AtomicReference<RateState> state) {
        long now = System.nanoTime();
        while (true) {
            RateState current = state.get();
            if (current.lastEventNanos == 0L) {
                if (state.compareAndSet(current, new RateState(0.0, now))) {
                    return;
                }
            } else {
                double elapsedSec = (now - current.lastEventNanos) / 1_000_000_000.0;
                if (elapsedSec <= 0) {
                    return;
                }
                double instantRate = 1.0 / elapsedSec;
                double smoothed = RATE_ALPHA * instantRate + (1.0 - RATE_ALPHA) * current.smoothedRate;
                if (state.compareAndSet(current, new RateState(smoothed, now))) {
                    return;
                }
            }
        }
    }

    /**
     * Immutable rate tracking state for lock-free CAS updates.
     *
     * @param smoothedRate   exponential moving average events/second
     * @param lastEventNanos System.nanoTime() of the most recent event (0 for first event)
     */
    private record RateState(double smoothedRate, long lastEventNanos) {
    }
}
