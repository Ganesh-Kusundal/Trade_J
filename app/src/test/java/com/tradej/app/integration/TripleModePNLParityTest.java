package com.tradej.app.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Placeholder for LIVE vs REPLAY vs BACKTEST P&amp;L parity validation.
 * Enable when broker sandbox credentials and historical tick data are configured.
 */
@Tag("integration")
@Disabled("Requires configured sandbox broker and DuckDB tick history")
class TripleModePNLParityTest {

    @Test
    void sameSignalProducesEquivalentPnlAcrossModes() {
        // Implemented when SimulatedOrderService + replay path are exercised in CI
    }
}
