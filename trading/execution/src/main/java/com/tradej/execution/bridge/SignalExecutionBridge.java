package com.tradej.execution.bridge;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import com.tradej.strategy.portfolio.PortfolioEngine;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Converts {@link SignalGenerated} strategy output into an executable
 * {@link SignalPendingExecution} with a normalized {@link OrderRequest}.
 */
public final class SignalExecutionBridge {

    private static final String ATTR_EXCHANGE_SEGMENT = "exchangeSegment";

    private SignalExecutionBridge() {
    }

    public static Optional<SignalPendingExecution> toPending(SignalGenerated signal) {
        long quantity = PortfolioEngine.extractQuantity(signal);
        if (quantity <= 0) {
            return Optional.empty();
        }
        OrderRequest orderRequest = new OrderRequest(
                signal.symbol(),
                resolveExchangeSegment(signal),
                signal.side(),
                quantity,
                OrderType.LIMIT,
                signal.entryPricePaisa(),
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                signal.signalId()
        );
        Map<String, Object> context = new HashMap<>(signal.attributes());
        context.putIfAbsent("setup", signal.setup());
        context.putIfAbsent("interval", signal.interval());
        return Optional.of(new SignalPendingExecution(
                EventMetadata.correlated(signal.signalId(), signal.sequenceId()),
                signal.signalId(),
                orderRequest,
                Map.copyOf(context)
        ));
    }

    private static ExchangeSegment resolveExchangeSegment(SignalGenerated signal) {
        Object raw = signal.attributes().get(ATTR_EXCHANGE_SEGMENT);
        if (raw instanceof ExchangeSegment segment) {
            return segment;
        }
        if (raw instanceof String name && !name.isBlank()) {
            try {
                return ExchangeSegment.valueOf(name.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return ExchangeSegment.NSE_EQ;
    }
}
