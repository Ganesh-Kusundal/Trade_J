package com.tradej.historical.ingest.universe;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class HivePartitionResolverTest {

    @Test
    void spansMonthsInRange() {
        List<String> partitions = HivePartitionResolver.partitionsForRange(
                LocalDate.of(2026, 4, 28),
                LocalDate.of(2026, 5, 5)
        );
        assertEquals(2, partitions.size());
        assertTrue(partitions.contains("part-hive-2026-04.parquet"));
        assertTrue(partitions.contains("part-hive-2026-05.parquet"));
    }

    @Test
    void partitionForDateUsesYearMonth() {
        assertEquals(
                "part-hive-2026-05.parquet",
                HivePartitionResolver.partitionFileForDate(LocalDate.of(2026, 5, 29))
        );
    }
}
