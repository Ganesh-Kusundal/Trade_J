package com.tradej.broker.api.port;

import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;

/**
 * Cover order — an intraday order with a mandatory stop-loss leg.
 * The stop-loss is placed automatically when the primary order is executed.
 *
 * <p>Supported by: Dhan (as "Super Order"), some other Indian brokers.
 * Not supported by: Upstox, ICICI.
 */
public interface CoverOrderProvider {

    /**
     * Place a cover order (primary + mandatory stop-loss).
     *
     * @param request the primary order request
     * @param slPaisa stop-loss trigger price in paisa
     * @return the placed order
     */
    Order placeCoverOrder(OrderRequest request, long slPaisa);

    /**
     * Exit (square off) a cover order.
     *
     * @param orderId the cover order ID to exit
     * @return the exit order
     */
    Order exitCoverOrder(String orderId);
}
