package com.tradej.cli;

import com.tradej.cli.command.CliAnalyticsCommands;
import com.tradej.cli.command.CliBrokerGatewayCommands;
import com.tradej.cli.config.CliConfig;
import com.tradej.cli.interactive.InteractiveShell;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.time.LocalDate;
import java.util.concurrent.Callable;

@Command(
        name = "tradej",
        mixinStandardHelpOptions = true,
        version = "trade-cli 1.0",
        description = "Trade-J operator CLI (attach to trade-app or standalone broker)",
        subcommands = {
                TradeCli.InteractiveCmd.class,
                TradeCli.StatusCmd.class,
                TradeCli.RuntimeCmd.class,
                TradeCli.PipelineCmd.class,
                TradeCli.StrategiesCmd.class,
                TradeCli.SummaryCmd.class,
                TradeCli.OrdersCmd.class,
                TradeCli.PositionsCmd.class,
                TradeCli.ReadModelCmd.class,
                TradeCli.BalanceCmd.class,
                TradeCli.HoldingsCmd.class,
                TradeCli.BrokerPositionsCmd.class,
                TradeCli.LtpCmd.class,
                TradeCli.QuoteCmd.class,
                TradeCli.DepthCmd.class,
                TradeCli.OhlcCmd.class,
                TradeCli.CandlesCmd.class,
                TradeCli.OrderBookCmd.class,
                TradeCli.TradesCmd.class,
                TradeCli.OrderCmd.class,
                TradeCli.LivePnlCmd.class,
                TradeCli.ExpiriesCmd.class,
                TradeCli.ChainCmd.class,
                TradeCli.DataCmd.class,
                TradeCli.BacktestCmd.class,
                TradeCli.PortfolioCmd.class,
                TradeCli.ScreenerCmd.class,
                TradeCli.BrokerValidateCmd.class,
                TradeCli.OptionsScanCmd.class,
                TradeCli.StrikeCmd.class,
                TradeCli.MarginCmd.class,
                TradeCli.RollingOptionCmd.class,
                TradeCli.StreamReadModelCmd.class,
                TradeCli.KillSwitchCmd.class,
                TradeCli.ReconcileCmd.class,
                TradeCli.RiskConfigCmd.class,
                TradeCli.HistoricalCmd.class,
                TradeCli.ReplayCmd.class,
                TradeCli.PlaceCmd.class,
                TradeCli.CancelCmd.class,
                TradeCli.ModifyCmd.class,
                TradeCli.TokenCmd.class,
                TradeCli.CatalogCmd.class,
                TradeCli.ScanCmd.class,
                TradeCli.DownloadCmd.class,
                TradeCli.UniverseCmd.class,
                TradeCli.EquityCmd.class,
                TradeCli.AnalyticsCmd.class,
                TradeCli.TestCmd.class,
                CliBrokerGatewayCommands.class,
                CliAnalyticsCommands.class,
                TradeCli.QueryCmd.class,
                TradeCli.PreviewCmd.class,
                TradeCli.BatchQuoteCmd.class,
                TradeCli.AlertsCmd.class,
                TradeCli.SquareOffCmd.class,
                TradeCli.BracketCmd.class,
                TradeCli.GttCmd.class,
                TradeCli.FuturesCmd.class,
                TradeCli.HealthCmd.class
        }
)
public class TradeCli implements Callable<Integer> {
    @Option(names = "--attach", description = "trade-app base URL", defaultValue = "")
    String attachUrl;

    @Option(names = "--profile", description = "live|sandbox (Dhan profile; Upstox maps live/sandbox property files)", defaultValue = "live")
    String profileName;

    @Option(names = "--broker", description = "dhan|upstox", defaultValue = "dhan")
    String brokerName;

    @Option(names = "--json", description = "JSON output")
    boolean json;

    @Option(names = {"-y", "--yes"}, description = "Skip confirmation prompts")
    boolean yes;

    private CliContext context;
    private CliOperations operations;

    public CliOperations ops() {
        if (operations == null) {
            context = createContext();
            operations = new CliOperations(context);
        }
        return operations;
    }

    public void close() {
        if (context != null) {
            context.close();
        }
    }

    @Override
    public Integer call() throws Exception {
        CliContext ctx = createContext();
        context = ctx;
        operations = new CliOperations(ctx);
        new InteractiveShell(ctx, operations).run();
        return 0;
    }

    private CliContext createContext() {
        String url = attachUrl == null || attachUrl.isBlank() ? CliConfig.attachUrl() : attachUrl;
        CliConfig.Profile profile = "sandbox".equalsIgnoreCase(profileName)
                ? CliConfig.Profile.SANDBOX
                : CliConfig.Profile.LIVE;
        CliConfig.BrokerType broker = CliConfig.BrokerType.parse(brokerName);
        return new CliContext(url, profile, broker, json, yes);
    }

    public static void main(String[] args) {
        TradeCli root = new TradeCli();
        CommandLine cmd = new CommandLine(root);
        int exit = cmd.execute(args);
        root.close();
        System.exit(exit);
    }

    abstract static class BaseCmd implements Callable<Integer> {
        @ParentCommand
        TradeCli parent;

        @Override
        public Integer call() throws Exception {
            run(parent.ops());
            return 0;
        }

        abstract void run(CliOperations ops) throws Exception;
    }

    @Command(name = "interactive", description = "Interactive REPL menu")
    static final class InteractiveCmd extends BaseCmd {
        @Override
        void run(CliOperations ops) throws Exception {
            CliContext ctx = parent.context != null ? parent.context : parent.createContext();
            new InteractiveShell(ctx, ops).run();
        }
    }

    @Command(name = "status")
    static final class StatusCmd extends BaseCmd {
        @Override
        void run(CliOperations ops) {
            ops.status();
        }
    }

    @Command(name = "runtime")
    static final class RuntimeCmd extends BaseCmd {
        @Override
        void run(CliOperations ops) {
            ops.runtime();
        }
    }

    @Command(name = "pipeline")
    static final class PipelineCmd extends BaseCmd {
        @Override
        void run(CliOperations ops) {
            ops.pipeline();
        }
    }

    @Command(name = "strategies")
    static final class StrategiesCmd extends BaseCmd {
        @Override
        void run(CliOperations ops) {
            ops.strategies();
        }
    }

