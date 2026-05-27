package com.tradej.broker.dhan.mapper;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import io.github.sonicalgo.dhan.common.TransactionType;
import io.github.sonicalgo.dhan.usecase.KillSwitchStatus;

public final class DhanSdkConverters {
    private DhanSdkConverters() {
    }

    public static io.github.sonicalgo.dhan.common.ExchangeSegment segment(ExchangeSegment value) {
        return io.github.sonicalgo.dhan.common.ExchangeSegment.valueOf(value.name());
    }

    public static TransactionType transactionType(Side side) {
        return side == Side.SELL ? TransactionType.SELL : TransactionType.BUY;
    }

    public static io.github.sonicalgo.dhan.common.ProductType productType(ProductType productType) {
        return io.github.sonicalgo.dhan.common.ProductType.valueOf(productType.name());
    }

    public static io.github.sonicalgo.dhan.common.OrderType orderType(OrderType orderType) {
        return io.github.sonicalgo.dhan.common.OrderType.valueOf(orderType.name());
    }

    public static io.github.sonicalgo.dhan.common.Validity validity(Validity validity) {
        return io.github.sonicalgo.dhan.common.Validity.valueOf(validity.name());
    }

    public static KillSwitchStatus killSwitch(boolean enabled) {
        return enabled ? KillSwitchStatus.ACTIVATE : KillSwitchStatus.DEACTIVATE;
    }
}
