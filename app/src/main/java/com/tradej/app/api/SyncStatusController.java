package com.tradej.app.api;

import com.tradej.historical.ingest.calendar.TradingCalendarStore;
import com.tradej.historical.ingest.canonical.CanonicalBarQuery;
import com.tradej.historical.ingest.canonical.CanonicalPaths;
import com.tradej.historical.ingest.canonical.HistoricalDataStore;
import com.tradej.historical.ingest.canonical.ParquetHistoricalDataStore;
import com.tradej.core.domain.config.DefaultSegments;
import com.tradej.core.domain.value.ExchangeSegment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/admin/sync")
public class SyncStatusController {

    private final Optional<HistoricalDataStore> historicalDataStore;
    private final TradingCalendarStore calendar;
    private final Optional<Path> dataRoot;
    private final com.tradej.historical.ingest.sync.DataGapScanService gapScanService;
    private final com.tradej.app.sync.HistoricalSyncScheduler scheduler;
    private final com.tradej.historical.ingest.calendar.CompositeHolidayCalendar compositeCalendar;
    private final com.tradej.historical.ingest.canonical.MultiIntervalGenerator multiIntervalGenerator;

    public SyncStatusController(
            @Autowired(required = false) HistoricalDataStore historicalDataStore,
            TradingCalendarStore calendar,
            @Autowired(required = false) @Qualifier("canonicalDataRoot") Path dataRoot,
            @Autowired(required = false) com.tradej.historical.ingest.sync.DataGapScanService gapScanService,
            @Autowired(required = false) com.tradej.app.sync.HistoricalSyncScheduler scheduler,
            @Autowired(required = false) com.tradej.historical.ingest.calendar.CompositeHolidayCalendar compositeCalendar,
            @Autowired(required = false) com.tradej.historical.ingest.canonical.MultiIntervalGenerator multiIntervalGenerator) {
        this.historicalDataStore = Optional.ofNullable(historicalDataStore);
        this.calendar = calendar;
        this.dataRoot = Optional.ofNullable(dataRoot);
        this.gapScanService = gapScanService;
        this.scheduler = scheduler;
        this.compositeCalendar = compositeCalendar;
        this.multiIntervalGenerator = multiIntervalGenerator;
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> syncStatus(
            @RequestParam(defaultValue = "SBIN") String symbol,
            @RequestParam(defaultValue = "1m") String interval) {
        HistoricalDataStore store = historicalDataStore.orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("symbol", symbol);
        body.put("interval", interval);
        if (store == null) {
            body.put("status", "UNAVAILABLE");
            body.put("message", "HistoricalDataStore not configured");
            return ResponseEntity.ok(body);
        }
        Optional<LocalDate> latest = store.latestAvailableDate(symbol, interval);
        body.put("latestDate", latest.map(LocalDate::toString).orElse("none"));
        body.put("isStale", latest.map(d -> d.isBefore(LocalDate.now().minusDays(1))).orElse(true));
        body.put("todayIsTradingDay", calendar.isTradingDay(ExchangeSegment.NSE_EQ, LocalDate.now()));
        return ResponseEntity.ok(body);
    }

    @GetMapping(value = "/quality", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> dataQuality(
            @RequestParam(defaultValue = "SBIN") String symbol,
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        HistoricalDataStore store = historicalDataStore.orElse(null);
        if (store == null) {
            return ResponseEntity.ok(Map.of("status", "UNAVAILABLE"));
        }
        LocalDate fromDate = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate toDate = to != null ? to : LocalDate.now();
        var report = store.qualityCheck(symbol, interval, fromDate, toDate);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("symbol", report.symbol());
        body.put("interval", report.interval());
        body.put("from", report.from().toString());
        body.put("to", report.to().toString());
        body.put("expectedBars", report.expectedBars());
        body.put("actualBars", report.actualBars());
        body.put("missingBars", report.missingBars());
        body.put("completenessPercent", Math.round(report.completenessPercent() * 100.0) / 100.0);
        body.put("isComplete", report.isComplete());
        body.put("gapDates", report.gapDates().stream().map(LocalDate::toString).toList());
        return ResponseEntity.ok(body);
    }

    @GetMapping(value = "/calendar", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> tradingCalendar(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        LocalDate fromDate = from != null ? from : LocalDate.now().minusDays(7);
        LocalDate toDate = to != null ? to : LocalDate.now().plusDays(7);
        var days = calendar.tradingDays(ExchangeSegment.NSE_EQ, fromDate, toDate);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", fromDate.toString());
        body.put("to", toDate.toString());
        body.put("tradingDays", days.stream().map(LocalDate::toString).toList());
        body.put("count", days.size());
        return ResponseEntity.ok(body);
    }

    @GetMapping(value = "/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> summary(
            @RequestParam(defaultValue = DefaultSegments.DEFAULT_EQUITY_SEGMENT) String segment) {
        if (dataRoot.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "UNAVAILABLE"));
        }
        try (CanonicalBarQuery query = new CanonicalBarQuery(CanonicalPaths.barsRoot(dataRoot.get()))) {
            return ResponseEntity.ok(query.summary(segment));
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping(value = "/intervals", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> intervals(
            @RequestParam String symbol,
            @RequestParam(defaultValue = DefaultSegments.DEFAULT_EQUITY_SEGMENT) String segment) {
        if (dataRoot.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "UNAVAILABLE"));
        }
        try (CanonicalBarQuery query = new CanonicalBarQuery(CanonicalPaths.barsRoot(dataRoot.get()))) {
            List<String> available = query.availableIntervals(symbol, segment);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("symbol", symbol);
            body.put("segment", segment);
            body.put("intervals", available);
            return ResponseEntity.ok(body);
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping(value = "/resample", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> resample(
            @RequestParam String symbol,
            @RequestParam(defaultValue = DefaultSegments.DEFAULT_EQUITY_SEGMENT) String segment,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        if (multiIntervalGenerator == null) {
            return ResponseEntity.ok(Map.of("status", "UNAVAILABLE"));
        }
        var result = multiIntervalGenerator.generateForSymbol(symbol, segment, from, to);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("symbol", result.symbol());
        body.put("sourceBarsRead", result.sourceBarsRead());
        body.put("derivedBarsWritten", result.derivedBarsWritten());
        return ResponseEntity.ok(body);
    }

    @GetMapping(value = "/gaps", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> gapScan(
            @RequestParam(defaultValue = DefaultSegments.DEFAULT_EQUITY_SEGMENT) String segment,
            @RequestParam(defaultValue = "3") int lookbackMonths) {
        if (gapScanService == null) {
            return ResponseEntity.ok(Map.of("status", "UNAVAILABLE"));
        }
        var report = gapScanService.scan(segment, "1m", lookbackMonths);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scanFrom", report.scanFrom().toString());
        body.put("scanTo", report.scanTo().toString());
        body.put("totalTradingDays", report.totalTradingDays());
        body.put("daysComplete", report.daysComplete());
        body.put("daysPartial", report.daysPartial());
        body.put("daysMissing", report.daysMissing());
        body.put("missingDates", report.missingDates().stream().map(LocalDate::toString).toList());
        body.put("partialDates", report.partialDates().stream().map(LocalDate::toString).toList());
        body.put("isFullyComplete", report.isFullyComplete());
        return ResponseEntity.ok(body);
    }

    @PostMapping(value = "/trigger", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> triggerSync(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        if (scheduler == null) {
            return ResponseEntity.ok(Map.of("status", "UNAVAILABLE"));
        }
        scheduler.triggerSync(from, to);
        return ResponseEntity.ok(Map.of("status", "TRIGGERED", "from", from.toString(), "to", to.toString()));
    }

    @PostMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> syncAll() {
        if (scheduler == null) {
            return ResponseEntity.ok(Map.of("status", "UNAVAILABLE"));
        }
        new Thread(() -> scheduler.syncAll(), "sync-all-worker").start();
        return ResponseEntity.ok(Map.of("status", "TRIGGERED", "type", "sync-all",
                "message", "Full sync started: gap scan + bulk equity (90-day windows) + runtime export"));
    }

    @GetMapping(value = "/holidays", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> holidays(
            @RequestParam(defaultValue = "2026") int year) {
        if (compositeCalendar == null) {
            return ResponseEntity.ok(Map.of("status", "UNAVAILABLE"));
        }
        var holidays = compositeCalendar.getHolidays(year);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("year", year);
        body.put("count", holidays.size());
        body.put("dates", holidays.stream().map(d -> Map.of(
                "date", d.toString(),
                "day", d.getDayOfWeek().toString(),
                "source", String.valueOf(compositeCalendar.getSource(d))
        )).toList());
        return ResponseEntity.ok(body);
    }

    @PostMapping(value = "/refresh-holidays", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> refreshHolidays() {
        if (compositeCalendar == null || dataRoot.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "UNAVAILABLE"));
        }
        compositeCalendar.refreshFromData(dataRoot.get());
        int year = LocalDate.now().getYear();
        var holidays = compositeCalendar.getHolidays(year);
        return ResponseEntity.ok(Map.of(
                "status", "REFRESHED",
                "year", year,
                "holidayCount", holidays.size()
        ));
    }

    @GetMapping(value = "/scheduler", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> schedulerStatus() {
        if (scheduler == null) {
            return ResponseEntity.ok(Map.of("status", "UNAVAILABLE"));
        }
        var status = scheduler.getStatus();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", status.state());
        body.put("trigger", status.trigger());
        body.put("startedAt", status.startedAt() != null ? status.startedAt().toString() : null);
        body.put("completedAt", status.completedAt() != null ? status.completedAt().toString() : null);
        body.put("totalDates", status.totalDates());
        body.put("gapDates", status.gapDates().stream().map(LocalDate::toString).toList());
        body.put("dateResults", status.dateResults().stream().map(r -> Map.of(
                "date", r.date().toString(),
                "status", r.status(),
                "detail", r.detail() != null ? r.detail() : ""
        )).toList());
        return ResponseEntity.ok(body);
    }
}
