package com.tradej.execution.risk;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.value.Side;
import com.tradej.execution.bridge.SignalExecutionBridge;
import com.tradej.strategy.portfolio.PortfolioEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enforces pre-trade risk checks and kill-switch conditions using a
 * {@link NetPositionProvider}, then qualifies {@link SignalGenerated} events into
 * {@link SignalPendingExecution} for the OMS stage.
 *
 * <p>When a {@link PortfolioEngine} is configured, portfolio capital and exposure
 * limits are enforced via {@link PortfolioEngine#reserveSignal(SignalGenerated)}
 * before a signal is forwarded to execution.
 */
public final class PositionRiskHandler {

    private static final Logger log = LoggerFactory.getLogger(PositionRiskHandler.class);

    private final RiskLimits riskLimits;
    private final NetPositionProvider netPositionProvider;
    private final PortfolioEngine portfolioEngine;

    // Mutable risk state.
    private final AtomicLong realizedLossPaisa = new AtomicLong();
    private final AtomicLong unrealizedLossPaisa = new AtomicLong();
    private final AtomicInteger consecutiveLosses = new AtomicInteger();
    private final AtomicInteger openTrades = new AtomicInteger();
    private volatile boolean killSwitch = false;

    // Snapshot support for replay isolation (AD-02).
    private volatile StateSnapshot snapshot;

    public PositionRiskHandler(RiskLimits limits, NetPositionProvider netPositionProvider) {
        this(limits, netPositionProvider, null);
    }

    public PositionRiskHandler(
            RiskLimits limits,
            NetPositionProvider netPositionProvider,
            PortfolioEngine portfolioEngine
    ) {
        this.riskLimits = Objects.requireNonNull(limits, "limits");
        this.netPositionProvider = Objects.requireNonNull(netPositionProvider, "netPositionProvider");
        this.portfolioEngine = portfolioEngine;
    }

    public void onDomainEvent(DomainEvent event, java.util.function.Consumer<DomainEvent> publisher) {
        if (event instanceof TradeOpened opened) {
            handleTradeOpened(opened);
        } else if (event instanceof TradeClosed closed) {
            handleTradeClosed(closed);
        } else if (event instanceof SignalGenerated generated) {
            handleSignalGenerated(generated, publisher);
        } else if (event instanceof SignalPendingExecution pending) {
            handleSignalPending(pending, publisher);
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
        log.debug(
                "Trade opened symbol={} side={} size={} netPosition={}",
                opened.symbol(),
                opened.side(),
                opened.size(),
                netPositionProvider.getNetPositions().get(opened.symbol()));
    }

    private void handleTradeClosed(TradeClosed closed) {
        int trades = openTrades.updateAndGet(current -> {
            if (current <= 0) {
                log.warn("TradeClosed received with no open trades — ignoring symbol={}", closed.symbol());
                return 0;
            }
            return current - 1;
        });
        long realized = closed.realizedPnlPaisa();
        if (realized < 0) {
            long prev = realizedLossPaisa.addAndGet(-realized);
            int seq = consecutiveLosses.incrementAndGet();
            log.debug(
                    "Loss realized symbol={} lossPaisa={} cumulativeLossPaisa={} consecutiveLosses={}",
                    closed.symbol(),
                    realized,
                    -realized + prev,
                    seq);
            if (seq >= riskLimits.maxConsecutiveLosses()) {
                log.warn(
                        "Consecutive-loss threshold breached: {} >= {} — activating kill switch",
                        seq,
                        riskLimits.maxConsecutiveLosses());
                activateKillSwitch("consecutive_losses");
            }
        } else {
            consecutiveLosses.set(0);
        }
        long totalRealized = realizedLossPaisa.get();
        if (totalRealized >= riskLimits.maxDailyLossPaisa()) {
            log.warn(
                    "Daily loss limit breached: {} >= {} — activating kill switch",
                    totalRealized,
                    riskLimits.maxDailyLossPaisa());
            activateKillSwitch("daily_loss");
        }
        if (trades == 0) {
            unrealizedLossPaisa.set(0);
        }
        log.debug("Trade closed symbol={} pnlPaisa={} openTrades={}", closed.symbol(), realized, trades);
    }

    private void handleSignalPending(SignalPendingExecution pending, java.util.function.Consumer<DomainEvent> publisher) {
        handleSignalPending(pending, publisher, null);
    }

    private void handleSignalPending(
            SignalPendingExecution pending,
            java.util.function.Consumer<DomainEvent> publisher,
            SignalGenerated sourceSignal
    ) {
        if (killSwitch) {
            log.warn("Kill switch active — rejecting signal signalId={}", pending.signalId());
            rejectSignal(pending, publisher, "kill_switch_active");
            return;
        }
        OrderRequest order = pending.orderRequest();
        String symbol = order.symbol();
        boolean isBuy = order.side() == Side.BUY;
        long currentPosition = netPositionProvider.getNetPosition(symbol);
        boolean wouldFlipPosition = (isBuy && currentPosition < 0) || (!isBuy && currentPosition > 0);
        if (wouldFlipPosition
                && Math.abs(currentPosition) + order.quantity() > riskLimits.maxOrderValuePaisa()) {
            log.warn(
                    "Order would exceed max order value after position flip: symbol={} currentPosition={} orderQty={}",
                    symbol,
                    currentPosition,
                    order.quantity());
            rejectSignal(pending, publisher, "max_order_value_breach");
            return;
        }
        if (Math.abs(order.quantity() * order.pricePaisa()) > riskLimits.maxOrderValuePaisa()) {
            log.warn(
                    "Order value exceeds limit: symbol={} notionalPaisa={} max={}",
                    symbol,
                    Math.abs(order.quantity() * order.pricePaisa()),
                    riskLimits.maxOrderValuePaisa());
            rejectSignal(pending, publisher, "max_notional_value");
            return;
        }
        if (Math.abs(currentPosition) >= riskLimits.maxOpenPositions() && wouldFlipPosition) {
            log.warn(
                    "Open position limit already reached: symbol={} position={} max={}",
                    symbol,
                    currentPosition,
                    riskLimits.maxOpenPositions());
            rejectSignal(pending, publisher, "max_open_positions");
            return;
        }

        if (sourceSignal != null && portfolioEngine != null) {
            String portfolioReason = portfolioEngine.reserveSignal(sourceSignal);
            if (portfolioReason != null) {
                rejectSignal(pending, publisher, portfolioReason);
                return;
            }
        }

        log.debug(
                "Signal passed risk qualification signalId={} symbol={} position={}",
                pending.signalId(),
                symbol,
                currentPosition);
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
                            "symbol",
                            pending.orderRequest().symbol(),
                            "netPosition",
                            netPositionProvider.getNetPosition(pending.orderRequest().symbol()))));
        }
    }

    private void activateKillSwitch(String reason) {
        this.killSwitch = true;
        log.error("Kill switch activated due to: {}", reason);
    }

    public void resetDailyLimits() {
        realizedLossPaisa.set(0);
        unrealizedLossPaisa.set(0);
        consecutiveLosses.set(0);
        killSwitch = false;
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
                killSwitch);
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
        this.killSwitch = state.killSwitch();
        this.snapshot = null;
        log.debug("Risk state restored from snapshot");
    }

    public record StateSnapshot(
            long realizedLossPaisa,
            long unrealizedLossPaisa,
            int consecutiveLosses,
            int openTrades,
            boolean killSwitch) {
        public StateSnapshot {
            if (realizedLossPaisa < 0) {
                throw new IllegalArgumentException("realizedLossPaisa cannot be negative");
            }
            if (unrealizedLossPaisa < 0) {
                throw new IllegalArgumentException("unrealizedLossPaisa cannot be negative");
            }
            if (consecutiveLosses < 0) {
                throw new IllegalArgumentException("consecutiveLosses cannot be negative");
            }
            if (openTrades < 0) {
                throw new IllegalArgumentException("openTrades cannot be negative");
            }
        }
    }
}
