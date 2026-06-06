package com.tradej.core.domain.instrument;

import com.tradej.core.domain.value.OptionType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single source of truth for F&amp;O and equity contract symbol formatting.
 *
 * <p>Canonical option: {@code BANKNIFTY 30 JUN 30000 CALL}
 * <p>Canonical future: {@code NIFTY 30 JUN FUT}
 */
public final class ContractSymbolNormalizer {
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final Pattern SPACED_OPTION_PATTERN = Pattern.compile(
            "^(?<underlying>[A-Z&\\-\\s]+)\\s+(?<day>\\d{1,2})\\s+(?<month>[A-Z]{3})\\s+(?<strike>\\d+(?:\\.\\d+)?)\\s+(?<type>CE|PE|CALL|PUT|C|P)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMPACT_OPTION_PATTERN = Pattern.compile(
            "^(?<underlying>[A-Z&]+)(?<day>\\d{2})(?<month>[A-Z]{3})(?<strike>\\d+(?:\\.\\d+)?)(?<type>CE|PE|CALL|PUT|C|P)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SPACED_FUTURE_PATTERN = Pattern.compile(
            "^(?<underlying>[A-Z&\\-\\s]+)\\s+(?<day>\\d{1,2})\\s+(?<month>[A-Z]{3})\\s+FUT(?:URES)?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMPACT_FUTURE_PATTERN = Pattern.compile(
            "^(?<underlying>[A-Z&]+)(?<day>\\d{2})(?<month>[A-Z]{3})FUT(?:URES)?$",
            Pattern.CASE_INSENSITIVE
    );

    private ContractSymbolNormalizer() {
    }

    public static ParsedContract parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim().toUpperCase(Locale.ENGLISH).replace('-', ' ').replace('_', ' ');

        Matcher spacedOption = SPACED_OPTION_PATTERN.matcher(cleaned);
        if (spacedOption.matches()) {
            return parsedOption(spacedOption);
        }
        Matcher compactOption = COMPACT_OPTION_PATTERN.matcher(stripped(cleaned));
        if (compactOption.matches()) {
            return parsedOption(compactOption);
        }
        Matcher spacedFuture = SPACED_FUTURE_PATTERN.matcher(cleaned);
        if (spacedFuture.matches()) {
            return parsedFuture(spacedFuture);
        }
        Matcher compactFuture = COMPACT_FUTURE_PATTERN.matcher(stripped(cleaned));
        if (compactFuture.matches()) {
            return parsedFuture(compactFuture);
        }
        return null;
    }

    /**
     * Normalizes any supported input (spaced, compact, CE/PE) to canonical CALL/PUT or FUT form.
     * Returns uppercase trimmed input when parsing does not apply (e.g. equity {@code SBIN}).
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        ParsedContract parsed = parse(raw);
        if (parsed == null) {
            return raw.trim().toUpperCase(Locale.ENGLISH);
        }
        if (parsed.option()) {
            return formatOption(parsed.underlying(), parsed.day(), parsed.month(), parsed.strike(), parsed.optionType());
        }
        return formatFuture(parsed.underlying(), parsed.day(), parsed.month());
    }

    /**
     * Strict normalization — throws if the symbol doesn't match any known contract pattern.
     * Unlike {@link #normalize(String)}, which silently returns the uppercased input for
     * unrecognized symbols, this method fails fast to catch bad data early.
     *
     * @param symbol the raw contract symbol to normalize
     * @return the canonical form of the symbol
     * @throws IllegalArgumentException if the symbol does not match any known pattern
     */
    public static String normalizeStrict(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Contract symbol must not be null or blank");
        }
        String normalized = normalize(symbol);
        if (!isKnownPattern(symbol)) {
            throw new IllegalArgumentException("Unrecognized contract symbol: " + symbol);
        }
        return normalized;
    }

    /**
     * Checks whether the given symbol matches any of the known contract regex patterns
     * (spaced/compact option or future).
     */
    private static boolean isKnownPattern(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        String cleaned = symbol.trim().toUpperCase(Locale.ENGLISH).replace('-', ' ').replace('_', ' ');
        String stripped = stripped(cleaned);
        return SPACED_OPTION_PATTERN.matcher(cleaned).matches()
                || COMPACT_OPTION_PATTERN.matcher(stripped).matches()
                || SPACED_FUTURE_PATTERN.matcher(cleaned).matches()
                || COMPACT_FUTURE_PATTERN.matcher(stripped).matches();
    }

    public static String canonicalOption(
            String underlying,
            LocalDate expiryDate,
            long strikePricePaisa,
            OptionType optionType
    ) {
        return getCanonicalSymbol(underlying, expiryDate, strikePricePaisa, optionType, true);
    }

    public static String canonicalFuture(String underlying, LocalDate expiryDate) {
        return getCanonicalSymbol(underlying, expiryDate, null, OptionType.UNKNOWN, false);
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
        String[] parts = dayMonth.split(" ");
        if (parts.length != 2) {
            return "";
        }
        int day = Integer.parseInt(parts[0]);
        String month = parts[1];
        if (option) {
            if (strikePricePaisa == null || optionType == null || optionType == OptionType.UNKNOWN) {
                return "";
            }
            long wholeRupees = strikePricePaisa / 100L;
            String strike = strikePricePaisa % 100L == 0L
                    ? Long.toString(wholeRupees)
                    : String.format(Locale.ENGLISH, "%.2f", strikePricePaisa / 100.0d);
            return formatOption(normalizedUnderlying, day, month, strike, optionType);
        }
        return formatFuture(normalizedUnderlying, day, month);
    }

    public static String stripped(String value) {
        return value == null ? "" : value.replace(" ", "").replace("-", "").replace("_", "").trim().toUpperCase(Locale.ENGLISH);
    }

    public static String extractFutureUnderlying(String tradingSymbol) {
        if (tradingSymbol == null || tradingSymbol.isBlank()) {
            return "";
        }
        ParsedContract parsed = parse(tradingSymbol);
        if (parsed != null && !parsed.option()) {
            return parsed.underlying();
        }
        return stripped(tradingSymbol).replaceFirst("\\d{2}[A-Z]{3}FUT(?:URES)?$", "");
    }

    private static ParsedContract parsedOption(Matcher matcher) {
        return new ParsedContract(
                matcher.group("underlying").trim(),
                normalizeMonth(matcher.group("month")),
                Integer.parseInt(matcher.group("day")),
                matcher.group("strike"),
                OptionType.fromCode(matcher.group("type")),
                true
        );
    }

    private static ParsedContract parsedFuture(Matcher matcher) {
        return new ParsedContract(
                matcher.group("underlying").trim(),
                normalizeMonth(matcher.group("month")),
                Integer.parseInt(matcher.group("day")),
                null,
                OptionType.UNKNOWN,
                false
        );
    }

    private static String formatOption(String underlying, int day, String month, String strike, OptionType optionType) {
        return (underlying + " "
                + String.format(Locale.ENGLISH, "%02d", day) + " "
                + month + " "
                + strike + " "
                + (optionType == OptionType.CALL ? "CALL" : "PUT")).trim();
    }

    private static String formatFuture(String underlying, int day, String month) {
        return (underlying + " "
                + String.format(Locale.ENGLISH, "%02d", day) + " "
                + month + " FUT").trim();
    }

    private static String normalizeMonth(String month) {
        return month == null ? "" : month.trim().substring(0, 3).toUpperCase(Locale.ENGLISH);
    }

    public record ParsedContract(
            String underlying,
            String month,
            int day,
            String strike,
            OptionType optionType,
            boolean option
    ) {
    }
}
