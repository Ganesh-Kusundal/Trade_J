package com.tradej.execution.risk;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.KillSwitchEngaged;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.event.TradeUpdated;
import com.tradej.core.domain.instrument.ContractSymbolMatcher;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import com.tradej.strategy.portfolio.PortfolioEngine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class PositionRiskHandler {
    private final InstrumentResolver instrumentResolver;
    private final RiskLimits riskLimits;
    private final PortfolioEngine portfolioEngine;
    private final Map<String, ManagedTrade> openTrades = new ConcurrentHashMap<>();
    private final AtomicBoolean killSwitch = new AtomicBoolean(false);
    private final AtomicLong realizedLossPaisa = new AtomicLong();
    private final AtomicInteger consecutiveLosses = new AtomicInteger();

    public PositionRiskHandler(InstrumentResolver instrumentResolver, RiskLimits riskLimits, PortfolioEngine portfolioEngine) {
        this.instrumentResolver = instrumentResolver;
        this.riskLimits = riskLimits;
        this.portfolioEngine = portfolioEngine;
    }

    /** @deprecated Only for backward-compatible test construction without portfolio checks. */
    @Deprecated
    public PositionRiskHandler(InstrumentResolver instrumentResolver, RiskLimits riskLimits) {
        this(instrumentResolver, riskLimits, new PortfolioEngine());
    }

    /** Reset daily loss limits and kill switch. Safe to call at any time. */
    public void resetDailyLimits() {
        realizedLossPaisa.set(0);
        consecutiveLosses.set(0);
        killSwitch.set(false);
    }

    public void onDomainEvent(DomainEvent event, Consumer<DomainEvent> downstream) {
        if (event instanceof MarketTickEvent tick) {
            handleMarketTick(tick, downstream);
            return;
        }
        if (event instanceof TickReceived tickReceived) {
            handleTick(tickReceived, downstream);
            return;
        }
        if (event instanceof SignalGenerated signalGenerated) {
            qualifySignal(signalGenerated, downstream);
            return;
        }
        if (event instanceof TradeOpened tradeOpened) {
            openTrades.put(tradeOpened.tradeId(), new ManagedTrade(
                    tradeOpened.tradeId(),
                    tradeOpened.symbol(),
                    tradeOpened.side(),
                    tradeOpened.size(),
                    tradeOpened.entryPricePaisa(),
                    tradeOpened.stopLossPaisa(),
                    tradeOpened.takeProfitPaisa()
            ));
            return;
        }
        if (event instanceof TradeClosed tradeClosed) {
            openTrades.remove(tradeClosed.tradeId());
            if (tradeClosed.realizedPnlPaisa() < 0) {
                consecutiveLosses.incrementAndGet();
                realizedLossPaisa.addAndGet(Math.abs(tradeClosed.realizedPnlPaisa()));
            } else {
                consecutiveLosses.set(0);
            }
        }
    }

    private void qualifySignal(SignalGenerated signal, Consumer<DomainEvent> downstream) {
        if (killSwitch.get()) {
            downstream.accept(new SignalSuppressed(EventMetadata.correlated(signal.signalId(), signal.sequenceId()), signal.signalId(), signal.symbol(), "Kill switch engaged", signal.attributes()));
            return;
        }
        if (openTrades.size() >= riskLimits.maxOpenPositions()) {
            downstream.accept(new SignalSuppressed(EventMetadata.correlated(signal.signalId(), signal.sequenceId()), signal.signalId(), signal.symbol(), "Max open positions reached", signal.attributes()));
            return;
        }
        if (realizedLossPaisa.get() >= riskLimits.maxDailyLossPaisa() || consecutiveLosses.get() >= riskLimits.maxConsecutiveLosses()) {
            killSwitch.set(true);
            downstream.accept(new KillSwitchEngaged(EventMetadata.correlated(signal.signalId(), signal.sequenceId()), "Risk limit breached", 0L, 0L, realizedLossPaisa.get(), consecutiveLosses.get()));
            downstream.accept(new SignalSuppressed(EventMetadata.correlated(signal.signalId(), signal.sequenceId()), signal.signalId(), signal.symbol(), "Risk limit breached", signal.attributes()));
            return;
        }

        Object quantityValue = signal.attributes().get("quantity");
        if (!(quantityValue instanceof Number quantityNumber) || quantityNumber.longValue() <= 0L) {
            downstream.accept(new SignalSuppressed(EventMetadata.correlated(signal.signalId(), signal.sequenceId()), signal.signalId(), signal.symbol(), "Signal missing explicit quantity", signal.attributes()));
            return;
        }

        // Portfolio-level constraints (Phase F.3)
        String portfolioRejection = portfolioEngine.reserveSignal(signal);
        if (portfolioRejection != null) {
            downstream.accept(new SignalSuppressed(EventMetadata.correlated(signal.signalId(), signal.sequenceId()), signal.signalId(), signal.symbol(), portfolioRejection, signal.attributes()));
            return;
        }

        long quantity = quantityNumber.longValue();
        List<Instrument> matches = instrumentResolver.allInstruments().stream()
                .filter(candidate -> ContractSymbolMatcher.matches(candidate, signal.symbol()))
                .toList();
        if (matches.isEmpty()) {
            downstream.accept(new SignalSuppressed(EventMetadata.correlated(signal.signalId(), signal.sequenceId()), signal.signalId(), signal.symbol(), "No instrument mapping loaded", signal.attributes()));
            return;
        }
        if (matches.size() > 1) {
            downstream.accept(new SignalSuppressed(EventMetadata.correlated(signal.signalId(), signal.sequenceId()), signal.signalId(), signal.symbol(), "Ambiguous instrument mapping across venues", signal.attributes()));
            return;
        }
        Instrument instrument = matches.getFirst();

        long notional = quantity * signal.entryPricePaisa();
        if (notional > riskLimits.maxOrderValuePaisa()) {
            downstream.accept(new SignalSuppressed(EventMetadata.correlated(signal.signalId(), signal.sequenceId()), signal.signalId(), signal.symbol(), "Order value exceeds risk limit", signal.attributes()));
            return;
        }

        OrderRequest orderRequest = new OrderRequest(
                instrument.canonicalSymbol(),
                instrument.exchangeSegment(),
                signal.side(),
                quantity,
                OrderType.LIMIT,
                signal.entryPricePaisa(),
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                signal.signalId()
        );
        downstream.accept(new SignalPendingExecution(EventMetadata.correlated(signal.signalId(), signal.sequenceId()), signal.signalId(), orderRequest, signal.attributes()));
    }

    private void handleMarketTick(MarketTickEvent tick, Consumer<DomainEvent> downstream) {
        openTrades.values().stream()
                .filter(trade -> trade.symbol().equalsIgnoreCase(tick.symbol()))
                .forEach(trade -> {
                    long pnl = trade.side() == Side.BUY
                            ? (tick.ltpPaisa() - trade.entryPricePaisa()) * trade.quantity()
                            : (trade.entryPricePaisa() - tick.ltpPaisa()) * trade.quantity();
                    downstream.accept(new TradeUpdated(
                            EventMetadata.correlated(trade.tradeId(), tick.sequenceId()),
                            trade.tradeId(),
                            trade.symbol(),
                            tick.ltpPaisa(),
                            pnl,
                            trade.stopLossPaisa()
                    ));
                });
    }

    private void handleTick(TickReceived tickReceived, Consumer<DomainEvent> downstream) {
        openTrades.values().stream()
                .filter(trade -> trade.symbol().equalsIgnoreCase(tickReceived.symbol()))
                .forEach(trade -> {
                    long pnl = trade.side() == Side.BUY
                            ? (tickReceived.ltpPaisa() - trade.entryPricePaisa()) * trade.quantity()
                            : (trade.entryPricePaisa() - tickReceived.ltpPaisa()) * trade.quantity();
                    downstream.accept(new TradeUpdated(
                            EventMetadata.correlated(trade.tradeId(), tickReceived.sequenceId()),
                            trade.tradeId(),
                            trade.symbol(),
                            tickReceived.ltpPaisa(),
                            pnl,
                            trade.stopLossPaisa()
                    ));
                });
    }

    private record ManagedTrade(
            String tradeId,
            String symbol,
            Side side,
            long quantity,
            long entryPricePaisa,
            long stopLossPaisa,
            long takeProfitPaisa
    ) {
    }
}
