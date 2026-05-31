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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Portfolio-level capital allocation and net exposure engine.
 *
 * <p>Tracks per-strategy capital usage and per-symbol net positions across all
 * registered strategies. Filters {@link SignalGenerated} events by checking:
 * <ol>
 *   <li><b>Strategy capital limit</b> — the strategy's used capital plus the
 *       proposed position's notional must not exceed its allocated capital.</li>
 *   <li><b>Symbol net exposure limit</b> — the aggregate net position across
 *       all strategies for the given symbol, after adding the proposed signal,
 *       must not exceed the maximum net exposure threshold.</li>
 * </ol>
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
 *
 * <p>Trade → strategy attribution uses {@code signalId}:
 * {@link SignalGenerated#signalId()} → {@link TradeOpened#signalId()}.
 * Trade info (strategy, symbol, size, price) is stored per trade ID for
 * accurate cleanup on close.
 */
public final class PortfolioEngine {

    private static final Logger log = LoggerFactory.getLogger(PortfolioEngine.class);

    /** Default capital allocated per strategy (in paisa) when no config override exists. */
    public static final long DEFAULT_CAPITAL_PER_STRATEGY_PAISA = 1_000_000L; // ₹10,000

    /** Default maximum net exposure per symbol (in paisa) when no config override exists. */
    public static final long DEFAULT_MAX_NET_EXPOSURE_PAISA = 5_000_000L; // ₹50,000

    /** Attribute key used to extract the strategy name from a SignalGenerated. */
    public static final String ATTR_STRATEGY_NAME = "strategyName";

    /** Attribute key used to extract the trade quantity from a SignalGenerated. */
    private static final String ATTR_QUANTITY = "quantity";

    // Per-strategy capital tracking: strategy name → allocation state
    private final ConcurrentHashMap<String, StrategyAllocation> allocations = new ConcurrentHashMap<>();

    // Per-symbol net position across ALL strategies: symbol → net quantity
    // Positive = net long, negative = net short
    private final ConcurrentHashMap<String, Long> netPositions = new ConcurrentHashMap<>();

    // Signal → strategy attribution: signalId → strategyName
    private final ConcurrentHashMap<String, String> signalToStrategy = new ConcurrentHashMap<>();

    // Signal → estimated capital: signalId → capital reserved (for TradeOpened adjustment)
    private final ConcurrentHashMap<String, Long> signalToEstimatedCapital = new ConcurrentHashMap<>();

    // Signal → net delta: signalId → signed net quantity delta (for net position correction)
    private final ConcurrentHashMap<String, Long> signalDeltas = new ConcurrentHashMap<>();

    // Order → signal attribution: internal orderId → signalId (PE-02 fix: decouples from correlationId)
    private final ConcurrentHashMap<String, String> orderIdToSignalId = new ConcurrentHashMap<>();

    // Trade → trade info: tradeId → TradeInfo (for TradeClosed cleanup)
    private final ConcurrentHashMap<String, TradeInfo> openTrades = new ConcurrentHashMap<>();

    private final long defaultCapitalPaisa;
    private final long maxNetExposurePaisa;

    /**
     * Creates a PortfolioEngine with the given defaults.
     *
     * @param defaultCapitalPaisa default capital allocated per strategy
     * @param maxNetExposurePaisa maximum notional exposure per symbol (paisa)
     */
    public PortfolioEngine(long defaultCapitalPaisa, long maxNetExposurePaisa) {
        this.defaultCapitalPaisa = defaultCapitalPaisa;
        this.maxNetExposurePaisa = maxNetExposurePaisa;
    }

    /** Creates an engine with the default capital and exposure limits. */
    public PortfolioEngine() {
        this(DEFAULT_CAPITAL_PER_STRATEGY_PAISA, DEFAULT_MAX_NET_EXPOSURE_PAISA);
    }

    // ── External API ──

    /**
     * Processes a domain event through the portfolio engine.
     *
     * <ul>
     *   <li>{@link SignalGenerated} — checked against capital and exposure limits</li>
     *   <li>{@link SignalSuppressed} — frees reserved capital if the signal was previously approved</li>
     *   <li>{@link OrderRejected} — frees reserved capital if the order had a prior signal approval</li>
     *   <li>{@link TradeOpened} — adjusts reserves to actual fill values</li>
     *   <li>{@link TradeClosed} — frees capital and updates net position</li>
     *   <li>All other events pass through unchanged</li>
     * </ul>
     */
    public void onDomainEvent(DomainEvent event, Consumer<DomainEvent> downstream) {
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

    // ── Portfolio state queries (for metrics / admin) ──

    /** Returns the used capital for the given strategy name, or 0 if unknown. */
    public long usedCapitalPaisa(String strategyName) {
        StrategyAllocation alloc = allocations.get(strategyName);
        return alloc != null ? alloc.usedCapitalPaisa() : 0L;
    }

    /** Returns the allocated capital limit for the given strategy name. */
    public long allocatedCapitalPaisa(String strategyName) {
        StrategyAllocation alloc = allocations.get(strategyName);
        return alloc != null ? alloc.allocatedCapitalPaisa() : defaultCapitalPaisa;
    }

    /** Returns the net position (signed quantity) for the given symbol. */
    public long netPosition(String symbol) {
        return netPositions.getOrDefault(symbol, 0L);
    }

    /** Returns an unmodifiable snapshot of all per-symbol net positions. */
    public Map<String, Long> netPositionsSnapshot() {
        return Collections.unmodifiableMap(new ConcurrentHashMap<>(netPositions));
    }

    /** Returns an unmodifiable snapshot of all per-strategy allocations. */
    public Map<String, StrategyAllocation> allocationsSnapshot() {
        return Collections.unmodifiableMap(new ConcurrentHashMap<>(allocations));
    }

    /** Resets all portfolio state. Useful for testing and daily reset. */
    public void reset() {
        allocations.clear();
        netPositions.clear();
        signalToStrategy.clear();
        signalToEstimatedCapital.clear();
        signalDeltas.clear();
        orderIdToSignalId.clear();
        openTrades.clear();
    }

    // ── Replay state isolation (AD-02) ──

    /**
     * Captures a snapshot of all mutable portfolio state for replay state isolation.
     */
    public StateSnapshot snapshot() {
        return new StateSnapshot(
                new ConcurrentHashMap<>(allocations),
                new ConcurrentHashMap<>(netPositions),
                new ConcurrentHashMap<>(signalToStrategy),
                new ConcurrentHashMap<>(signalToEstimatedCapital),
                new ConcurrentHashMap<>(signalDeltas),
                new ConcurrentHashMap<>(orderIdToSignalId),
                new ConcurrentHashMap<>(openTrades)
        );
    }

    /** Restores portfolio state from a previously captured snapshot. */
    public void restore(StateSnapshot snapshot) {
        allocations.clear();
        allocations.putAll(snapshot.allocations());
        netPositions.clear();
        netPositions.putAll(snapshot.netPositions());
        signalToStrategy.clear();
        signalToStrategy.putAll(snapshot.signalToStrategy());
        signalToEstimatedCapital.clear();
        signalToEstimatedCapital.putAll(snapshot.signalToEstimatedCapital());
        signalDeltas.clear();
        signalDeltas.putAll(snapshot.signalDeltas());
        orderIdToSignalId.clear();
        orderIdToSignalId.putAll(snapshot.orderIdToSignalId());
        openTrades.clear();
        openTrades.putAll(snapshot.openTrades());
    }

    /** Immutable snapshot of all mutable portfolio state for replay isolation. */
    public record StateSnapshot(
            Map<String, StrategyAllocation> allocations,
            Map<String, Long> netPositions,
            Map<String, String> signalToStrategy,
            Map<String, Long> signalToEstimatedCapital,
            Map<String, Long> signalDeltas,
            Map<String, String> orderIdToSignalId,
            Map<String, TradeInfo> openTrades
    ) {}

    // ── Signal filtering (pass-through) ──

    /**
     * Passes signals through without filtering — portfolio-level checks have
     * been moved to {@link com.tradej.execution.risk.PositionRiskHandler}.
     * Capital reservation is done by {@link #reserveSignal(SignalGenerated)}
     * which is called by PositionRiskHandler after all risk checks pass.
     *
     * <p>Signals must still flow through this method so that PortfolioEngine's
     * downstream subscriber path works correctly when it's wired as a pipeline
     * filter. The actual portfolio check (reserveSignal) is called separately
     * by PositionRiskHandler before emission.
     */
    private void passThroughSignal(SignalGenerated signal, Consumer<DomainEvent> downstream) {
        downstream.accept(signal);
    }

    /**
     * Checks portfolio-level constraints (strategy capital limit and symbol
     * net exposure limit) for the given signal. If all checks pass, reserves
     * the estimated capital and updates the net position estimate.
     *
     * <p>Called by {@link com.tradej.execution.risk.PositionRiskHandler}
     * after its own risk checks pass, just before emitting
     * {@link com.tradej.core.domain.event.SignalPendingExecution}.
     *
     * @param signal the signal to reserve capital for
     * @return {@code null} if the signal was approved and capital reserved,
     *         or a rejection reason string if portfolio limits are exceeded
     */
    public String reserveSignal(SignalGenerated signal) {
        String strategyName = extractStrategyName(signal);
        long requiredCapital = requiredCapital(signal);
        long qty = extractQuantity(signal);
        long signalDelta = signal.side().isBuySide() ? qty : -qty;

        // 1. Check and update strategy capital limit atomically
        boolean[] approved = {false};
        allocations.compute(strategyName, (name, alloc) -> {
            StrategyAllocation a = alloc != null ? alloc
                    : new StrategyAllocation(defaultCapitalPaisa, 0L);
            long used = a.usedCapitalPaisa();
            if (used + requiredCapital > a.allocatedCapitalPaisa()) {
                return a; // unchanged — rejection handled after compute
            }
            approved[0] = true;
            return new StrategyAllocation(a.allocatedCapitalPaisa(), used + requiredCapital);
        });

        if (!approved[0]) {
            StrategyAllocation current = allocations.get(strategyName);
            long remaining = current != null
                    ? current.allocatedCapitalPaisa() - current.usedCapitalPaisa()
                    : defaultCapitalPaisa;
            log.warn("Strategy {} capital limit exceeded required={} remaining={} symbol={}",
                    strategyName, requiredCapital, remaining, signal.symbol());
            return "Strategy capital limit exceeded: " + requiredCapital
                    + " paisa required, " + Math.max(0, remaining) + " paisa remaining";
        }

        // 2. Check and update symbol net exposure limit atomically
        boolean[] exposureApproved = {false};
        long[] signalDeltaRef = {signalDelta};
        netPositions.compute(signal.symbol(), (symbol, current) -> {
            long cur = current != null ? current : 0L;
            long newNet = cur + signalDeltaRef[0];
            long newExposurePaisa = Math.abs(newNet) * signal.entryPricePaisa();
            if (newExposurePaisa > maxNetExposurePaisa) {
                return current; // unchanged — revert capital too
            }
            exposureApproved[0] = true;
            return newNet;
        });

        if (!exposureApproved[0]) {
            // Revert the capital allocation
            allocations.compute(strategyName, (name, alloc) -> {
                if (alloc == null) {
                    return alloc;
                }
                long freed = Math.max(0, alloc.usedCapitalPaisa() - requiredCapital);
                return new StrategyAllocation(alloc.allocatedCapitalPaisa(), freed);
            });
            long curNet = netPositions.getOrDefault(signal.symbol(), 0L);
            log.warn("Symbol {} net exposure limit exceeded currentNet={} symbol={}",
                    signal.symbol(), curNet, signal.symbol());
            return "Symbol net exposure limit exceeded: " + signal.symbol();
        }

        // 3. Store attribution for cleanup
        signalToStrategy.put(signal.signalId(), strategyName);
        signalToEstimatedCapital.put(signal.signalId(), requiredCapital);
        signalDeltas.put(signal.signalId(), signalDelta);

        log.info("Portfolio reserved capital for signal strategy={} symbol={} side={} qty={} price={}",
                strategyName, signal.symbol(), signal.side(), qty, signal.entryPricePaisa());
        return null;
    }

    // ── Signal / order lifecycle — free capital when rejected or suppressed ──

    /**
     * Frees the capital and reverses the net position estimate that was reserved
     * for a signal (by {@link #passThroughSignal}) when that signal is suppressed or
     * its order is rejected before a trade is opened.
     *
     * <p>This is a no-op if the signalId is not tracked (unknown signal or
     * already consumed by TradeOpened).
     *
     * @param signalId the signal whose reserved capital to free
     * @param symbol   the symbol for net position reversal
     * @return the strategy name if the signal was tracked, null otherwise
     */
    private String freeSignalCapital(String signalId, String symbol) {
        String strategyName = signalToStrategy.remove(signalId);
        Long estimatedCapital = signalToEstimatedCapital.remove(signalId);
        Long signalDelta = signalDeltas.remove(signalId);

        if (strategyName != null && estimatedCapital != null && estimatedCapital > 0L) {
            allocations.compute(strategyName, (name, alloc) -> {
                if (alloc == null) {
                    return alloc;
                }
                long freed = Math.max(0L, alloc.usedCapitalPaisa() - estimatedCapital);
                return new StrategyAllocation(alloc.allocatedCapitalPaisa(), freed);
            });
        }
        if (signalDelta != null) {
            netPositions.merge(symbol, -signalDelta, Long::sum);
        }
        return strategyName;
    }

    private void onSignalSuppressed(SignalSuppressed suppressed, Consumer<DomainEvent> downstream) {
        // freeSignalCapital is a no-op if the signalId isn't tracked
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
        if (signalId != null && !signalId.isBlank() && orderId != null && !orderId.isBlank()) {
            orderIdToSignalId.put(orderId, signalId);
        }
        downstream.accept(accepted);
    }

    private void onOrderRejected(OrderRejected rejected, Consumer<DomainEvent> downstream) {
        String orderId = rejected.order().orderId();
        String signalId = orderIdToSignalId.get(orderId);
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
        // 1. Look up strategy attribution from the signal that produced this trade
        String signalId = trade.signalId();
        String strategyName = signalToStrategy.remove(signalId);
        if (strategyName == null) {
            strategyName = "default";
            log.warn("Unknown signalId={} for tradeId={} — attributing to 'default' strategy",
                    signalId, trade.tradeId());
        }

        // 2. Remove the estimated capital that was reserved at signal time
        Long removedEstimate = signalToEstimatedCapital.remove(signalId);
        final long estimatedCapital = removedEstimate == null ? 0L : removedEstimate;

        // 3. Compute actual capital from the fill
        long actualCapital = trade.size() * trade.entryPricePaisa();

        // 4. Adjust per-strategy used capital: remove estimate, add actual
        allocations.compute(strategyName, (name, alloc) -> {
            if (alloc == null) {
                return new StrategyAllocation(defaultCapitalPaisa, actualCapital);
            }
            long adjusted = alloc.usedCapitalPaisa() - estimatedCapital + actualCapital;
            return new StrategyAllocation(alloc.allocatedCapitalPaisa(), Math.max(0L, adjusted));
        });

        // 5. Adjust net position: remove signal estimate, add actual fill delta
        Long signalDelta = signalDeltas.remove(signalId);
        long actualTradeDelta = trade.side().isBuySide() ? trade.size() : -trade.size();
        if (signalDelta != null) {
            netPositions.merge(trade.symbol(), -signalDelta + actualTradeDelta, Long::sum);
        } else {
            log.warn("No signal delta found for signalId={} — merging actual delta only", signalId);
            netPositions.merge(trade.symbol(), actualTradeDelta, Long::sum);
        }

        // 6. Store trade info for TradeClosed cleanup
        openTrades.put(trade.tradeId(), new TradeInfo(
                strategyName, trade.symbol(), trade.side(), trade.size(), trade.entryPricePaisa()
        ));

        log.info("Portfolio committed trade strategy={} symbol={} side={} size={} price={}",
                strategyName, trade.symbol(), trade.side(), trade.size(), trade.entryPricePaisa());
        downstream.accept(trade);
    }

    private void onTradeClosed(TradeClosed trade, Consumer<DomainEvent> downstream) {
        // 1. Look up trade info
        TradeInfo info = openTrades.remove(trade.tradeId());
        if (info == null) {
            log.warn("Unknown tradeId={} on close — cannot free capital", trade.tradeId());
            downstream.accept(trade);
            return;
        }

        // 2. Free per-strategy used capital
        allocations.compute(info.strategyName(), (name, alloc) -> {
            if (alloc == null) {
                return new StrategyAllocation(defaultCapitalPaisa, 0L);
            }
            long freed = Math.max(0L, alloc.usedCapitalPaisa() - info.capitalPaisa());
            return new StrategyAllocation(alloc.allocatedCapitalPaisa(), freed);
        });

        // 3. Reverse net position
        netPositions.merge(info.symbol(), -info.netDelta(), Long::sum);

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

    /**
     * Extracts the trade quantity from a SignalGenerated's attributes map.
     * Returns 0 if the quantity attribute is missing or invalid.
     *
     * <p>This mirrors the pattern used by {@link com.tradej.execution.risk.PositionRiskHandler}
     * which also reads quantity from {@code attributes.get("quantity")}.
     */
    static long extractQuantity(SignalGenerated signal) {
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

    /**
     * Immutable snapshot of a strategy's capital allocation state.
     *
     * @param allocatedCapitalPaisa total capital allocated to this strategy
     * @param usedCapitalPaisa      capital currently used by open trades and pending signals
     */
    public record StrategyAllocation(long allocatedCapitalPaisa, long usedCapitalPaisa) {
    }

    /**
     * Information about an open trade, stored for cleanup on TradeClosed.
     */
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
