package com.tradej.core.domain.model;

/**
 * Single price level in a market depth (order book) snapshot.
 *
 * @param pricePaisa price in paisa
 * @param quantity   total quantity at this price level
 * @param orderCount number of orders at this level (L2 data)
 * @param orderId    optional order ID for L3 data (null for L2)
 * @param levelType  visibility type of this level
 */
public record DepthLevel(
        long pricePaisa,
        long quantity,
        int orderCount,
        String orderId,
        DepthLevelType levelType
) {
    /**
     * L2 constructor (no order ID, visible level).
     */
    public DepthLevel(long pricePaisa, long quantity, int orderCount) {
        this(pricePaisa, quantity, orderCount, null, DepthLevelType.VISIBLE);
    }

    public boolean isL3() {
        return orderId != null;
    }

    public enum DepthLevelType {
        VISIBLE,
        HIDDEN,
        ICEBERG
    }
}
