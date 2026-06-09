package com.tradej.broker.dhan.reactive.batch;

import com.tradej.broker.dhan.reactive.resilience.DhanRateLimits;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch processor for historical data requests.
 * Automatically splits large date ranges into API-compliant chunks.
 * 
 * Limits:
 * - Intraday: 90 days per request
 * - Options/Futures: 30 days per request
 * - Daily: 3650 days (10 years) per request
 */
public final class HistoricalBatchProcessor {
    
    /**
     * Split intraday date range into 90-day batches.
     */
    public static List<DateRange> splitIntradayRange(LocalDate start, LocalDate end) {
        validateDateRange(start, end);
        return splitRange(start, end, DhanRateLimits.HISTORICAL_INTRADAY_MAX_DAYS);
    }
    
    /**
     * Split options/futures date range into 30-day batches.
     */
    public static List<DateRange> splitOptionsRange(LocalDate start, LocalDate end) {
        validateDateRange(start, end);
        return splitRange(start, end, DhanRateLimits.ROLLING_OPTION_MAX_DAYS);
    }
    
    /**
     * Split daily date range into 3650-day batches.
     */
    public static List<DateRange> splitDailyRange(LocalDate start, LocalDate end) {
        validateDateRange(start, end);
        return splitRange(start, end, DhanRateLimits.HISTORICAL_DAILY_MAX_DAYS);
    }
    
    /**
     * Generic range splitter.
     */
    private static List<DateRange> splitRange(LocalDate start, LocalDate end, int maxDays) {
        List<DateRange> batches = new ArrayList<>();
        LocalDate current = start;
        
        while (current.isBefore(end)) {
            LocalDate batchEnd = current.plusDays(maxDays);
            if (batchEnd.isAfter(end)) {
                batchEnd = end;
            }
            
            batches.add(new DateRange(current, batchEnd));
            current = batchEnd;
        }
        
        return batches;
    }
    
    /**
     * Validate date range.
     */
    private static void validateDateRange(LocalDate start, LocalDate end) {
        if (start == null) {
            throw new NullPointerException("Start date must not be null");
        }
        if (end == null) {
            throw new NullPointerException("End date must not be null");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException(
                "End date (" + end + ") must not be before start date (" + start + ")");
        }
    }
    
    /**
     * Immutable date range record.
     */
    public record DateRange(LocalDate start, LocalDate end) {
        public DateRange {
            if (start == null || end == null) {
                throw new NullPointerException("Date range dates must not be null");
            }
            if (end.isBefore(start)) {
                throw new IllegalArgumentException("End date must not be before start date");
            }
        }
        
        public long daysBetween() {
            return end.toEpochDay() - start.toEpochDay();
        }
    }
}
