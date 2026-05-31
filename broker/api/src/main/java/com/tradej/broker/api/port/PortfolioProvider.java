package com.tradej.broker.api.port;

import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Position;

import java.util.List;

public interface PortfolioProvider {
    List<Position> getPositions();

    List<Holding> getHoldings();

    Balance getBalance();
}
