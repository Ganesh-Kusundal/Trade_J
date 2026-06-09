package com.tradej.strategy.portfolio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.value.Side;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Portfolio-level capital allocation and net exposure engine.
 *
 * <p>Tracks per-strategy capital usage and per-symbol net positions across all
 * registered strategies. Delegates capital management to {@link CapitalReservationService}
 * and exposure tracking to {@link ExposureTracker}.
 *
 * <p><b>Capital lifecycle:</b>
 * <ul>
 *   <li>When a {@link SignalGenerated} passes portfolio checks, the estimated
 *       capital is <em>reserved</em> (added to used capital), the net position
 *       estimate is updated, and the signal ID is stored for attribution.</li>
 *   <li>When a {@link TradeOpened} arrives, the estimate is replaced with the
 *       actual fill values (handling partial fills, price slippage).</li>
 *   <li>When a {@link TradeClosed} arrives, the capital is freed and the net
 *       position is reduced.</li>
 * </ul>
 */
public final class PortfolioEngine {

    private static final Logger log = LoggerFactory.getLogger(PortfolioEngine.class);

    public static final long DEFAULT_CAPITAL_PER_STRATEGY_PAISA = 1_000_000L;
    public static final long DEFAULT_MAX_NET_EXPOSURE_PAISA = 5_000_000L;
    public static final String ATTR_STRATEGY_NAME = "strategyName";
    private static final String ATTR_QUANTITY = "quantity";

    private final DefaultCapitalReservationService capitalService;
    private final DefaultExposureTracker exposureTracker;
    private final long defaultCapitalPaisa;

    // Trade → trade info: tradeId → TradeInfo (for TradeClosed cleanup)
    private final ConcurrentHashMap<String, TradeInfo> openTrades = new ConcurrentHashMap<>();

