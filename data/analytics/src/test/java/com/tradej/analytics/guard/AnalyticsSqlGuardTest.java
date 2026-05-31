package com.tradej.analytics.guard;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class AnalyticsSqlGuardTest {

    @Test
    void allowsSelectQueries() {
        assertDoesNotThrow(() -> AnalyticsSqlGuard.validateReadOnly("select * from equity_bars_1m"));
    }

    @Test
    void rejectsMutatingStatements() {
        assertThrows(IllegalArgumentException.class, () -> AnalyticsSqlGuard.validateReadOnly("drop table equity_bars_1m"));
        assertThrows(IllegalArgumentException.class, () -> AnalyticsSqlGuard.validateReadOnly("select 1; delete from t"));
    }

    @Test
    void appendsLimitWhenMissing() {
        String sql = AnalyticsSqlGuard.applyRowLimit("select symbol from equity_universe", 100);
        assertTrue(sql.toLowerCase().contains("limit 100"));
    }
}
