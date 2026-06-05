package com.tradej.broker.dhan.mapper;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;

public final class DhanApiConverters {
    private DhanApiConverters() {
    }

    public static String segment(ExchangeSegment value) {
        return value.name();
    }

    public static String transactionType(Side side) {
        return side == Side.SELL ? "SELL" : "BUY";
    }

    public static String productType(ProductType productType) {
        return productType.name();
    }

    public static String orderType(OrderType orderType) {
        return orderType.name();
    }

    public static String validity(Validity validity) {
        return validity.name();
    }

    public static String killSwitch(boolean enabled) {
        return enabled ? "ACTIVATE" : "DEACTIVATE";
    }
}