    @Command(name = "summary")
    static final class SummaryCmd extends BaseCmd {
        @Override
        void run(CliOperations ops) {
            ops.summary();
        }
    }

    @Command(name = "orders")
    static final class OrdersCmd extends BaseCmd {
        @Override
        void run(CliOperations ops) {
            ops.orders();
        }
    }

    @Command(name = "positions")
    static final class PositionsCmd extends BaseCmd {
        @Override
        void run(CliOperations ops) {
            if (parent.ops().context().attachReachable()) {
                ops.positions();
            } else {
                ops.brokerPositions();
            }
        }
    }

    @Command(name = "read-model")
    static final class ReadModelCmd extends BaseCmd {
        @Override
        void run(CliOperations ops) {
            ops.readModel();
        }
    }

    @Command(name = "balance")
    static final class BalanceCmd extends BaseCmd {
        @Override
        void run(CliOperations ops) {
            ops.balance();
        }
    }

    @Command(name = "holdings")
    static final class HoldingsCmd extends BaseCmd {
        @Override
        void run(CliOperations ops) {
            ops.holdings();
        }
    }

    @Command(name = "broker-positions")
    static final class BrokerPositionsCmd extends BaseCmd {
        @Override
        void run(CliOperations ops) {
            ops.brokerPositions();
        }
    }

    @Command(name = "ltp")
    static final class LtpCmd extends BaseCmd {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;

        @Override
        void run(CliOperations ops) {
            ops.ltp(symbol, segment);
        }
    }

    @Command(name = "quote")
    static final class QuoteCmd extends BaseCmd {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;

        @Override
        void run(CliOperations ops) {
            ops.quote(symbol, segment);
        }
    }

    @Command(name = "depth")
    static final class DepthCmd extends BaseCmd {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;

        @Override
        void run(CliOperations ops) {
            ops.depth(symbol, segment);
        }
    }

    @Command(name = "ohlc")
    static final class OhlcCmd extends BaseCmd {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;

        @Override
        void run(CliOperations ops) {
            ops.ohlc(symbol, segment);
        }
    }

    @Command(name = "candles")
    static final class CandlesCmd extends BaseCmd {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Option(names = "--interval", defaultValue = "5m") String interval;
        @Option(names = "--from") LocalDate from;
        @Option(names = "--to") LocalDate to;

        @Override
        void run(CliOperations ops) {
            DateRange range = resolveRange(from, to);
            ops.candles(symbol, segment, interval, range.from(), range.to());
        }

        static DateRange resolveRange(LocalDate from, LocalDate to) {
            LocalDate end = to == null ? LocalDate.now() : to;
            LocalDate start = from == null ? end.minusDays(89) : from;
            if (end.isBefore(start)) {
                throw new IllegalArgumentException("--to must be on/after --from");
            }
            return new DateRange(start, end);
        }
    }

    record DateRange(LocalDate from, LocalDate to) {
    }

    @Command(name = "orderbook")
    static final class OrderBookCmd extends BaseCmd {
        @Override
        void run(CliOperations ops) {
            ops.orderBook();
        }
    }

    @Command(name = "trades")
    static final class TradesCmd extends BaseCmd {
        @Override
        void run(CliOperations ops) {
            ops.trades();
        }
    }

    @Command(name = "order")
    static final class OrderCmd extends BaseCmd {
        @Parameters(index = "0") String orderId;

        @Override
        void run(CliOperations ops) {
            ops.order(orderId);
        }
    }

    @Command(name = "live-pnl")
    static final class LivePnlCmd extends BaseCmd {
        @Override
        void run(CliOperations ops) {
            ops.livePnl();
        }
    }

    @Command(name = "expiries")
    static final class ExpiriesCmd extends BaseCmd {
        @Parameters(index = "0") String underlying;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;

        @Override
        void run(CliOperations ops) {
            ops.expiries(underlying, segment);
        }
    }

    @Command(name = "chain")
    static final class ChainCmd extends BaseCmd {
        @Parameters(index = "0") String underlying;
        @Parameters(index = "1") String expiry;
        @Parameters(index = "2", defaultValue = "IDX_I") String segment;

        @Override
        void run(CliOperations ops) {
            ops.chain(underlying, segment, LocalDate.parse(expiry));
        }
    }

    @Command(name = "options-scan", description = "Rank option contracts by liquidity (OI, volume, spread)")
    static final class OptionsScanCmd extends BaseCmd {
        @Parameters(index = "0") String underlying;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Option(names = "--expiry", description = "nearest|next|YYYY-MM-DD", defaultValue = "nearest") String expiry;
        @Option(names = "--side", description = "ce|pe|both", defaultValue = "both") String side;
        @Option(names = "--top", description = "Top N contracts", defaultValue = "10") int top;
        @Option(names = "--min-oi", defaultValue = "1000") long minOi;
        @Option(names = "--min-volume", defaultValue = "0") long minVolume;
        @Option(names = "--max-spread-bps", defaultValue = "300") double maxSpreadBps;
        @Option(names = "--strict-spread", description = "Exclude legs without bid/ask") boolean strictSpread;

        @Override
        void run(CliOperations ops) throws Exception {
            LocalDate explicitExpiry = null;
            String expiryPolicy = expiry;
            if (expiry != null && expiry.matches("\\d{4}-\\d{2}-\\d{2}")) {
                explicitExpiry = LocalDate.parse(expiry);
                expiryPolicy = "EXPLICIT";
            }
            ops.optionsScan(
                    underlying,
                    segment,
                    expiryPolicy,
                    explicitExpiry,
                    side,
                    top,
                    minOi,
                    minVolume,
                    maxSpreadBps,
                    strictSpread
            );
        }
    }

    @Command(name = "strike")
    static final class StrikeCmd extends BaseCmd {
        @Parameters(index = "0") String underlying;
        @Parameters(index = "1", defaultValue = "atm") String kind;
        @Parameters(index = "2", defaultValue = "IDX_I") String segment;
        @Option(names = "--depth", defaultValue = "0") int depth;

        @Override
        void run(CliOperations ops) {
            ops.strike(underlying, segment, kind, depth);
        }
    }

