package com.tradej.brokergateway.simulation;

import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Position;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulated portfolio provider for paper trading.
 * Tracks balance, positions, and holdings in memory.
 */
public final class SimulationPortfolioProvider implements PortfolioProvider {

    private final AtomicLong cashPaisa;
    private final CopyOnWriteArrayList<Position> positions = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Holding> holdings = new CopyOnWriteArrayList<>();

    public SimulationPortfolioProvider() {
        this(100_000_00L); // 1 lakh default
    }

    public SimulationPortfolioProvider(long initialCashPaisa) {
        this.cashPaisa = new AtomicLong(initialCashPaisa);
    }

    @Override
    public Balance getBalance() {
        long cash = cashPaisa.get();
        return new Balance("PAPER", cash, 0L, 0L, 0L, cash);
    }

    @Override
    public List<Position> getPositions() { return List.copyOf(positions); }

    @Override
    public List<Holding> getHoldings() { return List.copyOf(holdings); }

    // Mutation methods for simulation
    public void addPosition(Position position) { positions.add(position); }
    public void removePosition(String symbol) { positions.removeIf(p -> p.symbol().equals(symbol)); }
    public void addHolding(Holding holding) { holdings.add(holding); }
    public void debitCash(long amountPaisa) { cashPaisa.addAndGet(-amountPaisa); }
    public void creditCash(long amountPaisa) { cashPaisa.addAndGet(amountPaisa); }
    public long cashPaisa() { return cashPaisa.get(); }
}
