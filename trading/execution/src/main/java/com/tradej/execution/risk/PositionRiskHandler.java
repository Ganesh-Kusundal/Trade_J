package com.tradej.execution.risk;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.DomainEventVisitor;
import com.tradej.core.domain.event.ReconciliationHaltRequired;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.core.domain.service.PositionService;
import com.tradej.core.domain.value.Side;
import com.tradej.execution.bridge.SignalExecutionBridge;
import com.tradej.strategy.portfolio.PortfolioEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Enforces pre-trade risk checks and kill-switch conditions using a
 * {@link NetPositionProvider}, then qualifies {@link SignalGenerated} events into
 * {@link SignalPendingExecution} for the OMS stage.
 */
public final class PositionRiskHandler implements DomainEventVisitor {

    private static final Logger log = LoggerFactory.getLogger(PositionRiskHandler.class);

    private final RiskLimits riskLimits;
    private final NetPositionProvider netPositionProvider;
    private final PortfolioEngine portfolioEngine;
    private final MarginEnforcementHandler marginEnforcement;
    private final KillSwitchCoordinator killSwitchCoordinator;
    private final RiskCheckChain riskCheckChain;

    private final AtomicLong realizedLossPaisa = new AtomicLong();
    private final AtomicLong unrealizedLossPaisa = new AtomicLong();
    private final AtomicInteger consecutiveLosses = new AtomicInteger();
    private final AtomicInteger openTrades = new AtomicInteger();
    private final Set<String> symbolsWithOpenPosition = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean killSwitch = new AtomicBoolean(false);
    private final AtomicBoolean reconciliationHalt = new AtomicBoolean(false);
    private volatile StateSnapshot snapshot;
    private final ThreadLocal<Consumer<DomainEvent>> currentPublisher = new ThreadLocal<>();

    public PositionRiskHandler(RiskLimits limits, NetPositionProvider netPositionProvider) {
        this(limits, netPositionProvider, null, null, null);
    }

    public PositionRiskHandler(
            RiskLimits limits,
            NetPositionProvider netPositionProvider,
            PortfolioEngine portfolioEngine
    ) {
        this(limits, netPositionProvider, portfolioEngine, null, null);
    }

    /**
     * P3.3: primary constructor that takes the canonical {@link PositionService}
     * (the event-sourced position source from {@code FullComposition}). The
     * {@code PositionService} is also a {@link NetPositionProvider} so all existing
     * risk-check code continues to work unchanged.
     */
    public PositionRiskHandler(
            RiskLimits limits,
            PositionService positionService,
            PortfolioEngine portfolioEngine,
            MarginEnforcementHandler marginEnforcement,
            KillSwitchCoordinator killSwitchCoordinator
    ) {
        this.riskLimits = Objects.requireNonNull(limits, "limits");
        this.netPositionProvider = Objects.requireNonNull(positionService, "positionService");
        this.portfolioEngine = portfolioEngine;
        this.marginEnforcement = marginEnforcement;
        this.killSwitchCoordinator = killSwitchCoordinator;
        this.riskCheckChain = new RiskCheckChain(java.util.List.of(
                new KillSwitchRiskCheck(),
                new DailyLossRiskCheck(),
                new PositionLimitRiskCheck()
        ));
    }

    public PositionRiskHandler(
            RiskLimits limits,
            NetPositionProvider netPositionProvider,
            PortfolioEngine portfolioEngine,
            MarginEnforcementHandler marginEnforcement,
            KillSwitchCoordinator killSwitchCoordinator
    ) {
        this.riskLimits = Objects.requireNonNull(limits, "limits");
        this.netPositionProvider = Objects.requireNonNull(netPositionProvider, "netPositionProvider");
        this.portfolioEngine = portfolioEngine;
        this.marginEnforcement = marginEnforcement;
        this.killSwitchCoordinator = killSwitchCoordinator;
        this.riskCheckChain = new RiskCheckChain(java.util.List.of(
                new KillSwitchRiskCheck(),
                new DailyLossRiskCheck(),
                new PositionLimitRiskCheck()
        ));
    }

