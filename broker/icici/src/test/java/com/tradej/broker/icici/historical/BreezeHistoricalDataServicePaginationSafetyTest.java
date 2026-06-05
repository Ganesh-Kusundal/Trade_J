package com.tradej.broker.icici.historical;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class BreezeHistoricalDataServicePaginationSafetyTest {

    @Test
    void paginationLoopIsBounded() {
        assertTrue(BreezeHistoricalDataService.MAX_PAGINATION_ITERATIONS > 0);
        assertTrue(BreezeHistoricalDataService.MAX_PAGINATION_ITERATIONS <= 10_000,
                "Pagination safety limit must prevent unbounded historical fetch loops");
    }
}
