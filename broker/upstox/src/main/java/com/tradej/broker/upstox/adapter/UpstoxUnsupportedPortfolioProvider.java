package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Position;

import java.util.List;

final class UpstoxUnsupportedPortfolioProvider extends UpstoxUnsupportedPort implements PortfolioProvider {
    @Override
    public List<Position> getPositions() {
        throw unsupported("PortfolioProvider");
    }

    @Override
    public List<Holding> getHoldings() {
        throw unsupported("PortfolioProvider");
    }

    @Override
    public Balance getBalance() {
        throw unsupported("PortfolioProvider");
    }
}
