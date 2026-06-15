package com.tradej.pipeline.runtime;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TradeExecutionEvent;
import com.tradej.core.domain.id.IdGenerator;
import com.tradej.core.domain.id.UuidIdGenerator;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.OrderType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class DefaultBacktestFillModel implements BacktestFillModel {

    private final double slippagePct;
    private final long latencyMs;
    private final double fillRatio;
    private final IdGenerator idGenerator;

    public DefaultBacktestFillModel(double slippagePct, long latencyMs, double fillRatio) {
        this(slippagePct, latencyMs, fillRatio, new UuidIdGenerator());
    }

    public DefaultBacktestFillModel(double slippagePct, long latencyMs, double fillRatio, IdGenerator idGenerator) {
        this.slippagePct = slippagePct;
        this.latencyMs = latencyMs;
        this.fillRatio = fillRatio;
        this.idGenerator = Objects.requireNonNullElse(idGenerator, new UuidIdGenerator());
    }

    public DefaultBacktestFillModel() {
        this(0.05, 10, 1.0);
    }

    @Override
    public List<TradeExecutionEvent> fillOrder(OrderRequest request, DomainEvent trigger) {
        Objects.requireNonNull(request, "request must not be null");

        long fillQty = (long) (request.quantity() * fillRatio);
        if (fillQty <= 0) {
            return List.of();
        }

        long fillPrice = applySlippage(request.pricePaisa(), request.side());
        long exchangeTs = trigger != null ? trigger.timestampMs() : Instant.now().toEpochMilli();
        exchangeTs += latencyMs;

        TradeExecutionEvent fill = new TradeExecutionEvent(
                EventMetadata.correlated(request.correlationId(), 1L),
                idGenerator.generateFillId(),
                idGenerator.generateTradeId(),
                request.symbol(),
                request.exchangeSegment(),
                request.side(),
                fillQty,
                fillPrice,
                exchangeTs
        );

        return List.of(fill);
    }

    private long applySlippage(long pricePaisa, com.tradej.core.domain.value.Side side) {
        if (pricePaisa <= 0) return pricePaisa;
        long slip = (long) (pricePaisa * slippagePct / 100.0);
        if (slip < 1) slip = 1;
        return switch (side) {
            case BUY, LONG -> pricePaisa + slip;
            case SELL, SHORT -> pricePaisa - slip;
            case UNKNOWN -> pricePaisa;
        };
    }
}