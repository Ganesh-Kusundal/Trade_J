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

import java.util.List;
import java.util.Map;

/**
 * Standalone trading commands.
 *
 * <p>Supports both sandbox and live modes. Live mode requires explicit
 * confirmation ({\@code -y} or interactive YES) to prevent accidental orders.
 */
public final class CliTradingCommands extends CliCommandSupport {

    public CliTradingCommands(CliContext context, OutputFormatter out) {
        super(context, out);
    }

    /** Place an order via the broker session (sandbox or live). */
    public void placeOrder(
            String symbol,
            String segmentName,
            String side,
            long quantity,
            String orderType,
            long pricePaisa,
            long triggerPricePaisa,
            String productType,
            String validity
    ) {
        session().ensureCatalogLoaded();
        var request = new OrderRequest(
                symbol,
                parseSegment(segmentName),
                Side.valueOf(side.toUpperCase()),
                quantity,
                OrderType.valueOf(orderType.toUpperCase()),
                pricePaisa,
                triggerPricePaisa,
                ProductType.valueOf(productType.toUpperCase()),
                Validity.valueOf(validity.toUpperCase()),
                "cli-" + System.currentTimeMillis()
        );

        boolean isLive = context().profile() != CliConfig.Profile.SANDBOX;
        if (isLive) {
            out().println("⚠ LIVE ORDER — " + request);
            if (!context().yes()) {
                confirmOrAbort("Place LIVE order for " + request.quantity() + " " + request.symbol()
                        + " " + request.side() + " at " + request.pricePaisa());
            }
        } else if (!context().yes()) {
            confirmOrAbort("Place sandbox order: " + request);
        }
        Order placed = orderCommand().placeOrder(request);
        out().print(placed);
    }

    /** Cancel an order by ID (sandbox or live). */
    public void cancelOrder(String orderId) {
        boolean isLive = context().profile() != CliConfig.Profile.SANDBOX;
        if (isLive) {
            out().println("⚠ LIVE CANCEL — order " + orderId);
            if (!context().yes()) {
                confirmOrAbort("Cancel LIVE order " + orderId);
            }
        } else if (!context().yes()) {
            confirmOrAbort("Cancel order " + orderId);
        }
        boolean cancelled = orderCommand().cancelOrder(orderId);
        out().print(Map.of("orderId", orderId, "cancelled", cancelled));
    }

    /** Modify an order (sandbox or live). */
    public void modifyOrder(String orderId, long quantity, long pricePaisa) {
        boolean isLive = context().profile() != CliConfig.Profile.SANDBOX;
        if (isLive) {
            out().println("⚠ LIVE MODIFY — order " + orderId);
            if (!context().yes()) {
                confirmOrAbort("Modify LIVE order " + orderId);
            }
        } else if (!context().yes()) {
            confirmOrAbort("Modify order " + orderId);
        }
        Order modified = orderCommand().modifyOrder(new ModifyOrderRequest(
                orderId, null, null, quantity, pricePaisa, 0L, null, null));
        out().print(modified);
    }

    /** Get order book (open orders). */
    public void orderBook() {
        List<Order> orders = orderQuery().getOrderBook();
        if (context().json()) {
            out().print(orders);
            return;
        }
        if (orders.isEmpty()) {
            out().println("No open orders.");
            return;
        }
        List<String[]> rows = new java.util.ArrayList<>();
        for (Order o : orders) {
            rows.add(new String[]{
                    o.orderId(), o.symbol(), o.status().name(),
                    String.valueOf(o.quantity()), String.valueOf(o.pricePaisa())
            });
        }
        com.tradej.cli.output.TablePrinter.print(
                new String[]{"OrderId", "Symbol", "Status", "Qty", "Price"}, rows);
    }

    /** Get order status by ID. */
    public void orderStatus(String orderId) {
        Order order = orderQuery().getOrder(orderId);
        out().print(order);
    }

    /** Get trade book. */
    public void tradeBook() {
        var trades = orderQuery().getTradeBook();
        out().print(trades);
    }
}
