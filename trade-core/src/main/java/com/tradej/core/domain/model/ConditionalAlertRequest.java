package com.tradej.core.domain.model;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;

public record ConditionalAlertRequest(
        String symbol,
        ExchangeSegment exchangeSegment,
        Side side,
        long quantity,
        OrderType orderType,
        ProductType productType,
        long pricePaisa,
        long triggerPricePaisa,
        Validity validity,
        String comparisonType,
        String operator,
        String timeFrame,
        Double comparingValue,
        String indicatorName,
        String comparingIndicatorName,
        String frequency,
        String expiryDate,
        String userNote
) {
}
