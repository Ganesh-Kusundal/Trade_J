package com.tradej.brokergateway;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;
import com.tradej.core.domain.model.Position;

import java.util.List;

/**
 * Handle for portfolio and margin operations.
 * Wraps {@link com.tradej.broker.api.port.PortfolioProvider} and
 * {@link com.tradej.broker.api.port.MarginProvider} with timing and result metadata.
 */
public final class PortfolioHandle extends BaseBrokerHandle {

    PortfolioHandle(BrokerSource source, IBrokerConnection connection) {
        super(source, connection);
    }

    public GatewayResult<Balance> balance() {
        return timed(() -> connection.portfolio().getBalance());
    }

    public GatewayResult<List<Position>> positions() {
        return timed(() -> connection.portfolio().getPositions());
    }

    public GatewayResult<List<Holding>> holdings() {
        return timed(() -> connection.portfolio().getHoldings());
    }

    public GatewayResult<MarginEstimate> estimateMargin(MarginEstimateRequest request) {
        return timed(() -> connection.margin().estimateMargin(request));
    }

    public GatewayResult<PortfolioSummary> portfolioSummary() {
        return timed(() -> {
            Balance bal = connection.portfolio().getBalance();
            List<Position> pos = connection.portfolio().getPositions();
            List<Holding> hold = connection.portfolio().getHoldings();
            return new PortfolioSummary(bal, pos, hold);
        });
    }

    public record PortfolioSummary(Balance balance, List<Position> positions, List<Holding> holdings) {
        public int positionCount() { return positions == null ? 0 : positions.size(); }
        public int holdingCount() { return holdings == null ? 0 : holdings.size(); }
        public long cashPaisa() { return balance != null ? balance.cashPaisa() : 0; }
    }
}
