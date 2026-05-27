package com.tradej.core.domain.model;

import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.Validity;

public record ModifyOrderRequest(
        String orderId,
        Long quantity,
        Long pricePaisa,
        Long triggerPricePaisa,
        OrderType orderType,
        Validity validity
) {
}
