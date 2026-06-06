package com.tradej.core.domain.model;

import com.tradej.core.domain.value.CorrelationId;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Symbol;
import com.tradej.core.domain.value.Validity;

public record OrderRequest(
        String symbol,
        ExchangeSegment exchangeSegment,
        Side side,
        long quantity,
        OrderType orderType,
        long pricePaisa,
        long triggerPricePaisa,
        ProductType productType,
        Validity validity,
        String correlationId
) {
    public Exchange exchange() {
        return exchangeSegment.exchange();
    }

    public Symbol symbolValue() {
        return new Symbol(symbol);
    }

    public CorrelationId correlationIdValue() {
        return correlationId != null && !correlationId.isBlank()
                ? new CorrelationId(correlationId) : null;
    }
}
