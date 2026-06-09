package com.tradej.historical.ingest.canonical;

import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Canonical directory layout for all historical data storage.
 *
 * <pre>
 * data/historical/
 *   bars/
 *     segment=NSE_EQ/symbol=RELIANCE/interval=1m/year=2026/month=06/part.parquet
 *   futures/
 *     exchange=NSE/underlying=RELIANCE/expiry=2026-06-26/interval=1m/year=2026/month=06/part.parquet
 *   options/
 *     exchange=NSE/underlying=NIFTY/expiry=2026-06-25/instrument=CE/strike=25000/interval=1m/year=2026/month=06/part.parquet
 *   option-chains/
 *     underlying=NIFTY/expiry=2026-06-25/date=2026-06-08/snapshot-HHmm.parquet
 *   corporate-actions/
 *     symbol=RELIANCE/actions.parquet
 *   instruments/
 *     segment=NSE_EQ/master.parquet
 *   calendar/
 *     exchange=NSE/calendar.parquet
 *   features/
 *     version=v1/feature_set=technical/symbol=RELIANCE/date=2026-06-08/part.parquet
 * </pre>
 */
public final class CanonicalPaths {

    private CanonicalPaths() {}

    public static final String SEGMENT_HIVE = "segment";
    public static final String SYMBOL_HIVE = "symbol";
    public static final String INTERVAL_HIVE = "interval";
    public static final String YEAR_HIVE = "year";
    public static final String MONTH_HIVE = "month";
    public static final String EXCHANGE_HIVE = "exchange";
    public static final String UNDERLYING_HIVE = "underlying";
    public static final String EXPIRY_HIVE = "expiry";
    public static final String INSTRUMENT_HIVE = "instrument";
    public static final String STRIKE_HIVE = "strike";
    public static final String DATE_HIVE = "date";
    public static final String VERSION_HIVE = "version";
    public static final String FEATURE_SET_HIVE = "feature_set";

    public static Path barsRoot(Path dataRoot) {
        return dataRoot.resolve("historical").resolve("bars");
    }

    public static Path barsPath(Path dataRoot, String segment, String symbol,
                                 String interval, int year, int month) {
        return barsRoot(dataRoot)
                .resolve(SEGMENT_HIVE + "=" + segment)
                .resolve(SYMBOL_HIVE + "=" + symbol)
                .resolve(INTERVAL_HIVE + "=" + interval)
                .resolve(YEAR_HIVE + "=" + year)
                .resolve(MONTH_HIVE + "=" + String.format("%02d", month));
    }

    public static Path futuresRoot(Path dataRoot) {
        return dataRoot.resolve("historical").resolve("futures");
    }

    public static Path optionsRoot(Path dataRoot) {
        return dataRoot.resolve("historical").resolve("options");
    }

    public static Path optionChainsRoot(Path dataRoot) {
        return dataRoot.resolve("historical").resolve("option-chains");
    }

    public static Path optionChainPath(Path dataRoot, String underlying,
                                        LocalDate expiry, LocalDate date, String timeTag) {
        return optionChainsRoot(dataRoot)
                .resolve(UNDERLYING_HIVE + "=" + underlying)
                .resolve(EXPIRY_HIVE + "=" + expiry)
                .resolve(DATE_HIVE + "=" + date)
                .resolve("snapshot-" + timeTag + ".parquet");
    }

    public static Path corporateActionsRoot(Path dataRoot) {
        return dataRoot.resolve("historical").resolve("corporate-actions");
    }

    public static Path instrumentsRoot(Path dataRoot) {
        return dataRoot.resolve("historical").resolve("instruments");
    }

    public static Path calendarRoot(Path dataRoot) {
        return dataRoot.resolve("historical").resolve("calendar");
    }

    public static Path featuresRoot(Path dataRoot) {
        return dataRoot.resolve("historical").resolve("features");
    }

    public static String monthPartition(LocalDate date) {
        return String.format("%02d", date.getMonthValue());
    }

    public static int yearOf(LocalDate date) {
        return date.getYear();
    }

    public static int monthOf(LocalDate date) {
        return date.getMonthValue();
    }
}