    // P0-7: Dedicated thread infrastructure to move processing off the ring buffer thread
    private static final int DEFAULT_QUEUE_CAPACITY = 1024;
    private final BlockingQueue<PortfolioCommand> eventQueue = new ArrayBlockingQueue<>(DEFAULT_QUEUE_CAPACITY);
    private final ExecutorService portfolioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "portfolio-engine");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong droppedEventCount = new AtomicLong();
    private volatile boolean running;

    public PortfolioEngine(long defaultCapitalPaisa, long maxNetExposurePaisa) {
        this.defaultCapitalPaisa = defaultCapitalPaisa;
        this.capitalService = new DefaultCapitalReservationService(defaultCapitalPaisa);
        this.exposureTracker = new DefaultExposureTracker(maxNetExposurePaisa);
    }

    public PortfolioEngine() {
        this(DEFAULT_CAPITAL_PER_STRATEGY_PAISA, DEFAULT_MAX_NET_EXPOSURE_PAISA);
    }

    // ── External API ──

    /**
     * Starts the dedicated portfolio-engine thread (P0-7).
     * Once started, {@link #onDomainEvent} enqueues events for async processing
     * instead of handling them synchronously on the caller thread.
     */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        portfolioExecutor.submit(this::runLoop);
    }

    /**
     * Stops the dedicated portfolio-engine thread and drains remaining events.
     */
    public void stop() {
        running = false;
        portfolioExecutor.shutdownNow();
    }

    /**
     * Returns the number of events dropped due to a full queue (P0-7).
     */
    public long droppedEventCount() {
        return droppedEventCount.get();
    }

    /**
     * Returns the current queue depth (P0-7).
     */
    public int queueDepth() {
        return eventQueue.size();
    }

    /**
     * Dispatches a domain event for portfolio processing.
     * <p>
     * When the engine is {@linkplain #start() started}, the event is enqueued for
     * async processing on the dedicated {@code portfolio-engine} thread, keeping
     * the caller (e.g. Disruptor ring buffer) unblocked. If the queue is full,
     * the event is dropped and a WARN is logged.
     * <p>
     * When the engine is <em>not</em> started (default), processing is synchronous
     * on the caller thread for backward compatibility.
     */
    public void onDomainEvent(DomainEvent event, Consumer<DomainEvent> downstream) {
        if (running) {
            boolean isLedgerEvent = event instanceof TradeOpened
                    || event instanceof TradeClosed
                    || event instanceof OrderAccepted
                    || event instanceof OrderRejected;
            if (isLedgerEvent) {
                try {
                    eventQueue.put(new PortfolioCommand(event, downstream));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted while putting ledger event into PortfolioEngine queue", e);
                }
            } else {
                if (!eventQueue.offer(new PortfolioCommand(event, downstream))) {
                    long dropped = droppedEventCount.incrementAndGet();
                    log.warn("PortfolioEngine queue full — dropping event type={} droppedCount={}",
                            event.getClass().getSimpleName(), dropped);
                }
            }
        } else {
            processEvent(event, downstream);
        }
    }

    private void runLoop() {
        while (running) {
            try {
                PortfolioCommand command = eventQueue.take();
                processEvent(command.event(), command.downstream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void processEvent(DomainEvent event, Consumer<DomainEvent> downstream) {
        switch (event) {
            case SignalGenerated signal -> passThroughSignal(signal, downstream);
            case SignalSuppressed suppressed -> onSignalSuppressed(suppressed, downstream);
            case OrderAccepted accepted -> onOrderAccepted(accepted, downstream);
            case OrderRejected rejected -> onOrderRejected(rejected, downstream);
            case TradeOpened trade -> onTradeOpened(trade, downstream);
            case TradeClosed trade -> onTradeClosed(trade, downstream);
            default -> downstream.accept(event);
        }
    }

    private record PortfolioCommand(DomainEvent event, Consumer<DomainEvent> downstream) {}

    // ── Portfolio state queries ──

    public long usedCapitalPaisa(String strategyName) {
        return capitalService.usedCapitalPaisa(strategyName);
    }

    public long allocatedCapitalPaisa(String strategyName) {
        return capitalService.allocatedCapitalPaisa(strategyName);
    }

    public long netPosition(String symbol) {
        return exposureTracker.netPosition(symbol);
    }

    public Map<String, Long> netPositionsSnapshot() {
        return exposureTracker.netPositionsSnapshot();
    }

    public Map<String, StrategyAllocation> allocationsSnapshot() {
        return capitalService.allocationsSnapshot();
    }

    public void reset() {
        capitalService.reset();
        exposureTracker.reset();
        openTrades.clear();
    }

    // ── Replay state isolation ──

    public StateSnapshot snapshot() {
        return new StateSnapshot(
                new ConcurrentHashMap<>(capitalService.allocations()),
                new ConcurrentHashMap<>(exposureTracker.netPositions()),
                new ConcurrentHashMap<>(capitalService.signalToStrategy()),
                new ConcurrentHashMap<>(capitalService.signalToEstimatedCapital()),
                new ConcurrentHashMap<>(exposureTracker.signalDeltas()),
                new ConcurrentHashMap<>(capitalService.orderIdToSignalId()),
                new ConcurrentHashMap<>(openTrades)
        );
    }

    public void restore(StateSnapshot snapshot) {
        capitalService.allocations().clear();
        capitalService.allocations().putAll(snapshot.allocations());
        exposureTracker.netPositions().clear();
        exposureTracker.netPositions().putAll(snapshot.netPositions());
        capitalService.signalToStrategy().clear();
        capitalService.signalToStrategy().putAll(snapshot.signalToStrategy());
        capitalService.signalToEstimatedCapital().clear();
        capitalService.signalToEstimatedCapital().putAll(snapshot.signalToEstimatedCapital());
        exposureTracker.signalDeltas().clear();
        exposureTracker.signalDeltas().putAll(snapshot.signalDeltas());
        capitalService.orderIdToSignalId().clear();
        capitalService.orderIdToSignalId().putAll(snapshot.orderIdToSignalId());
        openTrades.clear();
        openTrades.putAll(snapshot.openTrades());
    }

    public record StateSnapshot(
            Map<String, StrategyAllocation> allocations,
            Map<String, Long> netPositions,
            Map<String, String> signalToStrategy,
            Map<String, Long> signalToEstimatedCapital,
            Map<String, Long> signalDeltas,
            Map<String, String> orderIdToSignalId,
            Map<String, TradeInfo> openTrades
    ) {}

    // ── Signal pass-through ──

    private void passThroughSignal(SignalGenerated signal, Consumer<DomainEvent> downstream) {
        downstream.accept(signal);
    }

    /**
     * Checks portfolio-level constraints (strategy capital limit and symbol
     * net exposure limit) for the given signal. Delegates to
     * {@link CapitalReservationService#reserveSignal} and
     * {@link DefaultExposureTracker#checkAndReserve}.
     *
     * @return {@code null} if approved, or a rejection reason string
     */
    public String reserveSignal(SignalGenerated signal) {
        long qty = extractQuantity(signal);
        long signalDelta = signal.side().isBuySide() ? qty : -qty;

        String capitalResult = capitalService.reserveSignal(signal);
        if (capitalResult != null) {
            return capitalResult;
        }

        String exposureResult = exposureTracker.checkAndReserve(signal.symbol(), signalDelta, signal.entryPricePaisa());
        if (exposureResult != null) {
            capitalService.revertReservation(signal.signalId());
            return exposureResult;
        }

        exposureTracker.storeSignalDelta(signal.signalId(), signalDelta);

        log.info("Portfolio reserved capital for signal symbol={} side={} qty={} price={}",
                signal.symbol(), signal.side(), qty, signal.entryPricePaisa());
        return null;
    }

    // ── Signal / order lifecycle ──

    private String freeSignalCapital(String signalId, String symbol) {
        String strategyName = capitalService.freeSignalCapital(signalId, symbol);
        Long signalDelta = exposureTracker.removeSignalDelta(signalId);
        if (signalDelta != null) {
            exposureTracker.reverseSignalDelta(symbol, signalDelta);
        }
        return strategyName;
    }

    private void onSignalSuppressed(SignalSuppressed suppressed, Consumer<DomainEvent> downstream) {
        String strategyName = freeSignalCapital(suppressed.signalId(), suppressed.symbol());
        if (strategyName != null) {
            log.info("Portfolio freed capital for suppressed signal signalId={} symbol={} reason={}",
                    suppressed.signalId(), suppressed.symbol(), suppressed.reason());
        }
        downstream.accept(suppressed);
    }

    private void onOrderAccepted(OrderAccepted accepted, Consumer<DomainEvent> downstream) {
        String signalId = accepted.order().correlationId();
        String orderId = accepted.order().orderId();
        capitalService.trackOrderId(orderId, signalId);
        downstream.accept(accepted);
    }

    private void onOrderRejected(OrderRejected rejected, Consumer<DomainEvent> downstream) {
        String orderId = rejected.order().orderId();
        String signalId = capitalService.signalIdForOrder(orderId);
        if (signalId == null) {
            signalId = rejected.order().correlationId();
        }
        String strategyName = freeSignalCapital(signalId, rejected.order().symbol());
        if (strategyName != null) {
            log.info("Portfolio freed capital for rejected order orderId={} signalId={}",
                    orderId, signalId);
        }
        downstream.accept(rejected);
    }

    // ── Trade lifecycle ──

    private void onTradeOpened(TradeOpened trade, Consumer<DomainEvent> downstream) {
        String signalId = trade.signalId();
        String strategyName = capitalService.signalToStrategy().remove(signalId);
        if (strategyName == null) {
            strategyName = "default";
            log.warn("Unknown signalId={} for tradeId={} — attributing to 'default' strategy",
                    signalId, trade.tradeId());
        }

        Long removedEstimate = capitalService.signalToEstimatedCapital().remove(signalId);
        long estimatedCapital = removedEstimate == null ? 0L : removedEstimate;
        long actualCapital = trade.size() * trade.entryPricePaisa();
        capitalService.adjustOnTradeOpened(strategyName, estimatedCapital, actualCapital);

        long actualTradeDelta = trade.side().isBuySide() ? trade.size() : -trade.size();
        Long signalDelta = exposureTracker.removeSignalDelta(signalId);
        long sd = signalDelta != null ? signalDelta : 0L;
        exposureTracker.onTradeOpened(trade, actualTradeDelta, sd);

        openTrades.put(trade.tradeId(), new TradeInfo(
                strategyName, trade.symbol(), trade.side(), trade.size(), trade.entryPricePaisa()
        ));

        log.info("Portfolio committed trade strategy={} symbol={} side={} size={} price={}",
                strategyName, trade.symbol(), trade.side(), trade.size(), trade.entryPricePaisa());
        downstream.accept(trade);
    }

    private void onTradeClosed(TradeClosed trade, Consumer<DomainEvent> downstream) {
        TradeInfo info = openTrades.remove(trade.tradeId());
        if (info == null) {
            log.warn("Unknown tradeId={} on close — cannot free capital", trade.tradeId());
            downstream.accept(trade);
            return;
        }

        capitalService.freeTradeCapital(info.strategyName(), info.capitalPaisa());
        exposureTracker.onTradeClosed(trade, info.symbol(), info.netDelta());

        log.info("Portfolio freed trade strategy={} symbol={} pnl={} reason={}",
                info.strategyName(), trade.symbol(), trade.realizedPnlPaisa(), trade.reason());
        downstream.accept(trade);
    }

    // ── Helpers ──

    private static String extractStrategyName(SignalGenerated signal) {
        Object name = signal.attributes().get(ATTR_STRATEGY_NAME);
        if (name instanceof String s && !s.isBlank()) {
            return s;
        }
        return "unknown";
    }

    public static long extractQuantity(SignalGenerated signal) {
        Object qty = signal.attributes().get(ATTR_QUANTITY);
        if (qty instanceof Number n) {
            long v = n.longValue();
            return Math.max(0L, v);
        }
        return 0L;
    }

    private static long requiredCapital(SignalGenerated signal) {
        return extractQuantity(signal) * signal.entryPricePaisa();
    }

    // ── Value types ──

    public record StrategyAllocation(long allocatedCapitalPaisa, long usedCapitalPaisa) {
    }

    private record TradeInfo(
            String strategyName,
            String symbol,
            Side side,
            long size,
            long entryPricePaisa
    ) {
        long capitalPaisa() {
            return size * entryPricePaisa;
        }

        long netDelta() {
            return side().isBuySide() ? size : -size;
        }
    }
}