    @Command(name = "margin")
    static final class MarginCmd extends BaseCmd {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Option(names = "--side", defaultValue = "BUY") String side;
        @Option(names = "--qty", defaultValue = "1") long qty;
        @Option(names = "--product", defaultValue = "INTRADAY") String product;
        @Option(names = "--order-type", defaultValue = "MARKET") String orderType;
        @Option(names = "--price", defaultValue = "0") long price;

        @Override
        void run(CliOperations ops) {
            ops.margin(symbol, segment, side, qty, product, orderType, price);
        }
    }

    @Command(name = "rolling-option", description = "Expired rolling option history (opt-in via DHAN_ROLLING_OPTION_TEST_ENABLED)")
    static final class RollingOptionCmd extends BaseCmd {
        @Parameters(index = "0") String underlying;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Option(names = "--interval", defaultValue = "5") int intervalMinutes;
        @Option(names = "--expiry-flag", defaultValue = "MONTH") String expiryFlag;
        @Option(names = "--expiry-code", defaultValue = "1") int expiryCode;
        @Option(names = "--strike", defaultValue = "ATM") String strike;
        @Option(names = "--option-type", defaultValue = "CALL") String optionType;
        @Option(names = "--from") LocalDate from;
        @Option(names = "--to") LocalDate to;

        @Override
        void run(CliOperations ops) {
            LocalDate end = to == null ? LocalDate.now() : to;
            LocalDate start = from == null ? end.minusDays(30) : from;
            ops.rollingOption(underlying, segment, intervalMinutes, expiryFlag, expiryCode, strike, optionType, start, end);
        }
    }

    @Command(name = "stream-read-model", description = "Sample SSE read-model stream from trade-app")
    static final class StreamReadModelCmd extends BaseCmd {
        @Option(names = "--seconds", defaultValue = "30") int seconds;

        @Override
        void run(CliOperations ops) {
            ops.streamReadModel(seconds);
        }
    }

    @Command(name = "kill-switch")
    static final class KillSwitchCmd extends BaseCmd {
        @Parameters(index = "0") String state;
        @Option(names = "--confirm") boolean confirm;

        @Override
        void run(CliOperations ops) {
            if (confirm) {
                parent.yes = true;
            }
            ops.killSwitch("on".equalsIgnoreCase(state));
        }
    }

    @Command(name = "reconcile")
    static final class ReconcileCmd extends BaseCmd {
        @Parameters(index = "0") String json;

        @Override
        void run(CliOperations ops) throws Exception {
            ops.reconcile(json);
        }
    }

    @Command(name = "risk-config")
    static final class RiskConfigCmd extends BaseCmd {
        @Override
        void run(CliOperations ops) throws Exception {
            ops.showRiskConfig();
        }
    }

    @Command(
            name = "historical",
            subcommands = {
                    HistoricalCandlesCmd.class,
                    HistoricalTicksCmd.class,
                    HistoricalOrdersCmd.class,
                    HistoricalFillsCmd.class,
                    HistoricalStatsCmd.class
            }
    )
    static final class HistoricalCmd {
        @ParentCommand
        TradeCli root;
    }

    abstract static class NestedCmd implements Callable<Integer> {
        @ParentCommand
        HistoricalCmd historical;

        @Override
        public Integer call() throws Exception {
            run(historical.root.ops());
            return 0;
        }

        abstract void run(CliOperations ops) throws Exception;
    }

    abstract static class HistoricalBase extends NestedCmd {
        @Option(names = "--symbol", required = true) String symbol;
        @Option(names = "--from", required = true) long from;
        @Option(names = "--to", required = true) long to;
    }

    @Command(name = "candles")
    static final class HistoricalCandlesCmd extends HistoricalBase {
        @Option(names = "--interval", defaultValue = "5m") String interval;
        @Option(names = "--limit", defaultValue = "1000") int limit;

        @Override
        void run(CliOperations ops) {
            ops.historicalCandles(symbol, interval, from, to, limit);
        }
    }

    @Command(name = "ticks")
    static final class HistoricalTicksCmd extends HistoricalBase {
        @Option(names = "--limit", defaultValue = "5000") int limit;

        @Override
        void run(CliOperations ops) {
            ops.historicalTicks(symbol, from, to, limit);
        }
    }

    @Command(name = "orders")
    static final class HistoricalOrdersCmd extends HistoricalBase {
        @Option(names = "--limit", defaultValue = "500") int limit;

        @Override
        void run(CliOperations ops) {
            ops.historicalOrders(symbol, from, to, limit);
        }
    }

    @Command(name = "fills")
    static final class HistoricalFillsCmd extends HistoricalBase {
        @Option(names = "--limit", defaultValue = "500") int limit;

        @Override
        void run(CliOperations ops) {
            ops.historicalFills(symbol, from, to, limit);
        }
    }

    @Command(name = "stats")
    static final class HistoricalStatsCmd extends HistoricalBase {
        @Override
        void run(CliOperations ops) {
            ops.historicalStats(symbol, from, to);
        }
    }

    @Command(
            name = "replay",
            subcommands = {
                    ReplayTicksCmd.class,
                    ReplayCandlesCmd.class,
                    ReplayFillsCmd.class,
                    ReplayOrdersCmd.class,
                    ReplayChronicleCmd.class
            }
    )
    static final class ReplayCmd {
        @ParentCommand
        TradeCli root;
    }

    abstract static class ReplayNestedCmd implements Callable<Integer> {
        @ParentCommand
        ReplayCmd replay;

        @Override
        public Integer call() throws Exception {
            run(replay.root.ops());
            return 0;
        }

        abstract void run(CliOperations ops) throws Exception;
    }

    abstract static class ReplayBase extends ReplayNestedCmd {
        @Option(names = "--symbol") String symbol;
        @Option(names = "--from", required = true) long from;
        @Option(names = "--to", required = true) long to;
    }

    @Command(name = "ticks")
    static final class ReplayTicksCmd extends ReplayBase {
        @Override
        void run(CliOperations ops) {
            ops.replayTicks(symbol, from, to);
        }
    }

    @Command(name = "candles")
    static final class ReplayCandlesCmd extends ReplayBase {
        @Option(names = "--interval", defaultValue = "5m") String interval;

