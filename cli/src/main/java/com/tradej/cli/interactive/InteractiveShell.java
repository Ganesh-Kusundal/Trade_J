package com.tradej.cli.interactive;

import com.tradej.cli.CliContext;
import com.tradej.cli.CliOperations;
import com.tradej.cli.config.CliConfig;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.time.LocalDate;

public final class InteractiveShell {
    private final CliContext context;
    private final CliOperations operations;

    public InteractiveShell(CliContext context, CliOperations operations) {
        this.context = context;
        this.operations = operations;
    }

    public void run() throws IOException {
        Terminal terminal = buildTerminal();
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
        MenuContext menu = new MenuContext(context, operations, reader);

        if ("dumb".equals(terminal.getType()) && System.console() == null) {
            operations.output().error(
                    "No interactive terminal detected. Use ./scripts/tradej from a real terminal, "
                            + "or run non-interactive commands (e.g. ./scripts/tradej status).");
            return;
        }

        operations.output().println(menu.headerLine());
        operations.output().println("");
        printMainMenu();

        while (true) {
            String choice;
            try {
                choice = reader.readLine("tradej> ").trim();
            } catch (UserInterruptException | EndOfFileException ex) {
                operations.output().println("Bye.");
                return;
            }
            if (choice.isEmpty()) {
                continue;
            }
            try {
                if (!route(menu, choice)) {
                    return;
                }
            } catch (Exception ex) {
                operations.output().error("Error: " + ex.getMessage());
            }
            operations.output().println("");
        }
    }

    private static Terminal buildTerminal() throws IOException {
        if (System.console() != null) {
            return TerminalBuilder.builder().system(true).build();
        }
        return TerminalBuilder.builder()
                .dumb(true)
                .streams(System.in, System.out)
                .build();
    }

    private boolean route(MenuContext menu, String choice) throws Exception {
        return switch (choice.toLowerCase()) {
            case "0", "exit", "quit" -> {
                operations.output().println("Bye.");
                yield false;
            }
            case "1", "status" -> {
                operations.status();
                yield true;
            }
            case "2" -> {
                runtimeMenu(menu);
                yield true;
            }
            case "3" -> {
                ordersMenu(menu);
                yield true;
            }
            case "4" -> {
                riskMenu(menu);
                yield true;
            }
            case "5" -> {
                portfolioMenu(menu);
                yield true;
            }
            case "6" -> {
                brokerMenu(menu);
                yield true;
            }
            case "7" -> {
                optionsMenu(menu);
                yield true;
            }
            case "8" -> {
                historicalMenu(menu);
                yield true;
            }
            case "9" -> {
                scanMenu(menu);
                yield true;
            }
            case "10" -> {
                verifyMenu(menu);
                yield true;
            }
            case "11", "orders-sandbox" -> {
                sandboxOrdersMenu(menu);
                yield true;
            }
            case "s", "settings" -> {
                settingsMenu(menu);
                yield true;
            }
            case "help", "?" -> {
                printMainMenu();
                yield true;
            }
            default -> {
                operations.output().error("Unknown choice. Enter help, s, or 0-11.");
                yield true;
            }
        };
    }

    private void settingsMenu(MenuContext menu) {
        operations.output().println("Settings: attach | profile | broker | show");
        String cmd = menu.prompt("Command", "show");
        switch (cmd.toLowerCase()) {
            case "attach", "url" -> {
                context.setAttachUrl(menu.prompt("Attach URL", context.attachUrl()));
                operations.output().println("Attach URL updated.");
            }
            case "profile" -> {
                String profile = menu.prompt("Profile live|sandbox", context.profile().name().toLowerCase());
                context.setProfile("sandbox".equalsIgnoreCase(profile)
                        ? CliConfig.Profile.SANDBOX
                        : CliConfig.Profile.LIVE);
                operations.output().println("Profile updated to " + context.profile() + ".");
            }
            case "broker" -> {
                String broker = menu.prompt("Broker dhan|upstox", context.brokerType().name().toLowerCase());
                context.setBrokerType(CliConfig.BrokerType.parse(broker));
                operations.output().println("Broker updated to " + context.brokerType() + ".");
            }
            default -> operations.output().println(menu.headerLine());
        }
    }

