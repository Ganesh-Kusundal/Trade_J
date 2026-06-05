package com.tradej.cli.command;

import com.tradej.cli.CliContext;
import com.tradej.cli.config.CliConfig;
import com.tradej.cli.output.OutputFormatter;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;

import java.util.Map;

public final class CliTradingCommands extends CliCommandSupport {

    public CliTradingCommands(CliContext context, OutputFormatter out) {
        super(context, out);
    }

    public void placeSandboxOrder(
            String symbol,
            String segmentName,
            String side,
            long quantity,
            String orderType,
            long pricePaisa,
            String productType
    ) {
        if (context().profile() != CliConfig.Profile.SANDBOX) {
            throw new IllegalStateException("place is allowed only with --profile sandbox");
        }
        OrderRequest request = new OrderRequest(
                symbol,
                parseSegment(segmentName),
                Side.valueOf(side.toUpperCase()),
                quantity,
                OrderType.valueOf(orderType.toUpperCase()),
                pricePaisa,
                0L,
                ProductType.valueOf(productType.toUpperCase()),
                Validity.DAY,
                "cli-" + System.currentTimeMillis()
        );
        if (!context().yes()) {
            confirmOrAbort("Place sandbox order: " + request);
        }
        Order placed = orderCommand().placeOrder(request);
        out().print(placed);
    }

    public void cancelOrder(String orderId) {
        if (context().profile() != CliConfig.Profile.SANDBOX) {
            throw new IllegalStateException("cancel is allowed only with --profile sandbox");
        }
        if (!context().yes()) {
            confirmOrAbort("Cancel order " + orderId);
        }
        boolean cancelled = orderCommand().cancelOrder(orderId);
        out().print(Map.of("orderId", orderId, "cancelled", cancelled));
    }

    public void modifyOrder(String orderId, long quantity, long pricePaisa) {
        if (context().profile() != CliConfig.Profile.SANDBOX) {
            throw new IllegalStateException("modify is allowed only with --profile sandbox");
        }
        if (!context().yes()) {
            confirmOrAbort("Modify order " + orderId);
        }
        Order modified = orderCommand().modifyOrder(new ModifyOrderRequest(
                orderId, quantity, pricePaisa, 0L, null, null));
        out().print(modified);
    }
}
