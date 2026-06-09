package com.tradej.broker.icici.adapter;

import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;

import java.util.List;

/**
 * ICICI GTT (Good-Till-Triggered) order adapter.
 * 
 * <p>ICICI Breeze API does not natively support GTT orders (forever orders).
 * This adapter implements the {@link GttOrderProvider} interface to maintain
 * API consistency but throws {@link UnsupportedOperationException} for all operations.
 * 
 * <p><b>API Limitation</b>: ICICI Breeze does not provide endpoints for:
 * <ul>
 *   <li>Placing GTT/forever orders</li>
 *   <li>Modifying GTT order triggers</li>
 *   <li>Canceling GTT orders</li>
 *   <li>Listing GTT orders</li>
 * </ul>
 * 
 * <p><b>Workaround</b>: Users can manually implement GTT-like behavior by:
 * <ol>
 *   <li>Monitoring market prices via {@link com.tradej.broker.api.port.MarketDataProvider}</li>
 *   <li>Placing limit orders when price reaches trigger level</li>
 *   <li>Using scheduled tasks to check price conditions periodically</li>
 * </ol>
 * 
 * @see <a href="https://api.icicidirect.com/breezeapi-documentation/">ICICI Breeze API Documentation</a>
 */
public final class IciciGttOrderAdapter implements GttOrderProvider {

    @Override
    public Order placeForeverOrder(OrderRequest request, String orderFlag, Long quantity2, Long price2Paisa, Long trigger2Paisa) {
        throw new UnsupportedOperationException("ICICI does not support GTT orders natively");
    }

    @Override
    public Order modifyForeverOrder(String orderId, String orderFlag, String legName, long quantity, long pricePaisa, long triggerPricePaisa) {
        throw new UnsupportedOperationException("ICICI does not support GTT orders natively");
    }

    @Override
    public boolean cancelForeverOrder(String orderId) {
        throw new UnsupportedOperationException("ICICI does not support GTT orders natively");
    }

    @Override
    public List<Order> getForeverOrders() {
        return List.of();
    }
}
