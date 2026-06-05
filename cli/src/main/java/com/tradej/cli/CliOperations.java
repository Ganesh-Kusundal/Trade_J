package com.tradej.cli;

import com.tradej.cli.command.CliAttachCommands;
import com.tradej.cli.command.CliBrokerCommands;
import com.tradej.cli.command.CliCommandSupport;
import com.tradej.cli.command.CliDownloadCommands;
import com.tradej.cli.command.CliMaintenanceCommands;
import com.tradej.cli.command.CliScanCommands;
import com.tradej.cli.command.CliTradingCommands;
import com.tradej.cli.output.OutputFormatter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public final class CliOperations {
    private final CliContext context;
    private final OutputFormatter out;
    private final CliCommandSupport support;
    private final CliAttachCommands attach;
    private final CliBrokerCommands broker;
    private final CliTradingCommands trading;
    private final CliScanCommands scan;
    private final CliDownloadCommands download;
    private final CliMaintenanceCommands maintenance;

    public CliOperations(CliContext context) {
        this.context = context;
        this.out = new OutputFormatter(context.json());
        this.support = new CliCommandSupport(context, out);
        this.attach = new CliAttachCommands(context, out);
        this.broker = new CliBrokerCommands(context, out);
        this.trading = new CliTradingCommands(context, out);
        this.scan = new CliScanCommands(context, out);
        this.download = new CliDownloadCommands(context, out);
        this.maintenance = new CliMaintenanceCommands(context, out);
    }

    public OutputFormatter output() {
        return out;
    }

    public CliContext context() {
        return context;
    }

    public void status() { attach.status(); }
    public void runtime() { attach.runtime(); }
    public void pipeline() { attach.pipeline(); }
    public void strategies() { attach.strategies(); }
    public void summary() { attach.summary(); }
    public void orders() { attach.orders(); }
    public void positions() { attach.positions(); }
    public void readModel() { attach.readModel(); }
    public void streamReadModel(int seconds) { attach.streamReadModel(seconds); }
    public void killSwitch(boolean enabled) { attach.killSwitch(enabled); }
    public void reconcile(String jsonPayload) throws IOException { attach.reconcile(jsonPayload); }
    public void showRiskConfig() throws IOException { attach.showRiskConfig(); }
    public void historicalCandles(String symbol, String interval, long from, long to, int limit) {
        attach.historicalCandles(symbol, interval, from, to, limit);
    }
    public void historicalTicks(String symbol, long from, long to, int limit) {
        attach.historicalTicks(symbol, from, to, limit);
    }
    public void historicalOrders(String symbol, long from, long to, int limit) {
        attach.historicalOrders(symbol, from, to, limit);
    }
    public void historicalFills(String symbol, long from, long to, int limit) {
        attach.historicalFills(symbol, from, to, limit);
    }
    public void historicalStats(String symbol, long from, long to) {
        attach.historicalStats(symbol, from, to);
    }
    public void replayTicks(String symbol, long from, long to) { attach.replayTicks(symbol, from, to); }
    public void replayCandles(String symbol, String interval, long from, long to) {
        attach.replayCandles(symbol, interval, from, to);
    }
    public void replayFills(String symbol, long from, long to) { attach.replayFills(symbol, from, to); }
    public void replayOrders(String symbol, long from, long to) { attach.replayOrders(symbol, from, to); }
    public void replayChronicle(String eventType) { attach.replayChronicle(eventType); }

    public void balance() { broker.balance(); }
    public void holdings() { broker.holdings(); }
    public void brokerPositions() { broker.brokerPositions(); }
    public void refreshCatalog(boolean forceRefresh) { broker.refreshCatalog(forceRefresh); }
    public void ltp(String symbol, String segmentName) { broker.ltp(symbol, segmentName); }
    public void quote(String symbol, String segmentName) { broker.quote(symbol, segmentName); }
    public void depth(String symbol, String segmentName) { broker.depth(symbol, segmentName); }
    public void ohlc(String symbol, String segmentName) { broker.ohlc(symbol, segmentName); }
    public void candles(String symbol, String segmentName, String interval, LocalDate from, LocalDate to) {
        broker.candles(symbol, segmentName, interval, from, to);
    }
    public void orderBook() { broker.orderBook(); }
    public void trades() { broker.trades(); }
    public void order(String orderId) { broker.order(orderId); }
    public void livePnl() { broker.livePnl(); }
    public void expiries(String underlying, String segmentName) { broker.expiries(underlying, segmentName); }
    public void chain(String underlying, String segmentName, LocalDate expiry) {
        broker.chain(underlying, segmentName, expiry);
    }
    public void strike(String underlying, String segmentName, String kind, int depth) {
        broker.strike(underlying, segmentName, kind, depth);
    }
    public void margin(String symbol, String segmentName, String side, long quantity, String productType,
            String orderType, long pricePaisa) {
        broker.margin(symbol, segmentName, side, quantity, productType, orderType, pricePaisa);
    }
    public void rollingOption(String underlying, String segmentName, int intervalMinutes, String expiryFlag,
            int expiryCode, String strike, String optionType, LocalDate fromDate, LocalDate toDate) {
        broker.rollingOption(underlying, segmentName, intervalMinutes, expiryFlag, expiryCode,
                strike, optionType, fromDate, toDate);
    }

    public void placeSandboxOrder(String symbol, String segmentName, String side, long quantity, String orderType,
            long pricePaisa, String productType) {
        trading.placeSandboxOrder(symbol, segmentName, side, quantity, orderType, pricePaisa, productType);
    }
    public void cancelOrder(String orderId) { trading.cancelOrder(orderId); }
    public void modifyOrder(String orderId, long quantity, long pricePaisa) {
        trading.modifyOrder(orderId, quantity, pricePaisa);
    }

    public void scanRun(String profileId) throws Exception { scan.scanRun(profileId); }
    public void optionsScan(String underlying, String segmentName, String expiryPolicy, LocalDate explicitExpiry,
            String side, int top, long minOi, long minVolume, double maxSpreadBps, boolean strictSpread) throws Exception {
        scan.optionsScan(underlying, segmentName, expiryPolicy, explicitExpiry, side, top,
                minOi, minVolume, maxSpreadBps, strictSpread);
    }
    public void scanList(String profileId, int last) throws Exception { scan.scanList(profileId, last); }

    public void downloadRollingOptions(String symbols, String segment, LocalDate from, LocalDate to,
            String intervals, String expiry, String strikes, String optionTypes, String warehousePath,
            long delayMs, int workers, boolean runImmediately) throws Exception {
        download.downloadRollingOptions(symbols, segment, from, to, intervals, expiry, strikes, optionTypes,
                warehousePath, delayMs, workers, runImmediately);
    }
    public void downloadReset(String warehousePath) throws Exception { download.downloadReset(warehousePath); }
    public void downloadStatus(String jobId, String warehousePath) throws Exception {
        download.downloadStatus(jobId, warehousePath);
    }
    public void downloadResume(String jobId, String warehousePath, long delayMs, int workers) throws Exception {
        download.downloadResume(jobId, warehousePath, delayMs, workers);
    }
    public void refreshNifty500Universe(String rootPath) throws Exception { download.refreshNifty500Universe(rootPath); }
    public void refreshUniverseFromDisk(String rootPath) throws Exception { download.refreshUniverseFromDisk(rootPath); }
    public void compactEquityPartitions(String rootPath) throws Exception { download.compactEquityPartitions(rootPath); }
    public void analyticsCatalog(String equityRoot, String optionsWarehouse) throws Exception {
        download.analyticsCatalog(equityRoot, optionsWarehouse);
    }
    public void analyticsSql(String equityRoot, String optionsWarehouse, String sql, int limit) throws Exception {
        download.analyticsSql(equityRoot, optionsWarehouse, sql, limit);
    }
    public void analyticsQueryEquity(String equityRoot, String optionsWarehouse, String symbol, String interval,
            LocalDate from, LocalDate to) throws Exception {
        download.analyticsQueryEquity(equityRoot, optionsWarehouse, symbol, interval, from, to);
    }
    public void analyticsQueryOptions(String equityRoot, String optionsWarehouse, String underlying,
            String expiryKind, int expiryCode, int strikeOffset, String optionType, int intervalMin,
            long fromMs, long toMs, int limit) throws Exception {
        download.analyticsQueryOptions(equityRoot, optionsWarehouse, underlying, expiryKind, expiryCode,
                strikeOffset, optionType, intervalMin, fromMs, toMs, limit);
    }
    public void downloadJobsList(String source, String equityRoot, String optionsWarehouse, int limit) throws Exception {
        download.downloadJobsList(source, equityRoot, optionsWarehouse, limit);
    }
    public void downloadEquity(String universeOrSymbols, String segment, LocalDate from, LocalDate to, String interval,
            String rootPath, long delayMs, int workers, boolean refreshUniverse, boolean runImmediately) throws Exception {
        download.downloadEquity(universeOrSymbols, segment, from, to, interval, rootPath,
                delayMs, workers, refreshUniverse, runImmediately);
    }
    public void importEquityHive(String sourceHive, String universeCsv, String industryParquet, String rootPath,
            String fromMonth, String toMonth, boolean force, String symbols, boolean skipUniverseImport) throws Exception {
        download.importEquityHive(sourceHive, universeCsv, industryParquet, rootPath,
                fromMonth, toMonth, force, symbols, skipUniverseImport);
    }

    public int runProcess(List<String> command) throws IOException, InterruptedException {
        return maintenance.runProcess(command);
    }
    public void tokenRefresh() throws IOException, InterruptedException { maintenance.tokenRefresh(); }
    public void runGradleTest(String task) throws IOException, InterruptedException { maintenance.runGradleTest(task); }

    public String configuredRuntimeMode() { return support.configuredRuntimeMode(); }
    public static long defaultFromMs() { return CliCommandSupport.defaultFromMs(); }
    public static long defaultToMs() { return CliCommandSupport.defaultToMs(); }
}
