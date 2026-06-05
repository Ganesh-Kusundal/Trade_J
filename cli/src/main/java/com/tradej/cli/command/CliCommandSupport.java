package com.tradej.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.dhan.config.DhanConfigPaths;
import com.tradej.cli.CliContext;
import com.tradej.cli.config.CliConfig;
import com.tradej.cli.output.OutputFormatter;
import com.tradej.cli.output.TablePrinter;
import com.tradej.cli.standalone.BrokerSession;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Shared helpers for CLI command handlers.
 */
public class CliCommandSupport {

    static final ObjectMapper MAPPER = new ObjectMapper();
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CliContext context;
    private final OutputFormatter out;

    public CliCommandSupport(CliContext context, OutputFormatter out) {
        this.context = context;
        this.out = out;
    }

    public CliContext context() {
        return context;
    }

    public OutputFormatter out() {
        return out;
    }

    public void requireAttach() {
        if (!context.attachReachable()) {
            throw new IllegalStateException(
                    "trade-app is not reachable at " + context.attachUrl()
                            + ". Start trade-app or use standalone broker commands.");
        }
    }

    public void confirmOrAbort(String message) {
        out.println("WARNING: " + message);
        out.print("Type YES to continue: ");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line == null || !"YES".equals(line.trim())) {
                throw new IllegalStateException("Aborted by user");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read confirmation", ex);
        }
    }

    public BrokerSession session() {
        return context.broker();
    }

    public PortfolioProvider portfolio() {
        return session().connection().portfolio();
    }

    public MarketDataProvider marketData() {
        return session().connection().marketData();
    }

    public OptionsProvider options() {
        return session().connection().options();
    }

    public MarginProvider margin() {
        return session().connection().margin();
    }

    public OrderQuery orderQuery() {
        return session().connection().orderQuery();
    }

    public OrderCommand orderCommand() {
        return session().connection().orders();
    }

    public static InstrumentKey instrument(String symbol, String segmentName) {
        return InstrumentKey.of(symbol, parseSegment(segmentName));
    }

    public static ExchangeSegment parseSegment(String segmentName) {
        return ExchangeSegment.valueOf(segmentName.trim().toUpperCase());
    }

    public void printJsonArrayTable(JsonNode array, String... fields) {
        List<String[]> rows = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode node : array) {
                String[] row = new String[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    JsonNode value = node.get(fields[i]);
                    row[i] = value == null || value.isNull() ? "" : value.asText();
                }
                rows.add(row);
            }
        }
        TablePrinter.print(fields, rows);
    }

    public static String formatMs(long epochMs) {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                Instant.ofEpochMilli(epochMs).atZone(IST));
    }

    public void guardReplay() {
        String mode = configuredRuntimeMode();
        if ("LIVE".equalsIgnoreCase(mode)) {
            throw new IllegalStateException(
                    "Replay blocked: trade.runtime.mode=LIVE in application.yml. Set REPLAY and restart trade-app.");
        }
    }

    public String configuredRuntimeMode() {
        try {
            Path yml = DhanConfigPaths.resolve("trade-app/src/main/resources/application.yml");
            String mode = readYamlValue(yml, "mode:");
            return mode == null || mode.isBlank() ? "UNKNOWN" : mode.trim();
        } catch (IOException ex) {
            return "UNKNOWN";
        }
    }

    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    public static String readYamlValue(Path yml, String keySuffix) throws IOException {
        for (String line : Files.readAllLines(yml)) {
            if (line.contains(keySuffix) && line.trim().startsWith("mode:")) {
                return line.substring(line.indexOf(':') + 1).trim();
            }
        }
        return null;
    }

    public static long defaultFromMs() {
        return Instant.now().minusSeconds(TimeUnit.DAYS.toSeconds(1)).toEpochMilli();
    }

    public static long defaultToMs() {
        return Instant.now().toEpochMilli();
    }

    public void requireStandaloneUpstox() {
        if (context.attachReachable()) {
            throw new IllegalStateException("Equity download requires standalone Upstox mode (do not use --attach).");
        }
        if (context.brokerType() != CliConfig.BrokerType.UPSTOX) {
            throw new IllegalStateException("Equity historical download is Upstox-only.");
        }
    }

    public void requireStandaloneDhan() {
        if (context.attachReachable()) {
            throw new IllegalStateException("Download jobs require standalone Dhan mode (do not use --attach).");
        }
        if (context.brokerType() != CliConfig.BrokerType.DHAN) {
            throw new IllegalStateException("Rolling option download is Dhan-only.");
        }
    }
}
