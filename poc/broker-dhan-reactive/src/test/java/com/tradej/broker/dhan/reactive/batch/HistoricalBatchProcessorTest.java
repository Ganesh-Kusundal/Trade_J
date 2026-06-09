package com.tradej.broker.dhan.reactive.batch;

import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for HistoricalBatchProcessor.
 * Tests auto-splitting of large date ranges into API-compliant chunks.
 */
class HistoricalBatchProcessorTest {
    
    @Nested
    class IntradaySplitTests {
        
        @Test
        void splitIntradayRange_180Days_SplitsIntoTwo90DayBatches() {
            LocalDate start = LocalDate.now().minusDays(180);
            LocalDate end = LocalDate.now();
            
            List<DateRange> batches = HistoricalBatchProcessor.splitIntradayRange(start, end);
            
            assertEquals(2, batches.size());
            assertEquals(start, batches.get(0).start());
            assertEquals(start.plusDays(90), batches.get(0).end());
            assertEquals(start.plusDays(90), batches.get(1).start());
            assertEquals(end, batches.get(1).end());
        }
        
        @Test
        void splitIntradayRange_45Days_NoSplit() {
            LocalDate start = LocalDate.now().minusDays(45);
            LocalDate end = LocalDate.now();
            
            List<DateRange> batches = HistoricalBatchProcessor.splitIntradayRange(start, end);
            
            assertEquals(1, batches.size());
            assertEquals(start, batches.get(0).start());
            assertEquals(end, batches.get(0).end());
        }
        
        @Test
        void splitIntradayRange_270Days_SplitsIntoThree90DayBatches() {
            LocalDate start = LocalDate.now().minusDays(270);
            LocalDate end = LocalDate.now();
            
            List<DateRange> batches = HistoricalBatchProcessor.splitIntradayRange(start, end);
            
            assertEquals(3, batches.size());
            assertTrue(batches.stream().allMatch(batch -> 
                batch.end().toEpochDay() - batch.start().toEpochDay() <= 90));
        }
    }
    
    @Nested
    class OptionsSplitTests {
        
        @Test
        void splitOptionsRange_60Days_SplitsIntoTwo30DayBatches() {
            LocalDate start = LocalDate.now().minusDays(60);
            LocalDate end = LocalDate.now();
            
            List<DateRange> batches = HistoricalBatchProcessor.splitOptionsRange(start, end);
            
            assertEquals(2, batches.size());
            assertEquals(start, batches.get(0).start());
            assertEquals(start.plusDays(30), batches.get(0).end());
            assertEquals(start.plusDays(30), batches.get(1).start());
            assertEquals(end, batches.get(1).end());
        }
        
        @Test
        void splitOptionsRange_25Days_NoSplit() {
            LocalDate start = LocalDate.now().minusDays(25);
            LocalDate end = LocalDate.now();
            
            List<DateRange> batches = HistoricalBatchProcessor.splitOptionsRange(start, end);
            
            assertEquals(1, batches.size());
        }
    }
    
    @Nested
    class DailySplitTests {
        
        @Test
        void splitDailyRange_7300Days_SplitsIntoTwo3650DayBatches() {
            LocalDate start = LocalDate.now().minusDays(7300);
            LocalDate end = LocalDate.now();
            
            List<DateRange> batches = HistoricalBatchProcessor.splitDailyRange(start, end);
            
            assertEquals(2, batches.size());
        }
        
        @Test
        void splitDailyRange_1000Days_NoSplit() {
            LocalDate start = LocalDate.now().minusDays(1000);
            LocalDate end = LocalDate.now();
            
            List<DateRange> batches = HistoricalBatchProcessor.splitDailyRange(start, end);
            
            assertEquals(1, batches.size());
        }
    }
    
    @Nested
    class ValidationTests {
        
        @Test
        void splitIntradayRange_InvalidDates_ThrowsException() {
            LocalDate start = LocalDate.now();
            LocalDate end = LocalDate.now().minusDays(10);
            
            assertThrows(IllegalArgumentException.class, () -> 
                HistoricalBatchProcessor.splitIntradayRange(start, end));
        }
        
        @Test
        void splitOptionsRange_NullStart_ThrowsException() {
            assertThrows(NullPointerException.class, () -> 
                HistoricalBatchProcessor.splitOptionsRange(null, LocalDate.now()));
        }
    }
}