    private void runtimeMenu(MenuContext menu) {
        operations.output().println("Runtime: status | pipeline | strategies | runtime");
        String cmd = menu.prompt("Command", "status");
        switch (cmd) {
            case "pipeline" -> operations.pipeline();
            case "strategies" -> operations.strategies();
            case "runtime" -> operations.runtime();
            default -> operations.status();
        }
    }

    private void ordersMenu(MenuContext menu) {
        operations.output().println("Orders: orders | positions | read-model | stream");
        String cmd = menu.prompt("Command", "orders");
        switch (cmd) {
            case "positions" -> operations.positions();
            case "read-model", "all" -> operations.readModel();
            case "stream" -> operations.streamReadModel(
                    Integer.parseInt(menu.prompt("Seconds", "30")));
            default -> operations.orders();
        }
    }

    private void riskMenu(MenuContext menu) throws Exception {
        operations.output().println("Risk: config | kill-switch on|off | reconcile");
        String cmd = menu.prompt("Command", "config");
        if ("config".equals(cmd)) {
            operations.showRiskConfig();
        } else if (cmd.startsWith("kill-switch")) {
            boolean enabled = cmd.endsWith("on");
            operations.killSwitch(enabled);
        } else if ("reconcile".equals(cmd)) {
            String json = menu.prompt("JSON symbol=qty map", "{}");
            operations.reconcile(json);
        }
    }

    private void portfolioMenu(MenuContext menu) {
        if (context.brokerType() == CliConfig.BrokerType.UPSTOX
                && context.profile() == CliConfig.Profile.LIVE
                && isAnalyticsOnlyUpstox()) {
            operations.output().error("Portfolio commands require full Upstox OAuth (not analytics-only token).");
            return;
        }
        operations.output().println("Portfolio: balance | positions | holdings | live-pnl");
        String cmd = menu.prompt("Command", "balance");
        switch (cmd) {
            case "positions" -> operations.brokerPositions();
            case "holdings" -> operations.holdings();
            case "live-pnl", "pnl" -> operations.livePnl();
            default -> operations.balance();
        }
    }

    private boolean isAnalyticsOnlyUpstox() {
        try {
            var settings = CliConfig.upstoxConnectionSettings(context.profile());
            return settings.analyticsOnly();
        } catch (Exception ex) {
            return true;
        }
    }

    private void brokerMenu(MenuContext menu) {
        operations.output().println("Broker (" + context.brokerType() + "): ltp | quote | depth | ohlc | candles | orderbook | trades | order | catalog");
        String symbol = menu.prompt("Symbol", "NIFTY");
        String segment = menu.prompt("Segment", context.brokerType() == CliConfig.BrokerType.UPSTOX ? "NSE_EQ" : "IDX_I");
        String cmd = menu.prompt("Command", "ltp");
        switch (cmd) {
            case "catalog", "catalog-refresh" -> operations.refreshCatalog(
                    "yes".equalsIgnoreCase(menu.prompt("Force re-download? yes|no", "no")));
            case "quote" -> operations.quote(symbol, segment);
            case "depth" -> operations.depth(symbol, segment);
            case "ohlc" -> operations.ohlc(symbol, segment);
            case "candles" -> {
                String interval = menu.prompt("Interval", "5m");
                LocalDate to = LocalDate.now();
                LocalDate from = to.minusDays(5);
                operations.candles(symbol, segment, interval, from, to);
            }
            case "orderbook" -> operations.orderBook();
            case "trades" -> operations.trades();
            case "order" -> operations.order(menu.prompt("Order id", ""));
            default -> operations.ltp(symbol, segment);
        }
    }

