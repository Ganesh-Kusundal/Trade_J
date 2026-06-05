package com.tradej.core.domain.scan;

/**
 * Single source of truth for asset class classification across the codebase.
 * Used by the scanner universe builder, node library, and pipeline nodes.
 */
public enum AssetClass {
    EQUITY,
    FUTURE,
    OPTION,
    ROLLING_OPTION
}
