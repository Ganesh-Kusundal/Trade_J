package com.tradej.core.domain.value;

/**
 * Product types for order placement.
 *
 * <p>The first four constants ({@link #INTRADAY}, {@link #CNC}, {@link #MARGIN},
 * {@link #CARRY_FORWARD}) are the canonical product types used throughout the system.
 *
 * <p>The remaining constants are exchange-agnostic aliases that map to a canonical
 * type via {@link #canonical()}. They allow broker-specific naming conventions to be
 * normalized without breaking existing code.
 */
public enum ProductType {
    /** Intraday square-off (MIS on NSE). */
    INTRADAY,
    /** Cash and Carry -- delivery-based trading. */
    CNC,
    /** Margin Trading Facility (MTF). */
    MARGIN,
    /** Normal / carry-forward position. */
    CARRY_FORWARD,

    // ---- Exchange-agnostic aliases ----

    /** Alias for {@link #CNC} -- delivery-based trading. */
    DELIVERY,
    /** Alias for {@link #INTRADAY} -- intraday margin trading. */
    INTRADAY_MARGIN,
    /** Alias for {@link #MARGIN} -- margin funding. */
    MARGIN_FUNDING;

    /**
     * Returns the canonical product type for this value.
     *
     * <p>For the original four constants, this returns {@code this}.
     * For alias constants, this returns the canonical type they map to:
     * <ul>
     *   <li>{@code DELIVERY} → {@code CNC}</li>
     *   <li>{@code INTRADAY_MARGIN} → {@code INTRADAY}</li>
     *   <li>{@code MARGIN_FUNDING} → {@code MARGIN}</li>
     * </ul>
     */
    public ProductType canonical() {
        return switch (this) {
            case DELIVERY -> CNC;
            case INTRADAY_MARGIN -> INTRADAY;
            case MARGIN_FUNDING -> MARGIN;
            default -> this;
        };
    }
}
