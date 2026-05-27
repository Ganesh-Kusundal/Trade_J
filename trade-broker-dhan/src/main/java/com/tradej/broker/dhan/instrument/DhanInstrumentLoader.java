package com.tradej.broker.dhan.instrument;

import com.tradej.broker.dhan.constants.DhanApiEndpoints;
import com.tradej.core.domain.value.PriceMath;
import com.tradej.core.domain.value.OptionType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DhanInstrumentLoader {
    private static final String INSTRUMENT_MASTER_URL = DhanApiEndpoints.INSTRUMENT_MASTER_URL;
    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm:ss", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("M/d/yyyy H:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)
    };

    public List<DhanInstrumentDefinition> load(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("Instrument catalog is empty: " + path);
            }
            return parse(lines);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read instrument catalog " + path, ex);
        }
    }

    public List<DhanInstrumentDefinition> loadFromDailyCache(Path cacheDirectory, boolean forceRefresh) {
        return load(ensureDailySnapshot(cacheDirectory, forceRefresh));
    }

    public Path ensureDailySnapshot(Path cacheDirectory, boolean forceRefresh) {
        try {
            Files.createDirectories(cacheDirectory);
            Path snapshot = cacheDirectory.resolve("api-scrip-master-" + LocalDate.now() + ".csv");
            if (forceRefresh || Files.notExists(snapshot) || Files.size(snapshot) == 0L) {
                try (InputStream stream = URI.create(INSTRUMENT_MASTER_URL).toURL().openStream()) {
                    Files.copy(stream, snapshot, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return snapshot;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to prepare cached Dhan instrument master", ex);
        }
    }

    List<DhanInstrumentDefinition> parse(List<String> lines) {
        String[] headers = splitCsv(lines.getFirst());
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            index.put(normalize(headers[i]), i);
        }
        List<DhanInstrumentDefinition> instruments = new ArrayList<>();
        for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber);
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] cells = splitCsv(line);
            DhanInstrumentDefinition definition = parseRow(index, cells, lineNumber + 1);
            if (definition != null) {
                instruments.add(definition);
            }
        }
        if (instruments.isEmpty()) {
            throw new IllegalArgumentException("Instrument catalog did not yield any resolvable instruments");
        }
        return instruments;
    }

    private DhanInstrumentDefinition parseRow(Map<String, Integer> index, String[] cells, int lineNumber) {
        String securityId = required(index, cells, "sem_smst_security_id", "securityid", "security_id");
        String symbol = required(index, cells, "sem_trading_symbol", "symbol", "tradingsymbol");
        String customSymbol = firstNonBlank(index, cells, "sem_custom_symbol", "canonicalsymbol", "canonical_symbol");
        String exchangeId = firstNonBlank(index, cells, "sem_exm_exch_id", "exchange", "exchangeid");
        String rawSegment = firstNonBlank(index, cells, "sem_segment", "segment", "exchange_segment");
        com.tradej.core.domain.value.ExchangeSegment segment;
        if (!exchangeId.isBlank() && !rawSegment.isBlank()) {
            segment = DhanSegmentMapper.fromCsv(exchangeId, rawSegment);
            if (segment == com.tradej.core.domain.value.ExchangeSegment.UNKNOWN) {
                segment = DhanSegmentMapper.fromValue(rawSegment);
            }
        } else {
            segment = DhanSegmentMapper.fromValue(rawSegment);
        }
        if (segment == com.tradej.core.domain.value.ExchangeSegment.UNKNOWN) {
            return null;
        }
        String instrumentType = firstNonBlank(index, cells, "sem_instrument_name", "instrumenttype", "instrument_type", "type");
        LocalDate expiry = parseDate(firstNonBlank(index, cells, "sem_expiry_date", "expiry"));
        Long strikePricePaisa = parsePricePaisa(firstNonBlank(index, cells, "sem_strike_price", "strikeprice", "strike_price"));
        OptionType optionType = OptionType.fromCode(firstNonBlank(index, cells, "sem_option_type", "optiontype", "option_type"));
        String underlying = resolveUnderlying(index, cells, customSymbol, symbol, instrumentType);
        long lotSize = parseLong(firstNonBlank(index, cells, "sem_lot_units", "lotsize", "lot_size"), 1L);
        long tickSizePaisa = PriceMath.toPaisa(firstNonBlank(index, cells, "sem_tick_size", "ticksize", "tick_size"));
        String canonicalSymbol = canonicalSymbol(symbol, customSymbol, instrumentType, underlying, expiry, strikePricePaisa, optionType);
        return new DhanInstrumentDefinition(
                symbol.trim(),
                canonicalSymbol,
                segment.exchange(),
                segment,
                securityId.trim(),
                instrumentType.toUpperCase(Locale.ENGLISH),
                underlying,
                expiry,
                strikePricePaisa,
                optionType,
                lotSize,
                tickSizePaisa,
                firstNonBlank(index, cells, "underlyingsecurityid", "underlying_security_id", "sem_underlying_security_id")
        );
    }

    private String canonicalize(String canonicalSymbol, String fallback) {
        String candidate = canonicalSymbol == null || canonicalSymbol.isBlank() ? fallback : canonicalSymbol;
        return candidate.trim().toUpperCase(Locale.ENGLISH);
    }

    private String canonicalSymbol(
            String symbol,
            String customSymbol,
            String instrumentType,
            String underlying,
            LocalDate expiry,
            Long strikePricePaisa,
            OptionType optionType
    ) {
        String normalizedType = instrumentType == null ? "" : instrumentType.toUpperCase(Locale.ENGLISH);
        if (optionType != OptionType.UNKNOWN) {
            String canonical = DhanSymbolNormalizer.canonicalOptionSymbol(underlying, expiry, strikePricePaisa, optionType);
            if (!canonical.isBlank()) {
                return canonical;
            }
        }
        if (normalizedType.startsWith("FUT")) {
            String canonical = DhanSymbolNormalizer.canonicalFutureSymbol(underlying, expiry);
            if (!canonical.isBlank()) {
                return canonical;
            }
        }
        if ("EQUITY".equals(normalizedType) || "INDEX".equals(normalizedType)) {
            return canonicalize(symbol, symbol);
        }
        return canonicalize(customSymbol, symbol);
    }

    private String resolveUnderlying(
            Map<String, Integer> index,
            String[] cells,
            String customSymbol,
            String symbol,
            String instrumentType
    ) {
        String direct = normalizeUnderlying(firstNonBlank(index, cells,
                "underlyingsymbol",
                "underlying_symbol",
                "underlying",
                "sem_underlying_symbol"));
        if (!direct.isBlank()) {
            return direct;
        }
        String fromCustom = normalizeUnderlying(customSymbol);
        if (!fromCustom.isBlank()) {
            return fromCustom;
        }
        if (instrumentType != null && instrumentType.toUpperCase(Locale.ENGLISH).startsWith("FUT")) {
            return DhanSymbolNormalizer.extractFutureUnderlying(symbol);
        }
        return "";
    }

    private String normalizeUnderlying(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toUpperCase(Locale.ENGLISH);
        int firstSpace = normalized.indexOf(' ');
        return firstSpace > 0 ? normalized.substring(0, firstSpace) : normalized;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String candidate = value.trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(candidate, formatter);
            } catch (DateTimeParseException ignored) {
                // try next formatter
            }
        }
        return null;
    }

    private Long parsePricePaisa(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PriceMath.toPaisa(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return (long) Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String required(Map<String, Integer> index, String[] cells, String... names) {
        String value = firstNonBlank(index, cells, names);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing required instrument column value for " + List.of(names));
        }
        return value;
    }

    private String firstNonBlank(Map<String, Integer> index, String[] cells, String... names) {
        for (String name : names) {
            Integer column = index.get(normalize(name));
            if (column != null && column < cells.length) {
                String value = unquote(cells[column]);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return "";
    }

    private String[] splitCsv(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                continue;
            }
            if (c == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        values.add(current.toString());
        return values.toArray(String[]::new);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("_", "").replace("-", "").trim().toLowerCase(Locale.ENGLISH);
    }

    private String unquote(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
