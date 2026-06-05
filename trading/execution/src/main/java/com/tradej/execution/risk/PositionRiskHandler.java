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
import com.tradej.core.domain.value.Side;
import com.tradej.execution.bridge.SignalExecutionBridge;
import com.tradej.strategy.portfolio.PortfolioEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    private final AtomicLong realizedLossPaisa = new AtomicLong();
    private final AtomicLong unrealizedLossPaisa = new AtomicLong();
    private final AtomicInteger consecutiveLosses = new AtomicInteger();
    private final AtomicInteger openTrades = new AtomicInteger();
    private final Set<String> symbolsWithOpenPosition = ConcurrentHashMap.newKeySet();
    private volatile boolean killSwitch = false;
    private volatile boolean reconciliationHalt = false;
    private volatile StateSnapshot snapshot;
    private Consumer<DomainEvent> currentPublisher;

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
    }

    public void onDomainEvent(DomainEvent event, Consumer<DomainEvent> publisher) {
        this.currentPublisher = publisher;
        try {
            event.accept(this);
        } finally {
            this.currentPublisher = null;
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
        handleSignalGenerated(event, currentPublisher);
    }

    @Override
    public void visit(SignalPendingExecution event) {
        handleSignalPending(event, currentPublisher);
    }

    @Override
    public void visit(ReconciliationHaltRequired event) {
        handleReconciliationHalt(event);
    }

    public void handleReconciliationHalt(ReconciliationHaltRequired halt) {
        reconciliationHalt = true;
        activateKillSwitch("reconciliation_mismatch:" + halt.symbol());
        log.error(
                "Reconciliation halt symbol={} expected={} broker={} mismatch={}",
                halt.symbol(),
                halt.expectedQuantity(),
                halt.brokerQuantity(),
                halt.mismatchQuantity());
    }

    public void acknowledgeReconciliationHalt() {
        reconciliationHalt = false;
        resetDailyLimits();
        if (killSwitchCoordinator != null) {
            killSwitchCoordinator.disengage();
        }
        log.info("Reconciliation halt acknowledged — trading resumed");
    }

    public boolean isReconciliationHaltActive() {
        return reconciliationHalt;
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
        if (killSwitch) {
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
        if (killSwitch || reconciliationHalt) {
            rejectSignal(pending, publisher, killSwitch ? "kill_switch_active" : "reconciliation_halt");
            return;
        }
        OrderRequest order = pending.orderRequest();
        String symbol = order.symbol();
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
        if (Math.abs(currentPosition) >= riskLimits.maxOpenPositionQuantity() && wouldFlipPosition) {
            rejectSignal(pending, publisher, "max_open_position_quantity");
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
        this.killSwitch = true;
        log.error("Kill switch activated due to: {}", reason);
        if (killSwitchCoordinator != null) {
            killSwitchCoordinator.engage(reason);
        }
    }

    public void resetDailyLimits() {
        realizedLossPaisa.set(0);
        unrealizedLossPaisa.set(0);
        consecutiveLosses.set(0);
        killSwitch = false;
        reconciliationHalt = false;
        log.info("Daily risk limits reset");
    }

    public boolean isKillSwitchActive() { return killSwitch; }
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
                killSwitch,
                reconciliationHalt,
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
        killSwitch = state.killSwitch();
        reconciliationHalt = state.reconciliationHalt();
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