    private void optionsMenu(MenuContext menu) throws Exception {
        String underlying = menu.prompt("Underlying", "NIFTY");
        String segment = menu.prompt("Segment", context.brokerType() == CliConfig.BrokerType.UPSTOX ? "NSE_EQ" : "IDX_I");
        operations.output().println("Options: expiries | chain | strike | margin | rolling-option | liquidity-scan");
        String cmd = menu.prompt("Command", "expiries");
        switch (cmd) {
            case "liquidity-scan", "scan" -> {
                String expiryInput = menu.prompt("Expiry nearest|next|YYYY-MM-DD", "nearest");
                LocalDate explicitExpiry = null;
                String expiryPolicy = expiryInput;
                if (expiryInput.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    explicitExpiry = LocalDate.parse(expiryInput);
                    expiryPolicy = "EXPLICIT";
                }
                operations.optionsScan(
                        underlying,
                        segment,
                        expiryPolicy,
                        explicitExpiry,
                        menu.prompt("Side ce|pe|both", "both"),
                        Integer.parseInt(menu.prompt("Top N", "10")),
                        Long.parseLong(menu.prompt("Min OI", "1000")),
                        Long.parseLong(menu.prompt("Min volume", "0")),
                        Double.parseDouble(menu.prompt("Max spread bps", "300")),
                        "yes".equalsIgnoreCase(menu.prompt("Strict spread yes|no", "no"))
                );
            }
            case "rolling-option" -> operations.rollingOption(
                    underlying,
                    segment,
                    Integer.parseInt(menu.prompt("Interval minutes", "5")),
                    menu.prompt("Expiry flag", "MONTH"),
                    Integer.parseInt(menu.prompt("Expiry code", "1")),
                    menu.prompt("Strike", "ATM"),
                    menu.prompt("Option type", "CALL"),
                    LocalDate.parse(menu.prompt("From YYYY-MM-DD", LocalDate.now().minusDays(30).toString())),
                    LocalDate.parse(menu.prompt("To YYYY-MM-DD", LocalDate.now().toString())));
            case "chain" -> {
                LocalDate expiry = LocalDate.parse(menu.prompt("Expiry YYYY-MM-DD", LocalDate.now().plusDays(7).toString()));
                operations.chain(underlying, segment, expiry);
            }
            case "strike" -> operations.strike(
                    underlying, segment, menu.prompt("atm|itm|otm", "atm"),
                    Integer.parseInt(menu.prompt("Depth", "0")));
            case "margin" -> operations.margin(
                    menu.prompt("Symbol", underlying),
                    segment,
                    menu.prompt("Side", "BUY"),
                    Long.parseLong(menu.prompt("Qty", "1")),
                    menu.prompt("Product", "INTRADAY"),
                    menu.prompt("OrderType", "MARKET"),
                    Long.parseLong(menu.prompt("Price paisa", "0")));
            default -> operations.expiries(underlying, segment);
        }
    }

    private void historicalMenu(MenuContext menu) throws Exception {
        if (!context.attachReachable()) {
            operations.output().error("Historical commands require a running app (attach).");
            return;
        }
        operations.output().println("Historical: candles|ticks|orders|fills|stats | replay-*");
        String cmd = menu.prompt("Command", "stats");
        String symbol = menu.prompt("Symbol", "NIFTY");
        long from = Long.parseLong(menu.prompt("From epoch ms", String.valueOf(CliOperations.defaultFromMs())));
        long to = Long.parseLong(menu.prompt("To epoch ms", String.valueOf(CliOperations.defaultToMs())));
        switch (cmd) {
            case "candles" -> operations.historicalCandles(symbol, menu.prompt("Interval", "5m"), from, to, 1000);
            case "ticks" -> operations.historicalTicks(symbol, from, to, 5000);
            case "orders" -> operations.historicalOrders(symbol, from, to, 500);
            case "fills" -> operations.historicalFills(symbol, from, to, 500);
            case "replay-ticks" -> operations.replayTicks(symbol, from, to);
            case "replay-candles" -> operations.replayCandles(symbol, menu.prompt("Interval", "5m"), from, to);
            case "replay-fills" -> operations.replayFills(symbol, from, to);
            case "replay-orders" -> operations.replayOrders(symbol, from, to);
            case "replay-chronicle" -> operations.replayChronicle(menu.prompt("Event class", ""));
            default -> operations.historicalStats(symbol, from, to);
        }
    }

