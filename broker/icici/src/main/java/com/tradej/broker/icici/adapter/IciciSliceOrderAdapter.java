package com.tradej.broker.icici.adapter;

import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.SliceOrderRequest;

import java.util.List;

/**
 * ICICI slice order adapter.
 * 
 * <p>ICICI Breeze API does not natively support slice orders (TWAP/VWAP-style order splitting).
 * This adapter implements the {@link SliceOrderCommand} interface to maintain
 * API consistency but throws {@link UnsupportedOperationException}.
 * 
 * <p><b>API Limitation</b>: ICICI Breeze does not provide a multi-order endpoint
 * or native order splitting functionality.
 * 
 * <p><b>Workaround</b>: Users can manually implement slice order logic by:
 * <ol>
 *   <li>Splitting the total quantity into chunks in application code</li>
 *   <li>Placing each chunk as a separate order via {@link com.tradej.broker.api.port.OrderCommand#placeOrder}</li>
 *   <li>Implementing rate limiting between order placements</li>
 * </ol>
 * 
 * @see <a href="https://api.icicidirect.com/breezeapi-documentation/">ICICI Breeze API Documentation</a>
 */
public final class IciciSliceOrderAdapter implements SliceOrderCommand {

    @Override
    public List<Order> placeSliceOrder(SliceOrderRequest request) {
        throw new UnsupportedOperationException("ICICI does not support slice orders");
    }
}