        @Override
        void run(CliOperations ops) {
            ops.replayCandles(symbol, interval, from, to);
        }
    }

    @Command(name = "fills")
    static final class ReplayFillsCmd extends ReplayBase {
        @Override
        void run(CliOperations ops) {
            ops.replayFills(symbol == null ? "" : symbol, from, to);
        }
    }

    @Command(name = "orders")
    static final class ReplayOrdersCmd extends ReplayBase {
        @Override
        void run(CliOperations ops) {
            ops.replayOrders(symbol == null ? "" : symbol, from, to);
        }
    }

    @Command(name = "chronicle")
    static final class ReplayChronicleCmd extends ReplayNestedCmd {
        @Parameters(index = "0") String eventType;

        @Override
        void run(CliOperations ops) {
            ops.replayChronicle(eventType);
        }
    }

    @Command(name = "place")
    static final class PlaceCmd extends BaseCmd {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Option(names = "--side", defaultValue = "BUY") String side;
        @Option(names = "--qty", defaultValue = "1") long qty;
        @Option(names = "--type", defaultValue = "LIMIT") String orderType;
        @Option(names = "--price", defaultValue = "0") long price;
        @Option(names = "--product", defaultValue = "INTRADAY") String product;

        @Override
        void run(CliOperations ops) {
            ops.placeSandboxOrder(symbol, segment, side, qty, orderType, price, product);
        }
    }

    @Command(name = "cancel")
    static final class CancelCmd extends BaseCmd {
        @Parameters(index = "0") String orderId;

        @Override
        void run(CliOperations ops) {
            ops.cancelOrder(orderId);
        }
    }

    @Command(name = "modify")
    static final class ModifyCmd extends BaseCmd {
        @Parameters(index = "0") String orderId;
        @Option(names = "--qty", required = true) long qty;
        @Option(names = "--price", required = true) long price;

        @Override
        void run(CliOperations ops) {
            ops.modifyOrder(orderId, qty, price);
        }
    }

    @Command(name = "token", subcommands = TokenRefreshSubCmd.class)
    static final class TokenCmd {
        @ParentCommand
        TradeCli root;
    }

    @Command(name = "refresh", description = "Refresh Dhan access token via scripts/refresh-dhan-token.sh")
    static final class TokenRefreshSubCmd implements Callable<Integer> {
        @ParentCommand
        TokenCmd token;

        @Override
        public Integer call() throws Exception {
            token.root.ops().tokenRefresh();
            return 0;
        }
    }

    @Command(name = "catalog", subcommands = CatalogRefreshCmd.class, description = "Dhan instrument master catalog")
    static final class CatalogCmd {
        @ParentCommand
        TradeCli root;
    }

    @Command(name = "refresh", description = "Download/load Dhan instrument catalog into CLI cache")
    static final class CatalogRefreshCmd implements Callable<Integer> {
        @ParentCommand
        CatalogCmd catalog;

        @Option(names = "--force", description = "Re-download even if today's cache exists")
        boolean force;

        @Override
        public Integer call() {
            catalog.root.ops().refreshCatalog(force);
            return 0;
        }
    }

    @Command(name = "portfolio", subcommands = {
            PortfolioSummaryCmd.class,
            PortfolioPositionsCmd.class,
            PortfolioHoldingsCmd.class,
            PortfolioPnlCmd.class
    }, description = "Standalone portfolio analysis (no trade-app required)")
    static final class PortfolioCmd {
        @ParentCommand
        TradeCli root;
    }

    abstract static class PortfolioNestedCmd implements Callable<Integer> {
        @ParentCommand
        PortfolioCmd portfolio;

        @Override
        public Integer call() throws Exception {
            run(portfolio.root.ops());
            return 0;
        }

        abstract void run(CliOperations ops) throws Exception;
    }

    @Command(name = "summary", description = "Portfolio summary — balance, positions, holdings")
    static final class PortfolioSummaryCmd extends PortfolioNestedCmd {
        @Override
        void run(CliOperations ops) { ops.portfolioSummary(); }
    }

    @Command(name = "positions", description = "Open positions with mark-to-market")
    static final class PortfolioPositionsCmd extends PortfolioNestedCmd {
        @Override
        void run(CliOperations ops) { ops.portfolioPositions(); }
    }

    @Command(name = "holdings", description = "Long-term holdings")
    static final class PortfolioHoldingsCmd extends PortfolioNestedCmd {
        @Override
        void run(CliOperations ops) { ops.portfolioHoldings(); }
    }

    @Command(name = "pnl", description = "Live P&L snapshot")
    static final class PortfolioPnlCmd extends PortfolioNestedCmd {
        @Override
        void run(CliOperations ops) { ops.portfolioPnl(); }
    }

    @Command(name = "screener", subcommands = {
            ScreenerRunCmd.class,
            ScreenerResultsCmd.class
    }, description = "Standalone screener (use --profile to run a scan profile)")
    static final class ScreenerCmd {
        @ParentCommand
        TradeCli root;
    }

    @Command(name = "run", description = "Run screener profile")
    static final class ScreenerRunCmd implements Callable<Integer> {
        @ParentCommand
        ScreenerCmd screener;
        @Option(names = "--profile", required = true) String profile;
        @Override
        public Integer call() throws Exception {
            screener.root.ops().screenerRun(profile);
            return 0;
        }
    }

    @Command(name = "results", description = "Show screener results")
    static final class ScreenerResultsCmd implements Callable<Integer> {
        @ParentCommand
        ScreenerCmd screener;
        @Option(names = "--profile", required = true) String profile;
        @Option(names = "--last", defaultValue = "5") int last;
        @Override
        public Integer call() throws Exception {
            screener.root.ops().screenerResults(profile, last);
            return 0;
        }
    }

    @Command(name = "broker-validate", description = "Run broker certification smoke test")
    static final class BrokerValidateCmd extends BaseCmd {
        @Parameters(index = "0", defaultValue = "RELIANCE") String symbol;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Option(names = "--skip-orders", description = "Skip order book check") boolean skipOrders;

        @Override
        void run(CliOperations ops) {
            ops.brokerValidate(symbol, segment, skipOrders);
        }
    }

    @Command(name = "scan", subcommands = {ScanRunCmd.class, ScanListCmd.class})
    static final class ScanCmd {
        @ParentCommand
        TradeCli root;
    }

