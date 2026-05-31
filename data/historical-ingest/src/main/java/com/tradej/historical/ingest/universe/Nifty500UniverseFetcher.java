package com.tradej.historical.ingest.universe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Downloads Nifty 500 constituent CSV from NIFTY Indices and parses symbol + industry columns.
 */
public final class Nifty500UniverseFetcher {

    public static final String DEFAULT_UNIVERSE_URL =
            "https://www.niftyindices.com/IndexConstituent/ind_nifty500list.csv";

    private static final Logger log = LoggerFactory.getLogger(Nifty500UniverseFetcher.class);

    private final HttpClient httpClient;
    private final String universeUrl;

    public Nifty500UniverseFetcher(HttpClient httpClient, String universeUrl) {
        this.httpClient = httpClient;
        this.universeUrl = universeUrl == null || universeUrl.isBlank() ? DEFAULT_UNIVERSE_URL : universeUrl;
    }

    public Nifty500UniverseFetcher() {
        this(HttpClient.newHttpClient(), DEFAULT_UNIVERSE_URL);
    }

    public List<Nifty500Constituent> fetch() throws IOException, InterruptedException {
        log.info("Downloading Nifty 500 universe from {}", universeUrl);
        HttpRequest request = HttpRequest.newBuilder(URI.create(universeUrl))
                .header("User-Agent", "TradeJ/1.0")
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Nifty 500 download failed with HTTP " + response.statusCode());
        }
        String body = new String(response.body(), StandardCharsets.UTF_8);
        return parseCsv(body, LocalDate.now());
    }

    static List<Nifty500Constituent> parseCsv(String csvBody, LocalDate asOfDate) throws IOException {
        List<Nifty500Constituent> constituents = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(csvBody))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return List.of();
            }
            Map<String, Integer> columns = parseHeader(headerLine);
            int symbolIdx = requireColumn(columns, "symbol");
            int industryIdx = optionalColumn(columns, "industry");
            int companyIdx = optionalColumn(columns, "company name", "company");
            int isinIdx = optionalColumn(columns, "isin code", "isin");
            int sectorIdx = optionalColumn(columns, "macro sector", "sector");

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> fields = splitCsvLine(line);
                if (fields.size() <= symbolIdx) {
                    continue;
                }
                String symbol = normalizeSymbol(fields.get(symbolIdx));
                if (symbol.isBlank()) {
                    continue;
                }
                String industry = readField(fields, industryIdx);
                String company = readField(fields, companyIdx);
                String isin = readField(fields, isinIdx);
                String sector = readField(fields, sectorIdx);
                if (sector.isBlank()) {
                    sector = industry;
                }
                constituents.add(new Nifty500Constituent(symbol, company, industry, sector, isin, asOfDate));
            }
        }
        return List.copyOf(dedupeBySymbol(constituents));
    }

    private static List<Nifty500Constituent> dedupeBySymbol(List<Nifty500Constituent> constituents) {
        Map<String, Nifty500Constituent> unique = new LinkedHashMap<>();
        for (Nifty500Constituent constituent : constituents) {
            unique.putIfAbsent(constituent.symbol(), constituent);
        }
        return List.copyOf(unique.values());
    }

    private static String normalizeSymbol(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace("-", "");
    }

    private static Map<String, Integer> parseHeader(String headerLine) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        List<String> headers = splitCsvLine(headerLine);
        for (int i = 0; i < headers.size(); i++) {
            columns.put(headers.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        return columns;
    }

    private static int requireColumn(Map<String, Integer> columns, String name) {
        Integer idx = columns.get(name.toLowerCase(Locale.ROOT));
        if (idx == null) {
            throw new IllegalArgumentException("CSV missing required column: " + name);
        }
        return idx;
    }

    private static int optionalColumn(Map<String, Integer> columns, String... names) {
        for (String name : names) {
            Integer idx = columns.get(name.toLowerCase(Locale.ROOT));
            if (idx != null) {
                return idx;
            }
        }
        return -1;
    }

    private static String readField(List<String> fields, int index) {
        if (index < 0 || index >= fields.size()) {
            return "";
        }
        return fields.get(index).trim();
    }

    static List<String> splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (ch == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        fields.add(current.toString());
        return fields;
    }
}
