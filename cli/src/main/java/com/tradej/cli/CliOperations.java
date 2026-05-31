package com.tradej.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.dhan.config.DhanConfigPaths;
import com.tradej.cli.attach.AttachClient;
import com.tradej.cli.config.CliConfig;
import com.tradej.cli.download.CliDownloadSupport;
import com.tradej.cli.output.OutputFormatter;
import com.tradej.cli.output.TablePrinter;
import com.tradej.cli.scan.ScanProfileJsonLoader;
import com.tradej.cli.standalone.BrokerSession;
import com.tradej.scanner.engine.ScanDependencies;
import com.tradej.scanner.engine.ScanEngine;
import com.tradej.scanner.model.ScanHit;
import com.tradej.scanner.model.ScanProfile;
import com.tradej.scanner.model.ScanResult;
import com.tradej.scanner.model.OptionScanSpec;
import com.tradej.scanner.option.OptionContractHit;
import com.tradej.scanner.option.OptionExpiryPolicy;
import com.tradej.scanner.option.OptionLiquidityScanner;
import com.tradej.scanner.option.OptionScanRequest;
import com.tradej.scanner.option.OptionScanResult;
import com.tradej.scanner.option.OptionSideFilter;
import com.tradej.core.domain.instrument.ContractSymbolNormalizer;
import com.tradej.core.domain.instrument.RollingExpiryKind;
import com.tradej.core.domain.instrument.RollingExpiryRoll;
import com.tradej.core.domain.instrument.RollingOptionSeriesKey;
import com.tradej.core.domain.instrument.StrikeOffset;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.LivePnlSnapshot;
import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.model.RollingOptionHistoryRequest;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.StrikeSelectionKind;
import com.tradej.core.domain.value.Validity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class CliOperations {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CliContext context;
    private final OutputFormatter out;

    public CliOperations(CliContext context) {
        this.context = context;
        this.out = new OutputFormatter(context.json());
    }

    public OutputFormatter output() {
        return out;
    }

    public CliContext context() {
        return context;
    }

    public void status() {
        requireAttach();
        JsonNode health = context.attach().health();
        JsonNode summary = context.attach().summary();
        JsonNode runtime = context.attach().runtime();
        if (context.json()) {
            out.print(Map.of("health", health, "summary", summary, "runtime", runtime));
            return;
        }
        out.println("=== Health ===");
        out.println(health.toPrettyString());
        out.println("=== Summary ===");
        out.println(summary.toPrettyString());
        out.println("=== Runtime ===");
        out.println(runtime.toPrettyString());
    }

    public void runtime() {
        requireAttach();
        out.print(context.attach().runtime());
    }

    public void pipeline() {
        requireAttach();
        out.print(context.attach().pipeline());
    }

    public void strategies() {
        requireAttach();
        out.print(context.attach().strategies());
    }

    public void summary() {
        requireAttach();
        out.print(context.attach().summary());
    }

    public void orders() {
        requireAttach();
        printReadModel(true, false);
    }

    public void positions() {
        requireAttach();
        printReadModel(false, true);
    }

    public void readModel() {
        requireAttach();
        printReadModel(true, true);
    }

    private void printReadModel(boolean orders, boolean positions) {
        JsonNode model = context.attach().readModel();
        if (context.json()) {
            out.print(model);
            return;
        }
        if (orders && model.has("orders")) {
            out.println("Orders:");
            printJsonArrayTable(model.get("orders"),
                    "orderId", "symbol", "status", "quantity", "pricePaisa");
        }
        if (positions && model.has("positions")) {
            out.println("Positions:");
            printJsonArrayTable(model.get("positions"),
                    "symbol", "quantity", "averagePricePaisa", "lastPricePaisa");
        }
    }

    public void balance() {
        Balance balance = portfolio().getBalance();
        if (context.json()) {
            out.print(balance);
            return;
        }
        out.println("Cash:         " + balance.cashPaisa() + " paisa");
        out.println("Utilized:     " + balance.utilizedPaisa() + " paisa");
        out.println("Withdrawable: " + balance.withdrawablePaisa() + " paisa");
    }

    public void holdings() {
        List<Holding> holdings = portfolio().getHoldings();
        if (context.json()) {
            out.print(holdings);
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Holding holding : holdings) {
            rows.add(new String[]{
                    holding.symbol(),
                    String.valueOf(holding.totalQuantity()),
                    String.valueOf(holding.averagePricePaisa())
            });
        }
        TablePrinter.print(new String[]{"Symbol", "Qty", "AvgPaisa"}, rows);
    }

    public void brokerPositions() {
        List<Position> positions = portfolio().getPositions();
        if (context.json()) {
            out.print(positions);
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Position position : positions) {
            rows.add(new String[]{
                    position.symbol(),
                    position.exchangeSegment().name(),
                    String.valueOf(position.quantity()),
                    String.valueOf(position.averagePricePaisa()),
                    String.valueOf(position.lastPricePaisa())
            });
        }
        TablePrinter.print(new String[]{"Symbol", "Segment", "Qty", "Avg", "LTP"}, rows);
    }

    public void refreshCatalog(boolean forceRefresh) {
        Path path = session().refreshInstrumentCatalog(forceRefresh);
        int size = session().catalogSize();
        if (context.json()) {
            out.print(Map.of(
                    "catalogPath", path.toString(),
                    "instrumentCount", size,
                    "forceRefresh", forceRefresh,
                    "profile", context.profile().name()
            ));
            return;
        }
        out.println("Instrument catalog refreshed.");
        out.println("  Path:        " + path);
        out.println("  Instruments: " + size);
        out.println("  Profile:     " + context.profile());
        out.println("  Force:       " + forceRefresh);
    }

    public void ltp(String symbol, String segmentName) {
        session().ensureCatalogLoaded();
        InstrumentKey key = instrument(symbol, segmentName);
        long ltp = marketData().getLtpPaisa(key);
        if (context.json()) {
            out.print(Map.of("symbol", symbol, "segment", segmentName, "ltpPaisa", ltp));
        } else {
            out.println(symbol + " " + segmentName + " LTP = " + ltp + " paisa");
        }
    }

    public void quote(String symbol, String segmentName) {
        session().ensureCatalogLoaded();
        Quote quote = marketData().getQuote(instrument(symbol, segmentName));
        out.print(quote);
    }

    public void depth(String symbol, String segmentName) {
        session().ensureCatalogLoaded();
        MarketDepth depth = marketData().getDepth(instrument(symbol, segmentName));
        out.print(depth);
    }

    public void ohlc(String symbol, String segmentName) {
        session().ensureCatalogLoaded();
        Quote quote = marketData().getOhlcSnapshot(instrument(symbol, segmentName));
        out.print(quote);
    }

    public void candles(String symbol, String segmentName, String interval, LocalDate from, LocalDate to) {
        session().ensureCatalogLoaded();
        List<Candle> candles = marketData().getCandles(new CandleHistoryRequest(
                instrument(symbol, segmentName), interval, from, to));
        if (context.json()) {
            out.print(candles);
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Candle candle : candles) {
            rows.add(new String[]{
                    formatMs(candle.startTimeMs()),
                    String.valueOf(candle.openPaisa()),
                    String.valueOf(candle.highPaisa()),
                    String.valueOf(candle.lowPaisa()),
                    String.valueOf(candle.closePaisa()),
                    String.valueOf(candle.volume())
            });
        }
        TablePrinter.print(new String[]{"Start", "Open", "High", "Low", "Close", "Vol"}, rows);
    }

    public void orderBook() {
        List<Order> orders = orderQuery().getOrderBook();
        if (context.json()) {
            out.print(orders);
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Order order : orders) {
            rows.add(new String[]{
                    order.orderId(),
                    order.symbol(),
                    order.status().name(),
                    String.valueOf(order.quantity()),
                    String.valueOf(order.pricePaisa())
            });
        }
        TablePrinter.print(new String[]{"OrderId", "Symbol", "Status", "Qty", "Price"}, rows);
    }

    public void trades() {
        List<Trade> trades = orderQuery().getTradeBook();
        out.print(trades);
    }

    public void order(String orderId) {
        Order order = orderQuery().getOrder(orderId);
        out.print(order);
    }

    public void livePnl() {
        session().ensureCatalogLoaded();
        List<Position> positions = portfolio().getPositions();
        if (positions.isEmpty()) {
            out.print(new LivePnlSnapshot(0L, 0L));
            return;
        }
        List<InstrumentKey> keys = positions.stream()
                .map(p -> new InstrumentKey(p.symbol(), p.exchangeSegment()))
                .toList();
        Map<InstrumentKey, Long> ltps = marketData().getLtpBatch(keys);
        long netPnl = 0L;
        long netQty = 0L;
        for (Position position : positions) {
            InstrumentKey key = new InstrumentKey(position.symbol(), position.exchangeSegment());
            long last = ltps.getOrDefault(key, position.lastPricePaisa());
            long qty = position.quantity();
            netQty += qty;
            netPnl += (last - position.averagePricePaisa()) * qty;
        }
        out.print(new LivePnlSnapshot(netPnl, netQty));
    }

    public void expiries(String underlying, String segmentName) {
        session().ensureCatalogLoaded();
        List<LocalDate> expiries = options().getExpiries(underlying, parseSegment(segmentName));
        out.print(expiries);
    }

    public void chain(String underlying, String segmentName, LocalDate expiry) {
        session().ensureCatalogLoaded();
        OptionChainSnapshot chain = options().getOptionChain(underlying, parseSegment(segmentName), expiry);
        if (context.json()) {
            out.print(chain);
            return;
        }
        out.println("Underlying " + underlying + " expiry " + expiry + " spot=" + chain.spotPricePaisa());
        out.println("Canonical naming: {UNDERLYING} {dd} {MMM} {STRIKE} CALL|PUT");
        List<String[]> rows = new ArrayList<>();
        for (OptionChainEntry entry : chain.strikes()) {
            String callName = entry.call() == null ? "-" : entry.call().instrument().canonicalSymbol();
            String putName = entry.put() == null ? "-" : entry.put().instrument().canonicalSymbol();
            rows.add(new String[]{
                    String.valueOf(entry.strikePricePaisa()),
                    callName,
                    putName,
                    entry.call() == null ? "-" : String.valueOf(entry.call().ltpPaisa()),
                    entry.put() == null ? "-" : String.valueOf(entry.put().ltpPaisa())
            });
        }
        TablePrinter.print(new String[]{"StrikePaisa", "CallSymbol", "PutSymbol", "CallLTP", "PutLTP"}, rows);
    }

    public void strike(String underlying, String segmentName, String kind, int depth) {
        session().ensureCatalogLoaded();
        ExchangeSegment segment = parseSegment(segmentName);
        long spot;
        try {
            spot = marketData().getLtpPaisa(InstrumentKey.of(underlying, segment));
        } catch (RuntimeException ltpError) {
            // Dhan index LTP endpoint can return sparse payloads for IDX_I.
            // Fallback to spot from option-chain snapshot so ATM/OTM/ITM still resolves.
            List<LocalDate> expiries = options().getExpiries(underlying, segment);
            if (expiries.isEmpty()) {
                throw new IllegalStateException("Unable to resolve spot: no option expiries for " + underlying
                        + " on " + segment + " and LTP lookup failed: " + ltpError.getMessage(), ltpError);
            }
            LocalDate expiry = expiries.getFirst();
            OptionChainSnapshot chain = options().getOptionChain(underlying, segment, expiry);
            spot = chain.spotPricePaisa();
            if (spot <= 0L) {
                throw new IllegalStateException("Unable to resolve spot from option chain for " + underlying
                        + " expiry " + expiry + " after LTP lookup failed: " + ltpError.getMessage(), ltpError);
            }
            out.println("LTP unavailable; using option-chain spot for " + underlying + " expiry " + expiry + ".");
        }
        StrikeSelectionKind selection = StrikeSelectionKind.valueOf(kind.trim().toUpperCase());
        long callStrike = options().selectStrikePaisa(underlying, segment, spot, OptionType.CALL, selection, depth);
        long putStrike = options().selectStrikePaisa(underlying, segment, spot, OptionType.PUT, selection, depth);
        out.print(Map.of(
                "underlying", underlying,
                "spotPaisa", spot,
                "selection", selection.name(),
                "depth", depth,
                "callStrikePaisa", callStrike,
                "putStrikePaisa", putStrike,
                "callStrikeRupees", callStrike / 100L,
                "putStrikeRupees", putStrike / 100L
        ));
    }

    public void margin(
            String symbol,
            String segmentName,
            String side,
            long quantity,
            String productType,
            String orderType,
            long pricePaisa
    ) {
        session().ensureCatalogLoaded();
        MarginEstimate estimate = margin().estimateMargin(new MarginEstimateRequest(
                symbol,
                parseSegment(segmentName),
                Side.valueOf(side.toUpperCase()),
                quantity,
                ProductType.valueOf(productType.toUpperCase()),
                OrderType.valueOf(orderType.toUpperCase()),
                pricePaisa,
                0L
        ));
        out.print(estimate);
    }

    public void rollingOption(
            String underlying,
            String segmentName,
            int intervalMinutes,
            String expiryFlag,
            int expiryCode,
            String strike,
            String optionType,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        String enabled = firstNonBlank(
                System.getenv("DHAN_ROLLING_OPTION_TEST_ENABLED"),
                System.getProperty("dhan.rollingOptionTestEnabled"),
                "false"
        );
        if (!"true".equalsIgnoreCase(enabled)) {
            out.println("Rolling option history is opt-in. Set DHAN_ROLLING_OPTION_TEST_ENABLED=true to enable.");
            return;
        }
        session().ensureCatalogLoaded();
        var seriesKey = new RollingOptionSeriesKey(
                ContractSymbolNormalizer.normalize(underlying),
                parseSegment(segmentName),
                new RollingExpiryRoll(RollingExpiryKind.fromCode(expiryFlag), expiryCode),
                StrikeOffset.parseSpec(strike),
                OptionType.fromCode(optionType),
                intervalMinutes
        );
        var data = options().getExpiredOptionHistory(new RollingOptionHistoryRequest(
                seriesKey,
                fromDate,
                toDate
        ));
        out.print(data);
    }

    public void streamReadModel(int seconds) {
        requireAttach();
        context.attach().streamReadModel(seconds, line -> out.println(line));
    }

    public void killSwitch(boolean enabled) {
        if (context.profile() == CliConfig.Profile.LIVE && enabled && !context.yes()) {
            confirmOrAbort("Enable kill-switch on LIVE broker session?");
        }
        if (context.attachReachable()) {
            out.print(context.attach().killSwitch(enabled));
            return;
        }
        boolean ack = orderCommand().setKillSwitch(enabled);
        out.print(Map.of("enabled", enabled, "acknowledged", ack, "backend", "standalone"));
    }

    public void reconcile(String jsonPayload) throws IOException {
        requireAttach();
        Map<String, Long> expected = MAPPER.readValue(jsonPayload, MAPPER.getTypeFactory()
                .constructMapType(LinkedHashMap.class, String.class, Long.class));
        if (!context.yes()) {
            confirmOrAbort("Trigger reconcile with payload: " + expected);
        }
        out.print(context.attach().reconcile(expected));
    }

    public void showRiskConfig() throws IOException {
        Path yml = DhanConfigPaths.resolve("trade-app/src/main/resources/application.yml");
        if (!Files.exists(yml)) {
            out.error("application.yml not found at " + yml);
            return;
        }
        boolean inRisk = false;
        for (String line : Files.readAllLines(yml)) {
            if (line.startsWith("  risk:")) {
                inRisk = true;
            } else if (inRisk && line.startsWith("  ") && !line.startsWith("    ")) {
                break;
            }
            if (inRisk) {
                out.println(line);
            }
        }
        String mode = readYamlValue(yml, "mode:");
        if (mode != null) {
            out.println("Configured runtime mode (application.yml): " + mode.trim());
        }
    }

    public void historicalCandles(String symbol, String interval, long from, long to, int limit) {
        requireAttach();
        out.print(context.attach().historicalCandles(symbol, interval, from, to, limit));
    }

    public void historicalTicks(String symbol, long from, long to, int limit) {
        requireAttach();
        out.print(context.attach().historicalTicks(symbol, from, to, limit));
    }

    public void historicalOrders(String symbol, long from, long to, int limit) {
        requireAttach();
        out.print(context.attach().historicalOrders(symbol, from, to, limit));
    }

    public void historicalFills(String symbol, long from, long to, int limit) {
        requireAttach();
        out.print(context.attach().historicalFills(symbol, from, to, limit));
    }

    public void historicalStats(String symbol, long from, long to) {
        requireAttach();
        out.print(context.attach().historicalStats(symbol, from, to));
    }

    public void replayTicks(String symbol, long from, long to) {
        requireAttach();
        guardReplay();
        if (!context.yes()) {
            confirmOrAbort("Replay ticks for " + symbol + " from " + from + " to " + to);
        }
        out.print(context.attach().replayTicks(symbol, from, to));
    }

    public void replayCandles(String symbol, String interval, long from, long to) {
        requireAttach();
        guardReplay();
        if (!context.yes()) {
            confirmOrAbort("Replay candles for " + symbol);
        }
        out.print(context.attach().replayCandles(symbol, interval, from, to));
    }

    public void replayFills(String symbol, long from, long to) {
        requireAttach();
        guardReplay();
        if (!context.yes()) {
            confirmOrAbort("Replay fill events");
        }
        out.print(context.attach().replayFills(symbol, from, to));
    }

    public void replayOrders(String symbol, long from, long to) {
        requireAttach();
        guardReplay();
        if (!context.yes()) {
            confirmOrAbort("Replay orders");
        }
        out.print(context.attach().replayOrders(symbol, from, to));
    }

    public void replayChronicle(String eventType) {
        requireAttach();
        guardReplay();
        if (!context.yes()) {
            confirmOrAbort("Replay chronicle events of type " + eventType);
        }
        out.print(context.attach().replayChronicle(eventType));
    }

    public void placeSandboxOrder(
            String symbol,
            String segmentName,
            String side,
            long quantity,
            String orderType,
            long pricePaisa,
            String productType
    ) {
        if (context.profile() != CliConfig.Profile.SANDBOX) {
            throw new IllegalStateException("place is allowed only with --profile sandbox");
        }
        OrderRequest request = new OrderRequest(
                symbol,
                parseSegment(segmentName),
                Side.valueOf(side.toUpperCase()),
                quantity,
                OrderType.valueOf(orderType.toUpperCase()),
                pricePaisa,
                0L,
                ProductType.valueOf(productType.toUpperCase()),
                Validity.DAY,
                "cli-" + System.currentTimeMillis()
        );
        if (!context.yes()) {
            confirmOrAbort("Place sandbox order: " + request);
        }
        Order placed = orderCommand().placeOrder(request);
        out.print(placed);
    }

    public void cancelOrder(String orderId) {
        if (context.profile() != CliConfig.Profile.SANDBOX) {
            throw new IllegalStateException("cancel is allowed only with --profile sandbox");
        }
        if (!context.yes()) {
            confirmOrAbort("Cancel order " + orderId);
        }
        boolean cancelled = orderCommand().cancelOrder(orderId);
        out.print(Map.of("orderId", orderId, "cancelled", cancelled));
    }

    public void modifyOrder(String orderId, long quantity, long pricePaisa) {
        if (context.profile() != CliConfig.Profile.SANDBOX) {
            throw new IllegalStateException("modify is allowed only with --profile sandbox");
        }
        if (!context.yes()) {
            confirmOrAbort("Modify order " + orderId);
        }
        Order modified = orderCommand().modifyOrder(new ModifyOrderRequest(
                orderId, quantity, pricePaisa, 0L, null, null));
        out.print(modified);
    }

    public int runProcess(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(DhanConfigPaths.resolve(".").toFile());
        builder.inheritIO();
        Process process = builder.start();
        return process.waitFor();
    }

    public void tokenRefresh() throws IOException, InterruptedException {
        int code = runProcess(List.of("bash", "scripts/refresh-dhan-token.sh"));
        if (code != 0) {
            throw new IllegalStateException("Token refresh failed with exit code " + code);
        }
    }

    public void runGradleTest(String task) throws IOException, InterruptedException {
        int code = runProcess(List.of("./gradlew", task, "--no-daemon"));
        if (code != 0) {
            throw new IllegalStateException("Gradle task failed: " + task);
        }
    }

    private void guardReplay() {
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String readYamlValue(Path yml, String keySuffix) throws IOException {
        for (String line : Files.readAllLines(yml)) {
            if (line.contains(keySuffix) && line.trim().startsWith("mode:")) {
                return line.substring(line.indexOf(':') + 1).trim();
            }
        }
        return null;
    }

    private void requireAttach() {
        if (!context.attachReachable()) {
            throw new IllegalStateException(
                    "trade-app is not reachable at " + context.attachUrl()
                            + ". Start trade-app or use standalone broker commands.");
        }
    }

    private void confirmOrAbort(String message) {
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

    private BrokerSession session() {
        return context.broker();
    }

    private PortfolioProvider portfolio() {
        return session().connection().portfolio();
    }

    private MarketDataProvider marketData() {
        return session().connection().marketData();
    }

    private OptionsProvider options() {
        return session().connection().options();
    }

    private MarginProvider margin() {
        return session().connection().margin();
    }

    private OrderQuery orderQuery() {
        return session().connection().orderQuery();
    }

    private OrderCommand orderCommand() {
        return session().connection().orders();
    }

    private static InstrumentKey instrument(String symbol, String segmentName) {
        return InstrumentKey.of(symbol, parseSegment(segmentName));
    }

    private static ExchangeSegment parseSegment(String segmentName) {
        return ExchangeSegment.valueOf(segmentName.trim().toUpperCase());
    }

    private void printJsonArrayTable(JsonNode array, String... fields) {
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

    private static String formatMs(long epochMs) {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                Instant.ofEpochMilli(epochMs).atZone(IST));
    }

    public static long defaultFromMs() {
        return Instant.now().minusSeconds(TimeUnit.DAYS.toSeconds(1)).toEpochMilli();
    }

    public static long defaultToMs() {
        return Instant.now().toEpochMilli();
    }

    public void scanRun(String profileId) throws Exception {
        if (context.attachReachable()) {
            JsonNode result = context.attach().scanRun(profileId);
            if (context.json()) {
                out.print(result);
                return;
            }
            printScanResult(result);
            return;
        }
        session().ensureCatalogLoaded();
        var broker = session().connection();
        ScanProfile profile = ScanProfileJsonLoader.load(profileId);
        ScanResult result = profile.isOptionLiquidityProfile()
                ? new OptionLiquidityScanner(broker.options()).scanProfile(profile)
                : new ScanEngine(new ScanDependencies(
                        broker.instruments(),
                        broker.marketData(),
                        broker.options(),
                        broker.futures()
                )).run(profile);
        if (context.json()) {
            out.print(Map.of(
                    "runId", result.run().runId(),
                    "profileId", result.run().profileId(),
                    "status", result.run().status().name(),
                    "hits", result.hits()
            ));
            return;
        }
        out.println("Scan " + result.run().runId() + " status=" + result.run().status()
                + " hits=" + result.hits().size());
        printScanHits(result);
    }

    public void optionsScan(
            String underlying,
            String segmentName,
            String expiryPolicy,
            LocalDate explicitExpiry,
            String side,
            int top,
            long minOi,
            long minVolume,
            double maxSpreadBps,
            boolean strictSpread
    ) throws Exception {
        if (context.attachReachable()) {
            JsonNode result = context.attach().optionsScan(
                    underlying,
                    segmentName,
                    expiryPolicy,
                    explicitExpiry,
                    side,
                    top,
                    minOi,
                    minVolume,
                    maxSpreadBps,
                    strictSpread
            );
            if (context.json()) {
                out.print(result);
                return;
            }
            printOptionScanResult(result);
            return;
        }
        session().ensureCatalogLoaded();
        ExchangeSegment segment = parseSegment(segmentName);
        OptionScanSpec spec = new OptionScanSpec(
                OptionExpiryPolicy.parse(expiryPolicy),
                explicitExpiry,
                OptionSideFilter.parse(side),
                minOi,
                minVolume,
                maxSpreadBps,
                strictSpread,
                top,
                0
        );
        OptionScanResult result = new OptionLiquidityScanner(options())
                .scan(new OptionScanRequest(underlying, segment, spec));
        if (context.json()) {
            out.print(result);
            return;
        }
        printOptionScanResultStandalone(result);
    }

    public void scanList(String profileId, int last) throws Exception {
        if (context.attachReachable()) {
            JsonNode result = context.attach().scanList(profileId, last);
            if (context.json()) {
                out.print(result);
                return;
            }
            printJsonArrayTable(result.get("runs"), "runId", "status", "hitCount", "startedAtMs");
            return;
        }
        out.println("scan list requires --attach to a running trade-app instance");
    }

    private void printScanResult(JsonNode result) {
        out.println("runId=" + result.path("run").path("runId").asText()
                + " status=" + result.path("run").path("status").asText()
                + " hits=" + result.path("hits").size());
        printJsonArrayTable(result.get("hits"), "symbol", "exchangeSegment", "score", "assetClass");
    }

    private void printScanHits(ScanResult result) {
        List<String[]> rows = new ArrayList<>();
        for (ScanHit hit : result.hits()) {
            rows.add(new String[]{
                    hit.symbol(),
                    hit.exchangeSegment().name(),
                    hit.assetClass().name(),
                    String.format("%.2f", hit.score()),
                    String.join("; ", hit.reasons())
            });
        }
        TablePrinter.print(new String[]{"symbol", "segment", "class", "score", "reasons"}, rows);
    }

    private void printOptionScanResult(JsonNode result) {
        out.println("underlying=" + result.path("underlying").asText()
                + " expiry=" + result.path("expiry").asText()
                + " status=" + result.path("status").asText()
                + " contracts=" + result.path("contracts").size());
        printJsonArrayTable(
                result.get("contracts"),
                "symbol", "optionType", "strikePaisa", "openInterest", "volume", "spreadBps", "score"
        );
    }

    private void printOptionScanResultStandalone(OptionScanResult result) {
        out.println("underlying=" + result.underlying()
                + " expiry=" + result.expiry()
                + " status=" + result.run().status()
                + " contracts=" + result.contracts().size());
        List<String[]> rows = new ArrayList<>();
        for (OptionContractHit hit : result.contracts()) {
            rows.add(new String[]{
                    hit.instrumentKey().symbol(),
                    hit.optionType().name(),
                    String.valueOf(hit.strikePricePaisa()),
                    String.valueOf(hit.openInterest()),
                    String.valueOf(hit.volume()),
                    String.format("%.1f", hit.spreadBps()),
                    String.format("%.2f", hit.liquidityScore())
            });
        }
        TablePrinter.print(new String[]{
                "symbol", "type", "strikePaisa", "oi", "volume", "spreadBps", "score"
        }, rows);
    }

    public void downloadRollingOptions(
            String symbols,
            String segment,
            LocalDate from,
            LocalDate to,
            String intervals,
            String expiry,
            String strikes,
            String optionTypes,
            String warehousePath,
            long delayMs,
            int workers,
            boolean runImmediately
    ) throws Exception {
        requireStandaloneDhan();
        session().ensureCatalogLoaded();
        var config = CliDownloadSupport.parseRollingOptionConfig(
                symbols, segment, from, to, intervals, expiry, strikes, optionTypes, delayMs
        );
        long estimated = com.tradej.historical.ingest.planner.RollingOptionDownloadPlanner.estimatedTaskCount(config);
        out.println("Estimated API tasks: " + estimated);
        if (!runImmediately) {
            try (var warehouse = new com.tradej.historical.ingest.store.DuckDbHistoricalWarehouse(Path.of(warehousePath))) {
                String jobId = java.util.UUID.randomUUID().toString();
                warehouse.insertJob(new com.tradej.historical.ingest.model.DownloadJobRecord(
                        jobId,
                        com.tradej.historical.ingest.model.DownloadSourceType.ROLLING_OPTION,
                        com.tradej.historical.ingest.model.DownloadJobStatus.PENDING,
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                                .writeValueAsString(config),
                        System.currentTimeMillis(),
                        null,
                        null,
                        null
                ));
                warehouse.insertTasks(new com.tradej.historical.ingest.planner.RollingOptionDownloadPlanner().planTasks(jobId, config));
                out.print(Map.of("jobId", jobId, "status", "PENDING", "estimatedTasks", estimated));
            }
            return;
        }
        var service = CliDownloadSupport.openService(Path.of(warehousePath), options(), delayMs, workers);
        out.print(CliDownloadSupport.startRollingOptions(service, config, true));
    }

    public void downloadReset(String warehousePath) throws Exception {
        try (var warehouse = new com.tradej.historical.ingest.store.DuckDbHistoricalWarehouse(Path.of(warehousePath))) {
            warehouse.truncateDownloadData();
            out.print(Map.of(
                    "warehouse", warehousePath,
                    "status", "RESET",
                    "message", "Truncated rolling_option_bars, download_tasks, download_jobs"
            ));
        }
    }

    public void downloadStatus(String jobId, String warehousePath) throws Exception {
        Path equityMeta = CliDownloadSupport.equityMetaDatabase(warehousePath);
        if (java.nio.file.Files.exists(equityMeta)) {
            try (var warehouse = new com.tradej.historical.ingest.store.DuckDbHistoricalWarehouse(equityMeta)) {
                var job = warehouse.findJob(jobId).orElseThrow(() -> new IllegalArgumentException("Unknown job " + jobId));
                var stats = warehouse.jobStats(jobId);
                out.print(Map.of(
                        "jobId", job.jobId(),
                        "sourceType", job.sourceType(),
                        "status", job.status(),
                        "stats", stats
                ));
            }
            return;
        }
        try (var warehouse = new com.tradej.historical.ingest.store.DuckDbHistoricalWarehouse(Path.of(warehousePath))) {
            var job = warehouse.findJob(jobId).orElseThrow(() -> new IllegalArgumentException("Unknown job " + jobId));
            var stats = warehouse.jobStats(jobId);
            out.print(Map.of(
                    "jobId", job.jobId(),
                    "status", job.status(),
                    "stats", stats
            ));
        }
    }

    public void downloadResume(String jobId, String warehousePath, long delayMs, int workers) throws Exception {
        Path equityMeta = CliDownloadSupport.equityMetaDatabase(warehousePath);
        if (java.nio.file.Files.exists(equityMeta)) {
            requireStandaloneUpstox();
            session().ensureCatalogLoaded();
            try (var service = CliDownloadSupport.openEquityService(
                    java.nio.file.Path.of(warehousePath),
                    marketData(),
                    session().connection().instruments(),
                    delayMs,
                    workers,
                    com.tradej.historical.ingest.universe.Nifty500UniverseFetcher.DEFAULT_UNIVERSE_URL
            )) {
                var stats = service.resumeJob(jobId);
                out.print(Map.of("jobId", jobId, "stats", stats));
            }
            return;
        }
        requireStandaloneDhan();
        session().ensureCatalogLoaded();
        var service = CliDownloadSupport.openService(Path.of(warehousePath), options(), delayMs, workers);
        var stats = service.resumeJob(jobId);
        out.print(Map.of("jobId", jobId, "stats", stats));
    }

    public void refreshNifty500Universe(String rootPath) throws Exception {
        requireStandaloneUpstox();
        session().ensureCatalogLoaded();
        try (var service = CliDownloadSupport.openEquityService(
                Path.of(rootPath),
                marketData(),
                session().connection().instruments(),
                0L,
                1,
                com.tradej.historical.ingest.universe.Nifty500UniverseFetcher.DEFAULT_UNIVERSE_URL
        )) {
            out.print(service.refreshUniverse());
        }
    }

    public void refreshUniverseFromDisk(String rootPath) throws Exception {
        int count = new com.tradej.historical.ingest.maintenance.UniverseDiskRefresher(Path.of(rootPath))
                .refreshFromBars();
        out.print(Map.of("root", rootPath, "symbolsWritten", count));
    }

    public void compactEquityPartitions(String rootPath) throws Exception {
        var result = new com.tradej.historical.ingest.maintenance.EquityParquetCompactor(Path.of(rootPath)).compact();
        out.print(Map.of(
                "root", rootPath,
                "symbolsProcessed", result.symbolsProcessed(),
                "taskFilesMerged", result.taskFilesMerged(),
                "partitionsWritten", result.partitionsWritten()
        ));
    }

    public void analyticsCatalog(String equityRoot, String optionsWarehouse) throws Exception {
        try (var service = com.tradej.cli.analytics.CliAnalyticsSupport.openService(
                equityRoot, optionsWarehouse, "", false)) {
            out.print(com.tradej.cli.analytics.CliAnalyticsSupport.catalog(service));
        }
    }

    public void analyticsSql(String equityRoot, String optionsWarehouse, String sql, int limit) throws Exception {
        try (var service = com.tradej.cli.analytics.CliAnalyticsSupport.openService(
                equityRoot, optionsWarehouse, "", false)) {
            out.print(com.tradej.cli.analytics.CliAnalyticsSupport.sql(service, sql, limit));
        }
    }

    public void analyticsQueryEquity(
            String equityRoot,
            String optionsWarehouse,
            String symbol,
            String interval,
            LocalDate from,
            LocalDate to
    ) throws Exception {
        try (var service = com.tradej.cli.analytics.CliAnalyticsSupport.openService(
                equityRoot, optionsWarehouse, "", false)) {
            out.print(com.tradej.cli.analytics.CliAnalyticsSupport.equityCandles(
                    service, symbol, interval, from, to));
        }
    }

    public void analyticsQueryOptions(
            String equityRoot,
            String optionsWarehouse,
            String underlying,
            String expiryKind,
            int expiryCode,
            int strikeOffset,
            String optionType,
            int intervalMin,
            long fromMs,
            long toMs,
            int limit
    ) throws Exception {
        try (var service = com.tradej.cli.analytics.CliAnalyticsSupport.openService(
                equityRoot, optionsWarehouse, "", false)) {
            out.print(com.tradej.cli.analytics.CliAnalyticsSupport.optionBars(
                    service, underlying, expiryKind, expiryCode, strikeOffset,
                    optionType, intervalMin, fromMs, toMs, limit));
        }
    }

    public void downloadJobsList(String source, String equityRoot, String optionsWarehouse, int limit) throws Exception {
        try (var registry = openDownloadJobRegistry(equityRoot, optionsWarehouse)) {
            var sourceType = com.tradej.historical.ingest.model.DownloadSourceType.valueOf(source.toUpperCase());
            out.print(registry.listJobs(sourceType, limit));
        }
    }

    private com.tradej.historical.ingest.service.DownloadJobRegistry openDownloadJobRegistry(
            String equityRoot,
            String optionsWarehouse
    ) {
        return new com.tradej.historical.ingest.service.DownloadJobRegistry(
                Path.of(optionsWarehouse),
                Path.of(equityRoot)
        );
    }

    public void downloadEquity(
            String universeOrSymbols,
            String segment,
            LocalDate from,
            LocalDate to,
            String interval,
            String rootPath,
            long delayMs,
            int workers,
            boolean refreshUniverse,
            boolean runImmediately
    ) throws Exception {
        requireStandaloneUpstox();
        session().ensureCatalogLoaded();
        var config = CliDownloadSupport.parseEquityConfig(
                universeOrSymbols, segment, from, to, interval, rootPath, delayMs, workers, refreshUniverse
        );
        long estimated = com.tradej.historical.ingest.planner.EquityHistoricalDownloadPlanner.estimatedTaskCount(config);
        out.println("Estimated API tasks: " + estimated);
        try (var service = CliDownloadSupport.openEquityService(
                Path.of(config.rootPath()),
                marketData(),
                session().connection().instruments(),
                delayMs,
                workers,
                com.tradej.historical.ingest.universe.Nifty500UniverseFetcher.DEFAULT_UNIVERSE_URL
        )) {
            out.print(CliDownloadSupport.startEquityDownload(service, config, runImmediately));
        }
    }

    public void importEquityHive(
            String sourceHive,
            String universeCsv,
            String industryParquet,
            String rootPath,
            String fromMonth,
            String toMonth,
            boolean force,
            String symbols,
            boolean skipUniverseImport
    ) throws Exception {
        var result = com.tradej.cli.download.CliEquityImportSupport.importHive(
                sourceHive,
                universeCsv,
                industryParquet,
                rootPath,
                fromMonth,
                toMonth,
                force,
                symbols,
                skipUniverseImport
        );
        out.print(com.tradej.cli.download.CliEquityImportSupport.toResponseMap(result));
    }

    private void requireStandaloneUpstox() {
        if (context.attachReachable()) {
            throw new IllegalStateException("Equity download requires standalone Upstox mode (do not use --attach).");
        }
        if (context.brokerType() != CliConfig.BrokerType.UPSTOX) {
            throw new IllegalStateException("Equity historical download is Upstox-only.");
        }
    }

    private void requireStandaloneDhan() {
        if (context.attachReachable()) {
            throw new IllegalStateException("Download jobs require standalone Dhan mode (do not use --attach).");
        }
        if (context.brokerType() != CliConfig.BrokerType.DHAN) {
            throw new IllegalStateException("Rolling option download is Dhan-only.");
        }
    }
}