    @Command(name = "run", description = "Run an intraday scan profile")
    static final class ScanRunCmd implements Callable<Integer> {
        @ParentCommand
        ScanCmd scan;
        @Option(names = "--profile", required = true, description = "Scan profile id") String profile;

        @Override
        public Integer call() throws Exception {
            scan.root.ops().scanRun(profile);
            return 0;
        }
    }

    @Command(name = "list", description = "List recent scan runs for a profile")
    static final class ScanListCmd implements Callable<Integer> {
        @ParentCommand
        ScanCmd scan;
        @Option(names = "--profile", required = true) String profile;
        @Option(names = "--last", defaultValue = "5") int last;

        @Override
        public Integer call() throws Exception {
            scan.root.ops().scanList(profile, last);
            return 0;
        }
    }

    @Command(name = "download", subcommands = {
            DownloadStartCmd.class,
            DownloadJobsCmd.class,
            DownloadStatusCmd.class,
            DownloadResumeCmd.class,
            DownloadResetCmd.class
    }, description = "Historical data download jobs")
    static final class DownloadCmd {
        @ParentCommand
        TradeCli root;
    }

    @Command(name = "start", subcommands = {
            DownloadStartRollingOptionsCmd.class,
            DownloadStartEquityCmd.class
    }, description = "Start a download job")
    static final class DownloadStartCmd {
        @ParentCommand
        DownloadCmd download;
    }

    @Command(name = "rolling-options", description = "Start Dhan expired rolling option backfill")
    static final class DownloadStartRollingOptionsCmd implements Callable<Integer> {
        @ParentCommand
        DownloadStartCmd startCmd;
        @Option(names = "--symbols", defaultValue = "NIFTY,BANKNIFTY") String symbols;
        @Option(names = "--segment", defaultValue = "IDX_I") String segment;
        @Option(names = "--from") LocalDate from;
        @Option(names = "--to") LocalDate to;
        @Option(names = "--intervals", defaultValue = "5") String intervals;
        @Option(names = "--expiry", defaultValue = "WEEK:1,WEEK:2,MONTH:1") String expiry;
        @Option(names = "--strikes", defaultValue = "atm±10") String strikes;
        @Option(names = "--option-types", defaultValue = "CALL,PUT") String optionTypes;
        @Option(names = "--warehouse", defaultValue = "runtime-dev/historical.duckdb") String warehouse;
        @Option(names = "--delay-ms", defaultValue = "0") long delayMs;
        @Option(names = "--workers", defaultValue = "2") int workers;
        @Option(names = "--plan-only", description = "Create job and print task count without calling Dhan") boolean planOnly;

        @Override
        public Integer call() throws Exception {
            LocalDate endDate = to == null ? LocalDate.now() : to;
            LocalDate startDate = from == null ? LocalDate.of(2021, 1, 1) : from;
            startCmd.download.root.ops().downloadRollingOptions(
                    symbols, segment, startDate, endDate, intervals, expiry, strikes, optionTypes,
                    warehouse, delayMs, workers, !planOnly
            );
            return 0;
        }
    }

    @Command(name = "equity", description = "Start Upstox NSE_EQ intraday equity backfill")
    static final class DownloadStartEquityCmd implements Callable<Integer> {
        @ParentCommand
        DownloadStartCmd startCmd;
        @Option(names = "--universe", description = "Use nifty500 to load online universe") String universe;
        @Option(names = "--symbols", description = "Comma-separated symbols (ignored when --universe nifty500)") String symbols;
        @Option(names = "--segment", defaultValue = "NSE_EQ") String segment;
        @Option(names = "--from") LocalDate from;
        @Option(names = "--to") LocalDate to;
        @Option(names = "--interval", defaultValue = "1m") String interval;
        @Option(names = "--root", defaultValue = "data/historical-equity") String root;
        @Option(names = "--delay-ms", defaultValue = "200") long delayMs;
        @Option(names = "--workers", defaultValue = "8") int workers;
        @Option(names = "--no-refresh-universe", description = "Skip online Nifty 500 refresh before job start") boolean skipUniverseRefresh;
        @Option(names = "--plan-only", description = "Create job metadata without calling Upstox") boolean planOnly;

        @Override
        public Integer call() throws Exception {
            String universeOrSymbols = universe != null && !universe.isBlank()
                    ? universe
                    : (symbols == null || symbols.isBlank() ? "nifty500" : symbols);
            LocalDate endDate = to == null ? LocalDate.now() : to;
            LocalDate startDate = from == null ? endDate.minusDays(89) : from;
            startCmd.download.root.ops().downloadEquity(
                    universeOrSymbols,
                    segment,
                    startDate,
                    endDate,
                    interval,
                    root,
                    delayMs,
                    workers,
                    !skipUniverseRefresh,
                    !planOnly
            );
            return 0;
        }
    }

    @Command(name = "jobs", subcommands = DownloadJobsListCmd.class, description = "Download job registry")
    static final class DownloadJobsCmd {
        @ParentCommand
        DownloadCmd download;
    }

    @Command(name = "list", description = "List recent download jobs by source")
    static final class DownloadJobsListCmd implements Callable<Integer> {
        @ParentCommand
        DownloadJobsCmd jobs;
        @Option(names = "--source", required = true, description = "EQUITY_INTRADAY or ROLLING_OPTION") String source;
        @Option(names = "--equity-root", defaultValue = "data/historical-equity") String equityRoot;
        @Option(names = "--options-warehouse", defaultValue = "runtime-dev/historical.duckdb") String optionsWarehouse;
        @Option(names = "--limit", defaultValue = "20") int limit;

        @Override
        public Integer call() throws Exception {
            jobs.download.root.ops().downloadJobsList(source, equityRoot, optionsWarehouse, limit);
            return 0;
        }
    }

    @Command(name = "status", description = "Show download job status")
    static final class DownloadStatusCmd implements Callable<Integer> {
        @ParentCommand
        DownloadCmd download;
        @Option(names = "--job-id", required = true) String jobId;
        @Option(names = "--warehouse", defaultValue = "runtime-dev/historical.duckdb") String warehouse;

