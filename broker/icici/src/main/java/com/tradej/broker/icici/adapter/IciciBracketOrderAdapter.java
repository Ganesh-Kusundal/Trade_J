package com.tradej.broker.icici.adapter;

import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;

import java.util.List;

/**
 * ICICI bracket order adapter.
 * 
 * <p>ICICI Breeze API does not natively support bracket orders (super orders).
 * This adapter implements the {@link BracketOrderProvider} interface to maintain
 * API consistency but throws {@link UnsupportedOperationException} for all operations.
 * 
 * <p><b>API Limitation</b>: ICICI Breeze does not provide endpoints for:
 * <ul>
 *   <li>Placing bracket/super orders</li>
 *   <li>Modifying bracket order legs</li>
 *   <li>Canceling bracket orders</li>
 *   <li>Listing bracket orders</li>
 * </ul>
 * 
 * <p><b>Workaround</b>: Users can manually implement bracket order logic by:
 * <ol>
 *   <li>Placing the main order via {@link com.tradej.broker.api.port.OrderCommand#placeOrder}</li>
 *   <li>Monitoring order status via {@link com.tradej.broker.api.port.OrderQuery}</li>
 *   <li>Placing target and stop-loss orders as separate limit orders when main order fills</li>
 * </ol>
 * 
 * @see <a href="https://api.icicidirect.com/breezeapi-documentation/">ICICI Breeze API Documentation</a>
 */
public final class IciciBracketOrderAdapter implements BracketOrderProvider {

    @Override
    public Order placeSuperOrder(OrderRequest request, long targetPricePaisa, long stopLossPricePaisa, long trailingJumpPaisa) {
        throw new UnsupportedOperationException("ICICI does not support bracket orders natively");
    }

    @Override
    public Order modifySuperOrder(String orderId, String legName, long quantity, long pricePaisa, long triggerPricePaisa) {
        throw new UnsupportedOperationException("ICICI does not support bracket orders natively");
    }

    @Override
    public boolean cancelSuperOrder(String orderId, String legName) {
        throw new UnsupportedOperationException("ICICI does not support bracket orders natively");
    }

    @Override
    public List<Order> getSuperOrders() {
        return List.of();
    }
}
