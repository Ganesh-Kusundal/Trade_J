package com.tradej.broker.upstox.instrument;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Loads the Upstox instrument master JSON and populates a resolver.
 */
public final class UpstoxInstrumentLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String COMPLETE_INSTRUMENT_URL =
            "https://assets.upstox.com/market-quote/instruments/exchange/complete.json.gz";

    private final HttpClient httpClient;

    public UpstoxInstrumentLoader() {
        this(HttpClient.newHttpClient());
    }

    public UpstoxInstrumentLoader(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Downloads the daily BOD instrument master and loads it into the resolver.
     */
    public Path downloadAndLoad(Path cacheDirectory, UpstoxInstrumentResolver resolver) throws IOException {
        Files.createDirectories(cacheDirectory);
        Path target = cacheDirectory.resolve("complete.json.gz");
        HttpRequest request = HttpRequest.newBuilder(URI.create(COMPLETE_INSTRUMENT_URL)).GET().build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("Upstox instrument download failed with status " + response.statusCode());
            }
            try (InputStream body = response.body()) {
                Files.copy(body, target);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Upstox instrument download interrupted", ex);
        }
        loadFromPath(target, resolver);
        return target;
    }

    public void loadFromPath(Path catalogPath, UpstoxInstrumentResolver resolver) throws IOException {
        try (InputStream input = openCatalogStream(catalogPath)) {
            parseJson(input, resolver);
        }
    }

    private InputStream openCatalogStream(Path catalogPath) throws IOException {
        InputStream raw = Files.newInputStream(catalogPath);
        if (catalogPath.toString().endsWith(".gz")) {
            return new GZIPInputStream(raw);
        }
        return raw;
    }

    /**
     * Parses instrument master JSON (list of instrument objects) and registers
     * each definition into the given resolver.
     */
    public void parseJson(InputStream jsonStream, UpstoxInstrumentResolver resolver) throws IOException {
        JsonNode root = MAPPER.readTree(jsonStream);
        JsonNode data = root.get("data");
        if (data == null) data = root;
        if (data.isArray()) {
            Iterator<JsonNode> elements = data.elements();
            while (elements.hasNext()) {
                JsonNode node = elements.next();
                UpstoxInstrumentDefinition def = parseDefinition(node);
                resolver.register(def);
            }
        }
    }

    private UpstoxInstrumentDefinition parseDefinition(JsonNode node) {
        long token = node.has("instrument_token")
                ? node.get("instrument_token").asLong()
                : node.has("exchange_token") ? node.get("exchange_token").asLong() : 0L;
        String instrumentKey = node.has("instrument_key") ? node.get("instrument_key").asText() : "";
        String symbol = node.has("trading_symbol") ? node.get("trading_symbol").asText()
                : node.has("tradingsymbol") ? node.get("tradingsymbol").asText() : "";
        String exchange = node.has("exchange") ? node.get("exchange").asText() : "";
        String segmentCode = node.has("segment") ? node.get("segment").asText() : exchange;
        ExchangeSegment segment = UpstoxSegmentMapper.fromUpstoxSegment(segmentCode);
        if (segment == ExchangeSegment.UNKNOWN) {
            segment = mapLegacyExchange(exchange);
        }


        String name = node.has("name") ? node.get("name").asText() : "";
        String isin = node.has("isin") ? node.get("isin").asText() : "";
        long lotSize = node.has("lot_size") ? node.get("lot_size").asLong() : 1L;
        long tickSize = node.has("tick_size") ? (long) (node.get("tick_size").asDouble() * 100) : 5L;
        OptionType optionType = node.has("option_type")
                ? OptionType.fromCode(node.get("option_type").asText()) : OptionType.UNKNOWN;
        long strike = node.has("strike_price") ? (long) (node.get("strike_price").asDouble() * 100)
                : node.has("strike") ? (long) (node.get("strike").asDouble() * 100) : 0L;
        LocalDate expiry = parseExpiry(node);
        String underlying = node.has("underlying_key") ? node.get("underlying_key").asText() : "";
        return new UpstoxInstrumentDefinition(token, instrumentKey, symbol, exchange, segment,
                name, isin, lotSize, tickSize, optionType, strike, expiry, underlying);
    }

    private static LocalDate parseExpiry(JsonNode node) {
        if (!node.has("expiry") || node.get("expiry").isNull()) {
            return null;
        }
        String expiryText = node.get("expiry").asText().trim();
        if (expiryText.isBlank() || expiryText.startsWith("0000")) {
            return null;
        }
        try {
            String datePart = expiryText.length() >= 10 ? expiryText.substring(0, 10) : expiryText;
            return LocalDate.parse(datePart, DATE_FMT);
        } catch (Exception ex) {
            return null;
        }
    }

    private static ExchangeSegment mapLegacyExchange(String exchange) {
        if (exchange == null) {
            return ExchangeSegment.NSE_EQ;
        }
        return switch (exchange.toUpperCase()) {
            case "NSE" -> ExchangeSegment.NSE_EQ;
            case "BSE" -> ExchangeSegment.BSE_EQ;
            case "NFO", "NSE_FO" -> ExchangeSegment.NSE_FNO;
            case "BFO", "BSE_FO" -> ExchangeSegment.BSE_FNO;
            case "MCX", "MCX_FO" -> ExchangeSegment.MCX_COMM;
            case "CDS", "NCD_FO" -> ExchangeSegment.NSE_CURRENCY;
            default -> ExchangeSegment.UNKNOWN;
        };
    }

    public static List<String> supportedDownloadSegments() {
        return List.of("NSE_EQ", "NSE_FO", "BSE_EQ", "BSE_FO", "NSE_INDEX", "MCX_FO");
    }
}
