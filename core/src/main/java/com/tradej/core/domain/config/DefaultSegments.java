package com.tradej.core.domain.config;

/**
 * Single source of truth for default exchange segment string values.
 *
 * <p>All controllers, CLI commands, and configuration defaults should reference
 * these constants instead of hardcoding {@code "NSE_EQ"}, {@code "IDX_I"}, etc.
 * This ensures that renaming a segment only requires changing one file.
 */
public final class DefaultSegments {

    private DefaultSegments() {
    }

    /** Default equity segment (NSE cash market). */
    public static final String DEFAULT_EQUITY_SEGMENT = "NSE_EQ";

    /** Default index segment. */
    public static final String DEFAULT_INDEX_SEGMENT = "IDX_I";

    /** Default F&O segment. */
    public static final String DEFAULT_FNO_SEGMENT = "NSE_FNO";
}