        @Override
        public Integer call() throws Exception {
            download.root.ops().downloadStatus(jobId, warehouse);
            return 0;
        }
    }

    @Command(name = "resume", description = "Resume a download job")
    static final class DownloadResumeCmd implements Callable<Integer> {
        @ParentCommand
        DownloadCmd download;
        @Option(names = "--job-id", required = true) String jobId;
        @Option(names = "--warehouse", defaultValue = "runtime-dev/historical.duckdb") String warehouse;
        @Option(names = "--delay-ms", defaultValue = "0") long delayMs;
        @Option(names = "--workers", defaultValue = "2") int workers;

        @Override
        public Integer call() throws Exception {
            download.root.ops().downloadResume(jobId, warehouse, delayMs, workers);
            return 0;
        }
    }

    @Command(name = "reset", description = "Truncate warehouse download tables for a fresh start")
    static final class DownloadResetCmd implements Callable<Integer> {
        @ParentCommand
        DownloadCmd download;
        @Option(names = "--warehouse", defaultValue = "runtime-dev/historical.duckdb") String warehouse;

        @Override
        public Integer call() throws Exception {
            download.root.ops().downloadReset(warehouse);
            return 0;
        }
    }

    @Command(name = "universe", subcommands = {UniverseRefreshNifty500Cmd.class, UniverseRefreshFromDiskCmd.class}, description = "Index universe maintenance")
    static final class UniverseCmd {
        @ParentCommand
        TradeCli root;
    }

    @Command(name = "refresh-nifty500", description = "Download Nifty 500 constituents + industry map")
    static final class UniverseRefreshNifty500Cmd implements Callable<Integer> {
        @ParentCommand
        UniverseCmd universe;
        @Option(names = "--root", defaultValue = "data/historical-equity") String root;

        @Override
        public Integer call() throws Exception {
            universe.root.ops().refreshNifty500Universe(root);
            return 0;
        }
    }

    @Command(name = "refresh-from-disk", description = "Rebuild universe parquet from on-disk symbol directories")
    static final class UniverseRefreshFromDiskCmd implements Callable<Integer> {
        @ParentCommand
        UniverseCmd universe;
        @Option(names = "--root", defaultValue = "data/historical-equity") String root;

        @Override
        public Integer call() throws Exception {
            universe.root.ops().refreshUniverseFromDisk(root);
            return 0;
        }
    }

    @Command(name = "equity", subcommands = {EquityImportHiveCmd.class, EquityCompactPartitionsCmd.class}, description = "Equity historical warehouse maintenance")
    static final class EquityCmd {
        @ParentCommand
        TradeCli root;
    }

    @Command(name = "import-hive", description = "Import 1m bars from an external hive cache into historical-equity layout")
    static final class EquityImportHiveCmd implements Callable<Integer> {
        @ParentCommand
        EquityCmd equity;
        @Option(names = "--source-hive", required = true, description = "Source hive root (year_month= partitions)")
        String sourceHive;
        @Option(names = "--universe-csv", required = true, description = "Nifty 500 constituents CSV")
        String universeCsv;
        @Option(names = "--industry-parquet", required = true, description = "Industry mapping parquet")
        String industryParquet;
        @Option(names = "--root", defaultValue = "data/historical-equity") String root;
        @Option(names = "--from-month", defaultValue = "2020-01") String fromMonth;
        @Option(names = "--to-month", description = "Inclusive YYYY-MM; default = latest source partition")
        String toMonth;
        @Option(names = "--symbols", description = "Optional comma-separated symbol subset")
        String symbols;
        @Option(names = "--force", description = "Overwrite existing part-hive parquet files")
        boolean force;
        @Option(names = "--skip-universe-import", description = "Skip rewriting universe parquet snapshots")
        boolean skipUniverseImport;

        @Override
        public Integer call() throws Exception {
            equity.root.ops().importEquityHive(
                    sourceHive,
                    universeCsv,
                    industryParquet,
                    root,
                    fromMonth,
                    toMonth,
                    force,
                    symbols,
                    skipUniverseImport
            );
            return 0;
        }
    }

    @Command(name = "compact-partitions", description = "Merge part-{taskId}.parquet files into part-hive-YYYY-MM partitions")
    static final class EquityCompactPartitionsCmd implements Callable<Integer> {
        @ParentCommand
        EquityCmd equity;
        @Option(names = "--root", defaultValue = "data/historical-equity") String root;

        @Override
        public Integer call() throws Exception {
            equity.root.ops().compactEquityPartitions(root);
            return 0;
        }
    }

    @Command(name = "analytics", subcommands = {
            AnalyticsCatalogCmd.class,
            AnalyticsQueryEquityCmd.class,
            AnalyticsQueryOptionsCmd.class,
            AnalyticsSqlCmd.class
    }, description = "Federated historical analytics queries")
    static final class AnalyticsCmd {
        @ParentCommand
        TradeCli root;
    }

    @Command(name = "catalog", description = "Show federated analytics catalog snapshot")
    static final class AnalyticsCatalogCmd implements Callable<Integer> {
        @ParentCommand
        AnalyticsCmd analytics;
        @Option(names = "--equity-root", defaultValue = "data/historical-equity") String equityRoot;
        @Option(names = "--options-warehouse", defaultValue = "runtime-dev/historical.duckdb") String optionsWarehouse;

        @Override
        public Integer call() throws Exception {
            analytics.root.ops().analyticsCatalog(equityRoot, optionsWarehouse);
            return 0;
        }
    }

    @Command(name = "query-equity", description = "Query resampled equity candles from federated catalog")
    static final class AnalyticsQueryEquityCmd implements Callable<Integer> {
        @ParentCommand
        AnalyticsCmd analytics;
        @Option(names = "--symbol", required = true) String symbol;
        @Option(names = "--interval", defaultValue = "5m") String interval;
        @Option(names = "--from", required = true) LocalDate from;
        @Option(names = "--to", required = true) LocalDate to;
        @Option(names = "--equity-root", defaultValue = "data/historical-equity") String equityRoot;
        @Option(names = "--options-warehouse", defaultValue = "runtime-dev/historical.duckdb") String optionsWarehouse;

        @Override
        public Integer call() throws Exception {
            analytics.root.ops().analyticsQueryEquity(equityRoot, optionsWarehouse, symbol, interval, from, to);
            return 0;
        }
    }

