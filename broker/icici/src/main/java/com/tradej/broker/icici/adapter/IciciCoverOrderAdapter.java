package com.tradej.broker.icici.adapter;

import com.tradej.broker.api.port.CoverOrderProvider;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;

/**
 * ICICI cover order adapter.
 * 
 * <p>ICICI Breeze API does not natively support cover orders.
 * This adapter implements the {@link CoverOrderProvider} interface to maintain
 * API consistency but throws {@link UnsupportedOperationException} for all operations.
 * 
 * <p><b>API Limitation</b>: ICICI Breeze does not provide endpoints for:
 * <ul>
 *   <li>Placing cover orders (order with mandatory stop-loss)</li>
 *   <li>Exiting cover orders separately from the main order</li>
 * </ul>
 * 
 * <p><b>Workaround</b>: Users can manually implement cover order logic by:
 * <ol>
 *   <li>Placing the main order via {@link com.tradej.broker.api.port.OrderCommand#placeOrder}</li>
 *   <li>Immediately placing a stop-loss order for the same quantity</li>
 *   <li>Managing both orders together in application logic</li>
 * </ol>
 * 
 * @see <a href="https://api.icicidirect.com/breezeapi-documentation/">ICICI Breeze API Documentation</a>
 */
public final class IciciCoverOrderAdapter implements CoverOrderProvider {

    @Override
    public Order placeCoverOrder(OrderRequest request, long slPaisa) {
        throw new UnsupportedOperationException("ICICI does not support cover orders");
    }

    @Override
    public Order exitCoverOrder(String orderId) {
        throw new UnsupportedOperationException("ICICI does not support cover orders");
    }
}
