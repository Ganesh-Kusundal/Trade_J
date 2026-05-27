package com.tradej.broker.dhan.instrument;

import com.tradej.core.domain.value.OptionType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SymbolNormalizationEngine {
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final Pattern SPACED_OPTION_PATTERN = Pattern.compile(
            "^(?<underlying>[A-Z&\\-\\s]+)\\s+(?<day>\\d{1,2})\\s+(?<month>[A-Z]{3})\\s+(?<strike>\\d+(?:\\.\\d+)?)\\s+(?<type>CE|PE|CALL|PUT|C|P)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SPACED_FUTURE_PATTERN = Pattern.compile(
            "^(?<underlying>[A-Z&\\-\\s]+)\\s+(?<day>\\d{1,2})\\s+(?<month>[A-Z]{3})\\s+FUT(?:URES)?$",
            Pattern.CASE_INSENSITIVE
    );

    private SymbolNormalizationEngine() {
    }

    public static ParsedSymbol parseSymbol(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim().toUpperCase(Locale.ENGLISH);

        Matcher option = SPACED_OPTION_PATTERN.matcher(cleaned);
        if (option.matches()) {
            return new ParsedSymbol(
                    option.group("underlying").trim(),
                    normalizeMonth(option.group("month")),
                    Integer.parseInt(option.group("day")),
                    option.group("strike"),
                    OptionType.fromCode(option.group("type")),
                    true
            );
        }

        Matcher future = SPACED_FUTURE_PATTERN.matcher(cleaned);
        if (future.matches()) {
            return new ParsedSymbol(
                    future.group("underlying").trim(),
                    normalizeMonth(future.group("month")),
                    Integer.parseInt(future.group("day")),
                    null,
                    OptionType.UNKNOWN,
                    false
            );
        }
        return null;
    }

    public static String normalizeDhanSymbol(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        ParsedSymbol parsed = parseSymbol(raw);
        if (parsed == null) {
            return raw.trim().toUpperCase(Locale.ENGLISH);
        }
        if (parsed.option()) {
            String strike = parsed.strike();
            return (parsed.underlying() + " "
                    + String.format(Locale.ENGLISH, "%02d", parsed.day()) + " "
                    + parsed.month() + " "
                    + strike + " "
                    + (parsed.optionType() == OptionType.CALL ? "CALL" : "PUT")).trim();
        }
        return (parsed.underlying() + " "
                + String.format(Locale.ENGLISH, "%02d", parsed.day()) + " "
                + parsed.month() + " FUT").trim();
    }

    public static String getCanonicalSymbol(
            String underlying,
            LocalDate expiryDate,
            Long strikePricePaisa,
            OptionType optionType,
            boolean option
    ) {
        if (underlying == null || underlying.isBlank() || expiryDate == null) {
            return "";
        }
        String normalizedUnderlying = underlying.trim().toUpperCase(Locale.ENGLISH);
        String dayMonth = expiryDate.format(DAY_MONTH).toUpperCase(Locale.ENGLISH);
        if (option) {
            if (strikePricePaisa == null || optionType == null || optionType == OptionType.UNKNOWN) {
                return "";
            }
            long wholeRupees = strikePricePaisa / 100L;
            String strike = strikePricePaisa % 100L == 0L
                    ? Long.toString(wholeRupees)
                    : String.format(Locale.ENGLISH, "%.2f", strikePricePaisa / 100.0d);
            return normalizedUnderlying + " " + dayMonth + " " + strike + " " + (optionType == OptionType.CALL ? "CALL" : "PUT");
        }
        return normalizedUnderlying + " " + dayMonth + " FUT";
    }

    public static String stripped(String value) {
        return value == null ? "" : value.replace(" ", "").replace("-", "").replace("_", "").trim().toUpperCase(Locale.ENGLISH);
    }

    public static String extractFutureUnderlying(String tradingSymbol) {
        if (tradingSymbol == null || tradingSymbol.isBlank()) {
            return "";
        }
        return stripped(tradingSymbol).replaceFirst("\\d{1,4}[A-Z]{3}FUT(?:URES)?$", "");
    }

    private static String normalizeMonth(String month) {
        return month == null ? "" : month.trim().substring(0, 3).toUpperCase(Locale.ENGLISH);
    }

    public record ParsedSymbol(
            String underlying,
            String month,
            int day,
            String strike,
            OptionType optionType,
            boolean option
    ) {
    }
}
