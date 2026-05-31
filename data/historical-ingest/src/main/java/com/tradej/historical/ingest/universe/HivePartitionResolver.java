package com.tradej.historical.ingest.universe;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps calendar dates to hive month partition files ({@code part-hive-YYYY-MM.parquet}).
 */
public final class HivePartitionResolver {

    private HivePartitionResolver() {
    }

    public static String partitionFileForDate(LocalDate date) {
        return partitionFileForMonth(YearMonth.from(date));
    }

    public static String partitionFileForMonth(YearMonth month) {
        return "part-hive-" + month + ".parquet";
    }

    public static List<String> partitionsForRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return List.of();
        }
        LocalDate start = from.isBefore(to) ? from : to;
        LocalDate end = from.isBefore(to) ? to : from;
        YearMonth cursor = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        List<String> partitions = new ArrayList<>();
        while (!cursor.isAfter(last)) {
            partitions.add(partitionFileForMonth(cursor));
            cursor = cursor.plusMonths(1);
        }
        return partitions;
    }
}
