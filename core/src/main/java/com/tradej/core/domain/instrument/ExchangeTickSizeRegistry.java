package com.tradej.core.domain.instrument;

import com.tradej.core.domain.value.ExchangeSegment;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of exchange-specific tick sizes (in paisa).
 *
 * <p>Different exchange segments have different minimum price increments.
 * For example, NSE Equity uses 5 paisa while MCX Commodity uses 1 paisa.
 * This registry centralizes those values and allows runtime overrides.
 */
public final class ExchangeTickSizeRegistry {

    private static final Map<String, Long> TICK_SIZES = new ConcurrentHashMap<>();

    static {
        // NSE Equity: 5 paisa
        TICK_SIZES.put("NSE_EQ", 5L);
        // NSE F&O: 5 paisa
        TICK_SIZES.put("NSE_FNO", 5L);
        // BSE Equity: 5 paisa
        TICK_SIZES.put("BSE_EQ", 5L);
        // MCX Commodity: 1 paisa
        TICK_SIZES.put("MCX_COMM", 1L);
        // Currency derivatives: 25 paisa (0.25 * 100)
        TICK_SIZES.put("NSE_CURRENCY", 25L);
        TICK_SIZES.put("BSE_CURRENCY", 25L);
        // Index: 1 paisa
        TICK_SIZES.put("IDX_I", 1L);
    }

    private ExchangeTickSizeRegistry() {
    }

    /**
     * Returns the tick size in paisa for the given exchange segment.
     * Defaults to 5 paisa if the segment is not explicitly registered.
     */
    public static long tickSizePaisa(ExchangeSegment segment) {
        return TICK_SIZES.getOrDefault(segment.name(), 5L);
    }

    /**
     * Register or override the tick size for a given exchange segment at runtime.
     */
    public static void register(ExchangeSegment segment, long tickSizePaisa) {
        TICK_SIZES.put(segment.name(), tickSizePaisa);
    }
}