    @Command(name = "query-options", description = "Query rolling option bars from federated catalog")
    static final class AnalyticsQueryOptionsCmd implements Callable<Integer> {
        @ParentCommand
        AnalyticsCmd analytics;
        @Option(names = "--underlying", required = true) String underlying;
        @Option(names = "--expiry-kind", defaultValue = "WEEK") String expiryKind;
        @Option(names = "--expiry-code", defaultValue = "1") int expiryCode;
        @Option(names = "--strike-offset", defaultValue = "0") int strikeOffset;
        @Option(names = "--option-type", defaultValue = "CALL") String optionType;
        @Option(names = "--interval-min", defaultValue = "5") int intervalMin;
        @Option(names = "--from-ms", required = true) long fromMs;
        @Option(names = "--to-ms", required = true) long toMs;
        @Option(names = "--limit", defaultValue = "1000") int limit;
        @Option(names = "--equity-root", defaultValue = "data/historical-equity") String equityRoot;
        @Option(names = "--options-warehouse", defaultValue = "runtime-dev/historical.duckdb") String optionsWarehouse;

        @Override
        public Integer call() throws Exception {
            analytics.root.ops().analyticsQueryOptions(
                    equityRoot, optionsWarehouse, underlying, expiryKind, expiryCode,
                    strikeOffset, optionType, intervalMin, fromMs, toMs, limit);
            return 0;
        }
    }

    @Command(name = "sql", description = "Execute guarded read-only SQL against analytics catalog views")
    static final class AnalyticsSqlCmd implements Callable<Integer> {
        @ParentCommand
        AnalyticsCmd analytics;
        @Option(names = "--equity-root", defaultValue = "data/historical-equity") String equityRoot;
        @Option(names = "--options-warehouse", defaultValue = "runtime-dev/historical.duckdb") String optionsWarehouse;
        @Option(names = "--file", description = "SQL file path") String file;
        @Parameters(index = "0", arity = "0..1", description = "Inline SQL query") String inlineSql;
        @Option(names = "--limit", defaultValue = "1000") int limit;

        @Override
        public Integer call() throws Exception {
            String sql = inlineSql;
            if (file != null && !file.isBlank()) {
                sql = java.nio.file.Files.readString(java.nio.file.Path.of(file));
            }
            if (sql == null || sql.isBlank()) {
                throw new IllegalArgumentException("Provide inline SQL or --file");
            }
            analytics.root.ops().analyticsSql(equityRoot, optionsWarehouse, sql, limit);
            return 0;
        }
    }

    @Command(name = "data", subcommands = {
            DataLtpCmd.class,
            DataQuoteCmd.class,
            DataDepthCmd.class,
            DataOhlcCmd.class,
            DataCandlesCmd.class,
            DataChainCmd.class
    }, description = "Standalone market data (no trade-app required)")
    static final class DataCmd {
        @ParentCommand
        TradeCli root;
    }

    abstract static class DataNestedCmd implements Callable<Integer> {
        @ParentCommand
        DataCmd data;

        @Override
        public Integer call() throws Exception {
            run(data.root.ops());
            return 0;
        }

        abstract void run(CliOperations ops) throws Exception;
    }

    @Command(name = "ltp", description = "Last traded price (standalone)")
    static final class DataLtpCmd extends DataNestedCmd {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Override
        void run(CliOperations ops) { ops.dataLtp(symbol, segment); }
    }

    @Command(name = "quote", description = "Full quote snapshot (standalone)")
    static final class DataQuoteCmd extends DataNestedCmd {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Override
        void run(CliOperations ops) { ops.dataQuote(symbol, segment); }
    }

    @Command(name = "depth", description = "Market depth (standalone)")
    static final class DataDepthCmd extends DataNestedCmd {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Override
        void run(CliOperations ops) { ops.dataDepth(symbol, segment); }
    }

    @Command(name = "ohlc", description = "OHLC snapshot (standalone)")
    static final class DataOhlcCmd extends DataNestedCmd {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Override
        void run(CliOperations ops) { ops.dataOhlc(symbol, segment); }
    }

