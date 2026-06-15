package com.tradej.simulation;

import com.tradej.core.domain.id.IdGenerator;
import com.tradej.core.domain.id.UuidIdGenerator;
import com.tradej.core.domain.instrument.ContractSymbolNormalizer;
import com.tradej.core.domain.model.OrderRequest;

import java.util.Objects;

/**
 * Service that routes simulated order placement through the in-process
 * {@link MatchingEngine} and records fills in {@link PnLLedger}.
 * Used in REPLAY and BACKTEST runtime modes.
 */
public final class SimulatedOrderService {

    private final MatchingEngine matchingEngine;
    private final PnLLedger pnlLedger;
    private final IdGenerator idGenerator;

    public SimulatedOrderService(MatchingEngine matchingEngine, PnLLedger pnlLedger) {
        this(matchingEngine, pnlLedger, new UuidIdGenerator());
    }

    public SimulatedOrderService(MatchingEngine matchingEngine, PnLLedger pnlLedger, IdGenerator idGenerator) {
        this.matchingEngine = matchingEngine;
        this.pnlLedger = pnlLedger;
        this.idGenerator = Objects.requireNonNullElse(idGenerator, new UuidIdGenerator());
    }

    /**
     * Place a simulated order. Normalizes the symbol, generates a simulated order ID,
     * matches via the matching engine, and records fills in the P&L ledger.
     *
     * @param request the order request
     * @return the match result with order and fills
     */
    public MatchingEngine.MatchResult placeOrder(OrderRequest request) {
        String canonicalSymbol = ContractSymbolNormalizer.normalize(request.symbol());
        OrderRequest normalizedRequest = new OrderRequest(
                canonicalSymbol,
                request.exchangeSegment(),
                request.side(),
                request.quantity(),
                request.orderType(),
                request.pricePaisa(),
                request.triggerPricePaisa(),
                request.productType(),
                request.validity(),
                request.correlationId()
        );
        String orderId = idGenerator.generateOrderId();
        MatchingEngine.MatchResult result = matchingEngine.match(normalizedRequest, orderId);

        if (!result.rejected()) {
            result.fills().forEach(fill ->
                    pnlLedger.applyFill(fill, fill.pricePaisa()));
        }

        return result;
    }

    public MatchingEngine matchingEngine() {
        return matchingEngine;
    }

    public PnLLedger pnlLedger() {
        return pnlLedger;
    }
}
