package com.tradej.broker.icici.instrument;

import com.tradej.broker.icici.constants.BreezeApiEndpoints;
import com.tradej.core.domain.value.ExchangeSegment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Loads ICICI SecurityMaster and maps symbols to script tokens (e.g. 4.1!1594).
 */
public final class BreezeInstrumentLoader {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient httpClient;

    public BreezeInstrumentLoader() {
        this(HttpClient.newHttpClient());
    }

    public BreezeInstrumentLoader(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Map<String, BreezeInstrumentDefinition> loadFromRemote() throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BreezeApiEndpoints.SECURITY_MASTER_URL))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("SecurityMaster download failed with HTTP " + response.statusCode());
            }
            try (InputStream body = response.body()) {
                return parseZip(body);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("SecurityMaster download interrupted", ex);
        }
    }

    public Map<String, BreezeInstrumentDefinition> loadFromPath(Path zipPath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(zipPath)) {
            return parseZip(inputStream);
        }
    }

    private Map<String, BreezeInstrumentDefinition> parseZip(InputStream zipStream) throws IOException {
        Map<String, BreezeInstrumentDefinition> bySymbol = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                try {
                    ExchangeSegment segment = segmentForFile(entry.getName());
                    if (segment == null || !entry.getName().toLowerCase().endsWith(".txt")) {
                        BreezeSecurityMasterCsv.drain(zip);
                        continue;
                    }
                    parseMasterFile(entry.getName(), segment, zip, bySymbol);
                } finally {
                    zip.closeEntry();
                }
            }
        }
        return bySymbol;
    }

    private void parseMasterFile(
            String fileName,
            ExchangeSegment segment,
            InputStream inputStream,
            Map<String, BreezeInstrumentDefinition> bySymbol
    ) throws IOException {
        BufferedReader reader = BreezeSecurityMasterCsv.reader(inputStream);
        reader.readLine(); // header
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            List<String> columns = BreezeSecurityMasterCsv.parseLine(line);
            if (columns.size() < 2) {
                continue;
            }
            String token = BreezeSecurityMasterCsv.field(columns, 0);
            if (token.isBlank() || "0".equals(token)) {
                continue;
            }
            String shortName = segment == ExchangeSegment.NSE_EQ
                    ? BreezeSecurityMasterCsv.field(columns, 1)
                    : BreezeSecurityMasterCsv.field(columns, 2);
            String nseTradingSymbol = BreezeSecurityMasterCsv.field(columns, columns.size() - 1);
            if (shortName.isBlank()) {
                shortName = nseTradingSymbol;
            }
            if (shortName.isBlank()) {
                continue;
            }
            String tradingSymbol = nseTradingSymbol.isBlank() ? shortName : nseTradingSymbol;
            String scriptCode = scriptCodeForSegment(segment, token);
            BreezeInstrumentDefinition definition = new BreezeInstrumentDefinition(
                    shortName,
                    tradingSymbol,
                    segment,
                    token,
                    scriptCode,
                    apiExchangeCode(segment)
            );
            registerDefinition(bySymbol, definition, shortName, segment);
            if (!nseTradingSymbol.isBlank() && !nseTradingSymbol.equalsIgnoreCase(shortName)) {
                registerDefinition(bySymbol, definition, nseTradingSymbol, segment);
            }
        }
    }

    private static void registerDefinition(
            Map<String, BreezeInstrumentDefinition> bySymbol,
            BreezeInstrumentDefinition definition,
            String alias,
            ExchangeSegment segment
    ) {
        bySymbol.putIfAbsent(key(alias, segment), definition);
    }

    private static ExchangeSegment segmentForFile(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.contains("cdnse")) {
            return null;
        }
        if (lower.contains("fonsescrip")) {
            return ExchangeSegment.NSE_FNO;
        }
        if (lower.contains("nsescrip")) {
            return ExchangeSegment.NSE_EQ;
        }
        return null;
    }

    private static String scriptCodeForSegment(ExchangeSegment segment, String token) {
        return switch (segment) {
            case NSE_EQ -> "4.1!" + token;
            case NSE_FNO -> "13.1!" + token;
            default -> "4.1!" + token;
        };
    }

    private static String apiExchangeCode(ExchangeSegment segment) {
        return switch (segment) {
            case NSE_EQ -> "NSE";
            case NSE_FNO -> "NFO";
            default -> "NSE";
        };
    }

    static String key(String stockCode, ExchangeSegment segment) {
        return segment.name() + ":" + stockCode.toUpperCase();
    }
}
