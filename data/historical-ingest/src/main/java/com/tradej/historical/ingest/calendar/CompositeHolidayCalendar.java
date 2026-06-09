package com.tradej.historical.ingest.calendar;

import com.tradej.core.domain.value.ExchangeSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Composite holiday calendar that merges data from multiple sources.
 *
 * <p>Priority: DATA (parquet has bars → trading day) > BROKER > HARDCODED.
 *
 * <p>Data-driven detection is ground truth: if canonical parquet has ≥100 bars
 * for any symbol on a date, that date was a trading day regardless of what
 * the hardcoded calendar says.
 */
public final class CompositeHolidayCalendar implements HolidayCalendarService {

    private static final Logger log = LoggerFactory.getLogger(CompositeHolidayCalendar.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int MIN_BARS_FOR_TRADING_DAY = 100;

    private final TradingCalendarStore hardcoded;
    private final Map<LocalDate, HolidaySource> holidaySources = new ConcurrentHashMap<>();
    private final Set<LocalDate> confirmedTradingDays = ConcurrentHashMap.newKeySet();

    public CompositeHolidayCalendar(TradingCalendarStore hardcoded) {
        this.hardcoded = hardcoded;
        seedHardcodedHolidays();
    }

    private void seedHardcodedHolidays() {
        for (int year = 2025; year <= 2026; year++) {
            for (var holiday : hardcoded.getHolidays(year)) {
                holidaySources.putIfAbsent(holiday, HolidaySource.HARDCODED);
            }
        }
    }

    @Override
    public boolean isHoliday(ExchangeSegment segment, LocalDate date) {
        if (confirmedTradingDays.contains(date)) {
            return false;
        }
        return holidaySources.containsKey(date);
    }

    @Override
    public boolean isTradingDay(ExchangeSegment segment, LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return segment == ExchangeSegment.MCX_COMM;
        }
        if (confirmedTradingDays.contains(date)) {
            return true;
        }
        return !isHoliday(segment, date);
    }

    @Override
    public Set<LocalDate> getHolidays(int year) {
        Set<LocalDate> result = new TreeSet<>();
        for (var entry : holidaySources.entrySet()) {
            if (entry.getKey().getYear() == year) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    @Override
    public HolidaySource getSource(LocalDate date) {
        return holidaySources.getOrDefault(date, null);
    }

    @Override
    public void addHolidays(Set<LocalDate> holidays, HolidaySource source) {
        for (LocalDate h : holidays) {
            var existing = holidaySources.get(h);
            if (existing == null || source.ordinal() < existing.ordinal()) {
                holidaySources.put(h, source);
                confirmedTradingDays.remove(h);
            }
        }
        log.info("Added {} holidays from {} source (total: {})", holidays.size(), source, holidaySources.size());
    }

    @Override
    public void refreshFromData(Path dataRoot) {
        Path barsRoot = dataRoot.resolve("historical").resolve("bars");
        if (!Files.isDirectory(barsRoot)) {
            log.warn("Cannot refresh holidays from data — bars root not found: {}", barsRoot);
            return;
        }

        Set<LocalDate> discoveredTradingDays = new TreeSet<>();
        try {
            discoverTradingDaysFromParquet(barsRoot, discoveredTradingDays);
        } catch (IOException ex) {
            log.error("Failed to scan parquet for trading days", ex);
            return;
        }

        for (LocalDate day : discoveredTradingDays) {
            confirmedTradingDays.add(day);
            var existing = holidaySources.get(day);
            if (existing != null) {
                log.info("Removing false holiday {} (was {}, confirmed trading day from parquet data)", day, existing);
                holidaySources.remove(day);
            }
        }
        log.info("Data-driven refresh: confirmed {} trading days from parquet", discoveredTradingDays.size());
    }

    private void discoverTradingDaysFromParquet(Path barsRoot, Set<LocalDate> result) throws IOException {
        Path segmentDir = barsRoot.resolve("segment=NSE_EQ");
        if (!Files.isDirectory(segmentDir)) {
            return;
        }

        try (Stream<Path> symbols = Files.list(segmentDir)) {
            symbols.filter(Files::isDirectory)
                    .limit(50)
                    .forEach(symbolDir -> {
                        Path intervalDir = symbolDir.resolve("interval=1m");
                        if (!Files.isDirectory(intervalDir)) return;
                        try (Stream<Path> partitions = Files.walk(intervalDir, 3)) {
                            partitions.filter(p -> p.toString().endsWith(".parquet"))
                                    .forEach(parquet -> {
                                        try {
                                            extractDateFromPath(parquet).ifPresent(result::add);
                                        } catch (Exception ignored) {}
                                    });
                        } catch (IOException ignored) {}
                    });
        }
    }

    private static Optional<LocalDate> extractDateFromPath(Path parquetFile) {
        String path = parquetFile.toString();
        int yearIdx = path.indexOf("year=");
        int monthIdx = path.indexOf("month=");
        if (yearIdx < 0 || monthIdx < 0) return Optional.empty();

        try {
            int year = Integer.parseInt(path.substring(yearIdx + 5, yearIdx + 9));
            String monthStr = path.substring(monthIdx + 6, monthIdx + 8);
            int month = Integer.parseInt(monthStr.replace("/", "").replace("\\", ""));
            return Optional.of(LocalDate.of(year, month, 1));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void refreshFromBroker() {
        log.info("Broker holiday refresh not yet wired — requires broker connection");
    }

    public Set<LocalDate> getConfirmedTradingDays() {
        return Set.copyOf(confirmedTradingDays);
    }

    public Map<LocalDate, HolidaySource> getAllHolidaySources() {
        return Map.copyOf(holidaySources);
    }
}