    /**
     * Creates a new builder for {@link PositionRiskHandler}. Replaces constructor
     * telescoping — required dependencies are non-null, optional ones are explicit.
     */
    public static Builder builder(RiskLimits limits, NetPositionProvider netPositionProvider) {
        return new Builder(limits, netPositionProvider);
    }

    /**
     * Fluent builder for {@link PositionRiskHandler}.
     */
    public static final class Builder {
        private final RiskLimits limits;
        private final NetPositionProvider netPositionProvider;
        private PortfolioEngine portfolioEngine;
        private MarginEnforcementHandler marginEnforcement;
        private KillSwitchCoordinator killSwitchCoordinator;

        private Builder(RiskLimits limits, NetPositionProvider netPositionProvider) {
            this.limits = Objects.requireNonNull(limits, "limits");
            this.netPositionProvider = Objects.requireNonNull(netPositionProvider, "netPositionProvider");
        }

        public Builder withPortfolioEngine(PortfolioEngine engine) {
            this.portfolioEngine = engine;
            return this;
        }

        public Builder withMarginEnforcement(MarginEnforcementHandler enforcement) {
            this.marginEnforcement = enforcement;
            return this;
        }

        public Builder withKillSwitchCoordinator(KillSwitchCoordinator coordinator) {
            this.killSwitchCoordinator = coordinator;
            return this;
        }

        public PositionRiskHandler build() {
            return new PositionRiskHandler(limits, netPositionProvider, portfolioEngine,
                    marginEnforcement, killSwitchCoordinator);
        }
    }

    public void onDomainEvent(DomainEvent event, Consumer<DomainEvent> publisher) {
        currentPublisher.set(publisher);
        try {
            event.accept(this);
        } finally {
            currentPublisher.remove();
        }
    }

    @Override
    public void visit(TradeOpened event) {
        handleTradeOpened(event);
    }

    @Override
    public void visit(TradeClosed event) {
        handleTradeClosed(event);
    }

    @Override
    public void visit(SignalGenerated event) {
        handleSignalGenerated(event, currentPublisher.get());
    }

    @Override
    public void visit(SignalPendingExecution event) {
        handleSignalPending(event, currentPublisher.get());
    }

    @Override
    public void visit(ReconciliationHaltRequired event) {
        handleReconciliationHalt(event);
    }

    public void handleReconciliationHalt(ReconciliationHaltRequired halt) {
        reconciliationHalt.set(true);
        activateKillSwitch("reconciliation_mismatch:" + halt.symbol());
        log.error(
                "Reconciliation halt symbol={} expected={} broker={} mismatch={}",
                halt.symbol(),
                halt.expectedQuantity(),
                halt.brokerQuantity(),
                halt.mismatchQuantity());
    }

    public void acknowledgeReconciliationHalt() {
        reconciliationHalt.set(false);
        resetDailyLimits();
        if (killSwitchCoordinator != null) {
            killSwitchCoordinator.disengage();
        }
        log.info("Reconciliation halt acknowledged — trading resumed");
    }

    public boolean isReconciliationHaltActive() {
        return reconciliationHalt.get();
    }

    public void updateUnrealizedLoss(long lossPaisa) {
        unrealizedLossPaisa.set(Math.max(0L, lossPaisa));
    }

    public void checkCombinedLossLimit(long maxDailyLossPaisa) {
        long total = realizedLossPaisa.get() + unrealizedLossPaisa.get();
        if (total >= maxDailyLossPaisa) {
            log.warn("Combined loss limit breached realized={} unrealized={} max={}",
                    realizedLossPaisa.get(), unrealizedLossPaisa.get(), maxDailyLossPaisa);
            activateKillSwitch("combined_loss_mtm");
        }
    }

