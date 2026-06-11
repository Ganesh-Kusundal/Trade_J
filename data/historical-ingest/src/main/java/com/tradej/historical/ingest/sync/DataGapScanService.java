package com.tradej.historical.ingest.sync;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.historical.ingest.calendar.CompositeHolidayCalendar;
import com.tradej.historical.ingest.canonical.CanonicalBarQuery;
import com.tradej.historical.ingest.canonical.CanonicalPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Scans canonical parquet for data gaps over a lookback window.
 *
 * <p>For each trading day in the window, checks if bars exist.
 * Distinguishes between holidays (from CompositeHolidayCalendar),
 * weekends, missing data, and partial data.
 */
public final class DataGapScanService {

    private static final Logger log = LoggerFactory.getLogger(DataGapScanService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int FULL_DAY_BARS_1M = 375;
    private static final double PARTIAL_THRESHOLD = 0.8;
    private static final int SAMPLE_SIZE = 10;

    private final CompositeHolidayCalendar calendar;
    private final Path barsRoot;

    public DataGapScanService(CompositeHolidayCalendar calendar, Path dataRoot) {
        this.calendar = calendar;
        this.barsRoot = CanonicalPaths.barsRoot(dataRoot);
    }

    public GapReport scan(String segment, String interval, int lookbackMonths) {
        LocalDate to = LocalDate.now(IST);
        LocalDate from = to.minusMonths(lookbackMonths);
        return scanRange(segment, interval, from, to);
    }

    public GapReport scanRange(String segment, String interval, LocalDate from, LocalDate to) {
        List<LocalDate> allDates = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            allDates.add(cursor);
            cursor = cursor.plusDays(1);
        }

        ExchangeSegment exchangeSegment;
        try {
            exchangeSegment = ExchangeSegment.valueOf(segment);
        } catch (IllegalArgumentException ex) {
            exchangeSegment = ExchangeSegment.NSE_EQ;
        }

        List<LocalDate> tradingDays = new ArrayList<>();
        for (LocalDate d : allDates) {
            if (calendar.isTradingDay(exchangeSegment, d)) {
                tradingDays.add(d);
            }
        }

        try (CanonicalBarQuery query = new CanonicalBarQuery(barsRoot)) {
            List<String> symbols = query.availableSymbols(segment, interval);
            if (symbols.isEmpty()) {
                log.warn("No symbols found for {} {}", segment, interval);
                return new GapReport(from, to, tradingDays.size(), 0, 0, tradingDays.size(),
                        tradingDays, List.of(), Map.of());
            }

            List<String> sampleSymbols = symbols.size() <= SAMPLE_SIZE
                    ? symbols
                    : symbols.subList(0, SAMPLE_SIZE);

            Set<LocalDate> allMissingDates = new LinkedHashSet<>();
            Set<LocalDate> allPartialDates = new LinkedHashSet<>();
            int completeDayCount = 0;

            for (LocalDate day : tradingDays) {
                long fromMs = day.atStartOfDay(IST).toInstant().toEpochMilli();
                long toMs = day.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli() - 1;

                boolean anyMissing = false;
                boolean anyPartial = false;
                boolean allComplete = true;

                for (String sym : sampleSymbols) {
                    List<Map<String, Object>> rows = query.queryBars(sym, segment, interval, fromMs, toMs, 1000);
                    int barCount = rows.size();

                    if (barCount == 0) {
                        anyMissing = true;
                        allComplete = false;
                    } else if (barCount < FULL_DAY_BARS_1M * PARTIAL_THRESHOLD) {
                        anyPartial = true;
                        allComplete = false;
                    }
                }

                if (anyMissing) {
                    allMissingDates.add(day);
                } else if (anyPartial) {
                    allPartialDates.add(day);
                } else if (allComplete) {
                    completeDayCount++;
                }
            }

            log.info("Gap scan {} {} ({} symbols sampled): {} trading days, {} complete, {} partial, {} missing",
                    segment, interval, sampleSymbols.size(), tradingDays.size(),
                    completeDayCount, allPartialDates.size(), allMissingDates.size());

            List<LocalDate> missingList = new ArrayList<>(allMissingDates);
            Collections.sort(missingList);
            List<LocalDate> partialList = new ArrayList<>(allPartialDates);
            Collections.sort(partialList);

            return new GapReport(from, to, tradingDays.size(),
                    completeDayCount, partialList.size(), missingList.size(),
                    missingList, partialList, Map.of());
        } catch (SQLException ex) {
            log.error("Gap scan failed", ex);
            return new GapReport(from, to, tradingDays.size(), 0, 0, tradingDays.size(),
                    tradingDays, List.of(), Map.of());
        }
    }

    public record GapReport(
            LocalDate scanFrom,
            LocalDate scanTo,
            int totalTradingDays,
            int daysComplete,
            int daysPartial,
            int daysMissing,
            List<LocalDate> missingDates,
            List<LocalDate> partialDates,
            Map<String, Integer> symbolsWithGaps
    ) {
        public boolean isFullyComplete() {
            return daysMissing == 0 && daysPartial == 0;
        }

        public List<LocalDate> allGapDates() {
            List<LocalDate> all = new ArrayList<>(missingDates);
            all.addAll(partialDates);
            Collections.sort(all);
            return all;
        }
    }
}