    @Command(name = "candles", description = "Historical candles via broker REST (standalone)")
    static final class DataCandlesCmd extends DataNestedCmd {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "IDX_I") String segment;
        @Option(names = "--interval", defaultValue = "5m") String interval;
        @Option(names = "--from") LocalDate from;
        @Option(names = "--to") LocalDate to;
        @Override
        void run(CliOperations ops) {
            LocalDate end = to == null ? LocalDate.now() : to;
            LocalDate start = from == null ? end.minusDays(89) : from;
            ops.dataCandles(symbol, segment, interval, start, end);
        }
    }

    @Command(name = "chain", description = "Option chain (standalone)")
    static final class DataChainCmd extends DataNestedCmd {
        @Parameters(index = "0") String underlying;
        @Parameters(index = "1") String segment;
        @Option(names = "--expiry", required = true) LocalDate expiry;
        @Override
        void run(CliOperations ops) { ops.dataOptionChain(underlying, segment, expiry); }
    }

    @Command(name = "backtest", subcommands = {
            BacktestRunCmd.class,
            BacktestListCmd.class,
            BacktestStatusCmd.class
    }, description = "Standalone backtesting (no trade-app required)")
    static final class BacktestCmd {
        @ParentCommand
        TradeCli root;
    }

    abstract static class BacktestNestedCmd implements Callable<Integer> {
        @ParentCommand
        BacktestCmd backtest;

        @Override
        public Integer call() throws Exception {
            run(backtest.root.ops());
            return 0;
        }

        abstract void run(CliOperations ops) throws Exception;
    }

    @Command(name = "run", description = "Run a backtest (sma-crossover | buy-hold)")
    static final class BacktestRunCmd extends BacktestNestedCmd {
        @Option(names = "--strategy", required = true, description = "Strategy name (sma-crossover, buy-hold)") String strategy;
        @Option(names = "--symbol", required = true) String symbol;
        @Option(names = "--segment", defaultValue = "NSE_EQ") String segment;
        @Option(names = "--from", required = true) LocalDate from;
        @Option(names = "--to", required = true) LocalDate to;
        @Option(names = "--capital", defaultValue = "10000000", description = "Initial capital in paisa") long capital;
        @Override
        void run(CliOperations ops) throws Exception {
            ops.backtestRun(strategy, symbol, segment, from, to, capital);
        }
    }

    @Command(name = "list", description = "List recent backtest results")
    static final class BacktestListCmd extends BacktestNestedCmd {
        @Option(names = "--limit", defaultValue = "10") int limit;
        @Override
        void run(CliOperations ops) { ops.backtestList(limit); }
    }

    @Command(name = "status", description = "Check backtest run status")
    static final class BacktestStatusCmd extends BacktestNestedCmd {
        @Parameters(index = "0") String runId;
        @Override
        void run(CliOperations ops) { ops.backtestStatus(runId); }
    }

    // Standalone replay is now integrated into `tradej replay` via DuckDB fallback.
    // Use `tradej replay ticks --symbol --from --to` or `tradej replay candles --symbol --interval --from --to`.
    // The standalone DuckDB replay commands are available via the CliReplayCommands class.

    @Command(name = "test")
    static final class TestCmd extends BaseCmd {
        @Parameters(index = "0") String task;

        @Override
        void run(CliOperations ops) throws Exception {
            switch (task) {
                case "unit" -> ops.runGradleTest(":cli:cliUnitTest");
                case "broker-rest" -> ops.runGradleTest("brokerRestTest");
                case "preflight" -> ops.runGradleTest(":app:regressionPreflightTest");
                case "full-regression" -> ops.runProcess(java.util.List.of("bash", "scripts/run-full-regression.sh"));
                default -> throw new IllegalArgumentException("Unknown test task: " + task);
            }
        }
    }

    // Broker gateway and analytics commands extracted to CliBrokerGatewayCommands and CliAnalyticsCommands

    @Command(name = "query", description = "Run SQL against DuckDB analytics warehouse")
    static final class QueryCmd implements Callable<Integer> {
        @ParentCommand TradeCli root;
        @Parameters(index = "0", arity = "0..1", description = "SQL query") String inlineSql;
        @Option(names = "--file", description = "SQL file path") String file;
        @Option(names = "--equity-root", defaultValue = "data/historical-equity") String equityRoot;
        @Option(names = "--options-warehouse", defaultValue = "runtime-dev/historical.duckdb") String optionsWarehouse;
        @Option(names = "--limit", defaultValue = "1000") int limit;

        @Override
        public Integer call() throws Exception {
            String sql = inlineSql;
            if (file != null && !file.isBlank()) {
                sql = java.nio.file.Files.readString(java.nio.file.Path.of(file));
            }
            if (sql == null || sql.isBlank()) {
                System.err.println("Provide inline SQL or --file");
                return 1;
            }
            root.ops().analyticsSql(equityRoot, optionsWarehouse, sql, limit);
            return 0;
        }
    }

    @Command(name = "preview", description = "Preview order without placing (broker validation + margin estimate)")
    static final class PreviewCmd extends BaseCmd {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "NSE_EQ") String segment;
        @Option(names = "--side", defaultValue = "BUY") String side;
        @Option(names = "--qty", defaultValue = "1") long qty;
        @Option(names = "--order-type", defaultValue = "MARKET") String orderType;
        @Option(names = "--price", defaultValue = "0") long price;
        @Option(names = "--product", defaultValue = "INTRADAY") String product;

        @Override
        void run(CliOperations ops) {
            ops.previewOrder(symbol, segment, side, qty, orderType, price, product);
        }
    }

    @Command(name = "batch-quote", description = "Batch quote for multiple symbols")
    static final class BatchQuoteCmd extends BaseCmd {
        @Parameters(index = "0") String symbol1;
        @Parameters(index = "1") String symbol2;
        @Parameters(index = "2", defaultValue = "NSE_EQ") String segment;
        @Override void run(CliOperations ops) { ops.batchQuote(symbol1, symbol2, segment); }
    }

    @Command(name = "alerts", description = "List conditional alerts")
    static final class AlertsCmd extends BaseCmd {
        @Override void run(CliOperations ops) { ops.listAlerts(); }
    }

    @Command(name = "square-off", description = "Cancel all orders and square off intraday positions")
    static final class SquareOffCmd extends BaseCmd {
        @Override void run(CliOperations ops) { ops.cancelAndSquareOff(); }
    }

    @Command(name = "bracket", description = "Place bracket order (target + SL)")
    static final class BracketCmd extends BaseCmd {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "NSE_EQ") String segment;
        @Option(names = "--side", defaultValue = "BUY") String side;
        @Option(names = "--qty", defaultValue = "1") long qty;
        @Option(names = "--price", defaultValue = "0") long price;
        @Option(names = "--target", required = true) long target;
        @Option(names = "--sl", required = true) long sl;
        @Option(names = "--trailing", defaultValue = "0") long trailing;
        @Override void run(CliOperations ops) { ops.bracketOrder(symbol, segment, side, qty, price, target, sl, trailing); }
    }

    @Command(name = "gtt", description = "Place GTT (Good-Till-Triggered) order")
    static final class GttCmd extends BaseCmd {
        @Parameters(index = "0") String symbol;
        @Parameters(index = "1", defaultValue = "NSE_EQ") String segment;
        @Option(names = "--side", defaultValue = "BUY") String side;
        @Option(names = "--qty", defaultValue = "1") long qty;
        @Option(names = "--price", defaultValue = "0") long price;
        @Option(names = "--flag", defaultValue = "SINGLE") String flag;
        @Override void run(CliOperations ops) { ops.gttOrder(symbol, segment, side, qty, price, flag); }
    }

    @Command(name = "futures", description = "List futures contracts for underlying")
    static final class FuturesCmd extends BaseCmd {
        @Parameters(index = "0") String underlying;
        @Parameters(index = "1", defaultValue = "NSE_FNO") String segment;
        @Override void run(CliOperations ops) { ops.futuresContracts(underlying, segment); }
    }

    @Command(name = "health", description = "Run broker health check")
    static final class HealthCmd extends BaseCmd {
        @Parameters(index = "0", defaultValue = "dhan") String broker;
        @Override void run(CliOperations ops) { ops.brokerHealthCheck(broker); }
    }
}