    private void handleSignalGenerated(SignalGenerated signal, java.util.function.Consumer<DomainEvent> publisher) {
        SignalExecutionBridge.toPending(signal).ifPresentOrElse(
                pending -> handleSignalPending(pending, publisher, signal),
                () -> rejectRawSignal(signal, publisher, "invalid_signal_quantity"));
    }

    private void rejectRawSignal(
            SignalGenerated signal,
            java.util.function.Consumer<DomainEvent> publisher,
            String reason) {
        if (publisher == null) {
            return;
        }
        publisher.accept(new SignalSuppressed(
                signal.metadata(),
                signal.signalId(),
                signal.symbol(),
                reason,
                Map.copyOf(signal.attributes())));
    }

    private void handleTradeOpened(TradeOpened opened) {
        if (killSwitch.get()) {
            log.warn("Kill switch is active — ignoring TradeOpened symbol={}", opened.symbol());
            return;
        }
        openTrades.incrementAndGet();
        symbolsWithOpenPosition.add(opened.symbol());
    }

    private void handleTradeClosed(TradeClosed closed) {
        int trades = openTrades.updateAndGet(current -> current <= 0 ? 0 : current - 1);
        long realized = closed.realizedPnlPaisa();
        if (realized < 0) {
            realizedLossPaisa.addAndGet(-realized);
            int seq = consecutiveLosses.incrementAndGet();
            if (seq >= riskLimits.maxConsecutiveLosses()) {
                activateKillSwitch("consecutive_losses");
            }
        } else {
            consecutiveLosses.set(0);
        }
        if (realizedLossPaisa.get() >= riskLimits.maxDailyLossPaisa()) {
            activateKillSwitch("daily_loss");
        }
        if (netPositionProvider.getNetPosition(closed.symbol()) == 0) {
            symbolsWithOpenPosition.remove(closed.symbol());
        }
        if (trades == 0) {
            unrealizedLossPaisa.set(0);
        }
    }

    private void handleSignalPending(SignalPendingExecution pending, java.util.function.Consumer<DomainEvent> publisher) {
        handleSignalPending(pending, publisher, null);
    }

    private void handleSignalPending(
            SignalPendingExecution pending,
            java.util.function.Consumer<DomainEvent> publisher,
            SignalGenerated sourceSignal
    ) {
        OrderRequest order = pending.orderRequest();
        String symbol = order.symbol();

        // Build risk context from current handler state
        RiskContext riskContext = new RiskContext(
                symbol,
                realizedLossPaisa.get(),
                unrealizedLossPaisa.get(),
                openTrades.get(),
                killSwitch.get(),
                reconciliationHalt.get(),
                riskLimits.maxDailyLossPaisa(),
                riskLimits.maxOpenPositionQuantity()
        );

        // Run composable risk check chain (kill switch → daily loss → position limit)
        java.util.Optional<RiskVerdict> rejection = riskCheckChain.findRejection(riskContext);
        if (rejection.isPresent()) {
            rejectSignal(pending, publisher, rejection.get().checkName() + ":" + rejection.get().reason());
            return;
        }

        boolean isBuy = order.side() == Side.BUY;
        long currentPosition = netPositionProvider.getNetPosition(symbol);
        boolean wouldFlipPosition = (isBuy && currentPosition < 0) || (!isBuy && currentPosition > 0);

        if (wouldFlipPosition
                && Math.abs(currentPosition) + order.quantity() > riskLimits.maxOpenPositionQuantity()) {
            rejectSignal(pending, publisher, "max_order_value_breach");
            return;
        }
        if (Math.abs(order.quantity() * order.pricePaisa()) > riskLimits.maxOrderValuePaisa()) {
            rejectSignal(pending, publisher, "max_notional_value");
            return;
        }
        if (currentPosition == 0
                && symbolsWithOpenPosition.size() >= riskLimits.maxDistinctOpenPositions()
                && !symbolsWithOpenPosition.contains(symbol)) {
            rejectSignal(pending, publisher, "max_distinct_open_positions");
            return;
        }

        if (marginEnforcement != null) {
            var marginReason = marginEnforcement.checkMargin(order);
            if (marginReason.isPresent()) {
                rejectSignal(pending, publisher, marginReason.get());
                return;
            }
        }

        if (sourceSignal != null && portfolioEngine != null) {
            String portfolioReason = portfolioEngine.reserveSignal(sourceSignal);
            if (portfolioReason != null) {
                rejectSignal(pending, publisher, portfolioReason);
                return;
            }
        }

        if (publisher != null) {
            publisher.accept(pending);
        }
    }