    private void scanMenu(MenuContext menu) throws Exception {
        if (!context.attachReachable()) {
            operations.output().error("Scan commands require a running app (attach).");
            return;
        }
        operations.output().println("Scan: run | list");
        String cmd = menu.prompt("Command", "run");
        String profileId = menu.prompt("Profile id", "default");
        if ("list".equalsIgnoreCase(cmd)) {
            operations.scanList(profileId, Integer.parseInt(menu.prompt("Last N runs", "10")));
        } else {
            operations.scanRun(profileId);
        }
    }

    private void sandboxOrdersMenu(MenuContext menu) throws Exception {
        if (context.profile() != CliConfig.Profile.SANDBOX) {
            operations.output().error("Sandbox orders require profile=sandbox (use Settings or --profile sandbox).");
            return;
        }
        if (context.brokerType() == CliConfig.BrokerType.UPSTOX && isAnalyticsOnlyUpstox()) {
            operations.output().error("Upstox analytics token does not support orders.");
            return;
        }
        operations.output().println("Sandbox orders: place | cancel | modify");
        String cmd = menu.prompt("Command", "place");
        switch (cmd) {
            case "cancel" -> operations.cancelOrder(menu.prompt("Order id", ""));
            case "modify" -> operations.modifyOrder(
                    menu.prompt("Order id", ""),
                    Long.parseLong(menu.prompt("Qty", "1")),
                    Long.parseLong(menu.prompt("Price paisa", "0")));
            default -> operations.placeSandboxOrder(
                    menu.prompt("Symbol", "RELIANCE"),
                    menu.prompt("Segment", "NSE_EQ"),
                    menu.prompt("Side BUY|SELL", "BUY"),
                    Long.parseLong(menu.prompt("Qty", "1")),
                    menu.prompt("Type MARKET|LIMIT", "LIMIT"),
                    Long.parseLong(menu.prompt("Price paisa", "250000")),
                    menu.prompt("Product INTRADAY|CNC", "INTRADAY"));
        }
    }

    private void verifyMenu(MenuContext menu) throws Exception {
        operations.output().println("Verify: token | test unit | test broker-rest | test preflight | test regression");
        String cmd = menu.prompt("Command", "token");
        switch (cmd) {
            case "token", "token-refresh" -> operations.tokenRefresh();
            case "test unit" -> operations.runGradleTest(":cli:cliUnitTest");
            case "test broker-rest" -> operations.runGradleTest("brokerRestTest");
            case "test preflight" -> operations.runGradleTest(":app:regressionPreflightTest");
            case "test regression" -> operations.runProcess(java.util.List.of("bash", "scripts/run-full-regression.sh"));
            default -> operations.output().error("Unknown verify command");
        }
    }

    private void printMainMenu() {
        operations.output().println("  1) Quick status          6) Broker / market");
        operations.output().println("  2) Runtime & pipeline    7) Options & derivatives");
        operations.output().println("  3) Orders & positions    8) Historical & replay");
        operations.output().println("  4) Risk & reconcile      9) Scan (attach)");
        operations.output().println("  5) Portfolio & PnL      10) Verify & maintenance");
        operations.output().println(" 11) Sandbox orders        s) Settings");
        operations.output().println("  0) Exit");
    }
}