    private void rejectSignal(
            SignalPendingExecution pending,
            java.util.function.Consumer<DomainEvent> publisher,
            String reason) {
        if (publisher != null) {
            publisher.accept(new SignalSuppressed(
                    pending.metadata(),
                    pending.signalId(),
                    pending.orderRequest().symbol(),
                    reason,
                    Map.of(
                            "symbol", pending.orderRequest().symbol(),
                            "netPosition", netPositionProvider.getNetPosition(pending.orderRequest().symbol()))));
        }
    }

    private void activateKillSwitch(String reason) {
        if (!killSwitch.compareAndSet(false, true)) {
            return; // already active — idempotent guard
        }
        log.error("Kill switch activated due to: {}", reason);
        if (killSwitchCoordinator != null) {
            killSwitchCoordinator.engage(reason);
        }
    }

    public void resetDailyLimits() {
        realizedLossPaisa.set(0);
        unrealizedLossPaisa.set(0);
        consecutiveLosses.set(0);
        killSwitch.set(false);
        reconciliationHalt.set(false);
        log.info("Daily risk limits reset");
    }

    public boolean isKillSwitchActive() { return killSwitch.get(); }
    public long getRealizedLossPaisa() { return realizedLossPaisa.get(); }
    public long getUnrealizedLossPaisa() { return unrealizedLossPaisa.get(); }
    public int getConsecutiveLosses() { return consecutiveLosses.get(); }
    public int getOpenTrades() { return openTrades.get(); }

    public StateSnapshot snapshot() {
        StateSnapshot current = snapshot;
        if (current != null) {
            return current;
        }
        current = new StateSnapshot(
                realizedLossPaisa.get(),
                unrealizedLossPaisa.get(),
                consecutiveLosses.get(),
                openTrades.get(),
                killSwitch.get(),
                reconciliationHalt.get(),
                Set.copyOf(symbolsWithOpenPosition));
        this.snapshot = current;
        return current;
    }

    public void restore(StateSnapshot state) {
        if (state == null) {
            return;
        }
        realizedLossPaisa.set(state.realizedLossPaisa());
        unrealizedLossPaisa.set(state.unrealizedLossPaisa());
        consecutiveLosses.set(state.consecutiveLosses());
        openTrades.set(state.openTrades());
        killSwitch.set(state.killSwitch());
        reconciliationHalt.set(state.reconciliationHalt());
        symbolsWithOpenPosition.clear();
        symbolsWithOpenPosition.addAll(state.symbolsWithOpenPosition());
        this.snapshot = null;
    }

    public record StateSnapshot(
            long realizedLossPaisa,
            long unrealizedLossPaisa,
            int consecutiveLosses,
            int openTrades,
            boolean killSwitch,
            boolean reconciliationHalt,
            Set<String> symbolsWithOpenPosition
    ) {
        public StateSnapshot(
                long realizedLossPaisa,
                long unrealizedLossPaisa,
                int consecutiveLosses,
                int openTrades,
                boolean killSwitch
        ) {
            this(realizedLossPaisa, unrealizedLossPaisa, consecutiveLosses, openTrades, killSwitch, false, Set.of());
        }
    }
}
