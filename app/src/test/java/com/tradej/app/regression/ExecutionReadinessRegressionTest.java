package com.tradej.app.regression;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import com.tradej.historical.ingest.query.ParquetHistoricalBarRepository;
import com.tradej.historical.ingest.universe.HistoricalEquityPaths;
import com.tradej.institutional.InstitutionalScanEngine;
import com.tradej.institutional.model.InstitutionalScanConfig;
import com.tradej.institutional.model.InstitutionalScanResult;
import com.tradej.institutional.model.ScoredBar;
import com.tradej.simulation.MatchingEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Regression gate for the first deployable trading path:
 * real NIFTY500 parquet data -> 09:45 scan -> top 3 candidates ->
 * 09:50 continuation strategy -> simulated portfolio PnL -> paper/live gates.
 *
 * <p>This suite intentionally does not fabricate data. Missing or stale
 * warehouses are readiness failures, not skipped tests.
 */
@Tag("execution-readiness")
class ExecutionReadinessRegressionTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime SCAN_TIME = LocalTime.of(9, 45);
    private static final LocalTime STRATEGY_START = LocalTime.of(9, 50);
    private static final LocalTime EXIT_TIME = LocalTime.of(15, 15);
    private static final int TOP_N = 3;
    private static final long DEFAULT_CAPITAL_PAISA = 1_000_000L;

    @Test
    void realNifty500WarehouseSupportsScanAt0945() throws Exception {
        try (ParquetHistoricalBarRepository repository = openRepository()) {
            ReadinessData data = loadReadinessData(repository);

            assertTrue(data.universeSize() >= requiredUniverseSize(),
                    "NIFTY500 universe is incomplete: " + data.universeSize());
            assertTrue(data.symbolsWithData().size() >= requiredUniverseSize(),
                    "Not enough symbols have 1m bars on " + data.scanDate());
            assertFalse(data.benchmarkBars().isEmpty(), "NIFTY benchmark bars are required for relative strength");
            assertTrue(hasBarAt(data.benchmarkBars(), data.scanDate(), SCAN_TIME),
                    "NIFTY benchmark must have an exact 09:45 bar");

            Map<String, Long> exact0945BySymbol = symbolsWithBarAt(data.stockBars(), data.scanDate(), SCAN_TIME);
            assertTrue(exact0945BySymbol.size() >= requiredUniverseSize(),
                    "At least " + requiredUniverseSize() + " symbols need exact 09:45 bars; found "
                            + exact0945BySymbol.size());
        }
    }

    @Test
    void institutionalScanProducesTop3WithoutFallback() throws Exception {
        try (ParquetHistoricalBarRepository repository = openRepository()) {
            ReadinessData data = loadReadinessData(repository);
            InstitutionalScanResult result = runTop3Scan(repository, data.scanDate());

            assertEquals("09:45:00", result.scanTime(), "Scan must certify the exact requested cutoff");
            assertFalse(result.provenance().containsKey("fallbackReason"),
                    "Scan fallback is not allowed for production certification: " + result.provenance());
            assertEquals(TOP_N, result.candidates().size(), "First strategy path must use exactly top 3 candidates");
            assertEquals(TOP_N, result.candidates().stream().map(ScoredBar::symbol).distinct().count(),
                    "Top 3 candidates must be distinct");
            assertTrue(result.candidates().stream().allMatch(c -> isAt(c.barTime(), data.scanDate(), SCAN_TIME)),
                    "Every candidate must come from the exact 09:45 bar");
        }
    }

    @Test
    void top3ContinuationStrategyProducesPortfolioPnLFromRealCandles() throws Exception {
        try (ParquetHistoricalBarRepository repository = openRepository()) {
            ReadinessData data = loadReadinessData(repository);
            InstitutionalScanResult scan = runTop3Scan(repository, data.scanDate());
            PortfolioRun portfolioRun = runContinuationPortfolio(repository, scan, data.scanDate());

            assertEquals(TOP_N, portfolioRun.decisions().size(), "Every top-3 candidate needs an explicit decision");
            assertFalse(portfolioRun.trades().isEmpty(), "At least one candidate must be tradable with configured capital");
            assertTrue(portfolioRun.rejected().isEmpty(), "Risk/capital rejects must be explicit and resolved: "
                    + portfolioRun.rejected());
            assertNotEquals(portfolioRun.initialCapitalPaisa(), portfolioRun.finalCapitalPaisa(),
                    "Portfolio run must produce a realized PnL result from real fills");
        }
    }

    @Test
    void paperAndLiveGatesAreClosedUnlessCertifiedRunExists() throws Exception {
        try (ParquetHistoricalBarRepository repository = openRepository()) {
            ReadinessData data = loadReadinessData(repository);
            InstitutionalScanResult scan = runTop3Scan(repository, data.scanDate());
            PortfolioRun portfolioRun = runContinuationPortfolio(repository, scan, data.scanDate());
            GateDecision paperGate = paperGate(data, scan, portfolioRun);
            GateDecision liveGate = liveGate(paperGate);

            assertTrue(paperGate.open(), paperGate.reason());
            assertFalse(liveGate.open(), "Limited live must stay closed until paper evidence and operations gates exist");
            assertTrue(liveGate.reason().contains("paper sessions"),
                    "Live gate reason must name the missing production evidence: " + liveGate.reason());
        }
    }

    private static ParquetHistoricalBarRepository openRepository() throws Exception {
        Path root = historicalEquityRoot();
        Path barsDir = HistoricalEquityPaths.barsDir(root, "interval=1m");
        if (!Files.isDirectory(barsDir)) {
            fail("Historical equity 1m warehouse missing at " + barsDir
                    + ". Set trade.historical.equity.root or TRADE_HISTORICAL_EQUITY_ROOT to a real NIFTY500 lake.");
        }
        return new ParquetHistoricalBarRepository(root);
    }

    private static ReadinessData loadReadinessData(HistoricalBarRepository repository) {
        LocalDate scanDate = repository.latestAvailableTradingDay(maxLookbackDays())
                .orElseGet(() -> fail("No recent trading day available within "
                        + maxLookbackDays() + " days in the historical warehouse"));
        List<String> symbolsWithData = repository.querySymbolsWithDataOn(scanDate, requiredUniverseSize());
        if (symbolsWithData.size() < requiredUniverseSize()) {
            fail("NIFTY500 data incomplete on " + scanDate + ": expected at least "
                    + requiredUniverseSize() + " symbols, found " + symbolsWithData.size());
        }

        List<Candle> stockBars = repository.queryIntradayBars(symbolsWithData, scanDate);
        List<Candle> benchmarkBars = repository.queryBenchmarkBars(scanDate, "NIFTY");
        int universeSize = repository.queryUniverse().size();
        return new ReadinessData(scanDate, universeSize, symbolsWithData, stockBars, benchmarkBars);
    }

    private static InstitutionalScanResult runTop3Scan(HistoricalBarRepository repository, LocalDate scanDate) {
        InstitutionalScanConfig baseline = InstitutionalScanConfig.baseline();
        InstitutionalScanConfig config = new InstitutionalScanConfig(
                requiredUniverseSize(),
                TOP_N,
                baseline.maxPerSector(),
                "09:45:00",
                baseline.masterScoreWeights());
        return new InstitutionalScanEngine(repository, config).runHistoricalScan(scanDate, "09:45:00");
    }

    private static PortfolioRun runContinuationPortfolio(
            HistoricalBarRepository repository,
            InstitutionalScanResult scan,
            LocalDate scanDate
    ) {
        List<String> symbols = scan.candidates().stream().map(ScoredBar::symbol).toList();
        List<Candle> candles = repository.queryIntradayBars(symbols, scanDate);
        Map<String, List<Candle>> bySymbol = candles.stream()
                .collect(Collectors.groupingBy(Candle::symbol, LinkedHashMap::new, Collectors.toList()));

        long initialCapital = configuredCapitalPaisa();
        long maxOrderValue = Math.max(1L, initialCapital / TOP_N);
        long capital = initialCapital;
        MatchingEngine matchingEngine = new MatchingEngine(MatchingEngine.SlippageConfig.CONSERVATIVE);
        List<TradeDecision> decisions = new ArrayList<>();
        List<TradeResult> trades = new ArrayList<>();
        List<String> rejected = new ArrayList<>();

        for (ScoredBar candidate : scan.candidates()) {
            List<Candle> symbolBars = bySymbol.getOrDefault(candidate.symbol(), List.of()).stream()
                    .sorted(Comparator.comparingLong(Candle::startTimeMs))
                    .toList();
            Candle entry = firstBarAtOrAfter(symbolBars, scanDate, STRATEGY_START);
            Candle exit = lastBarAtOrBefore(symbolBars, scanDate, EXIT_TIME);
            if (entry == null || exit == null || !exitAfterEntry(exit, entry)) {
                String reason = "missing entry/exit bars for " + candidate.symbol();
                decisions.add(new TradeDecision(candidate.symbol(), "REJECT", reason));
                rejected.add(reason);
                continue;
            }

            long quantity = maxOrderValue / entry.closePaisa();
            if (quantity <= 0L) {
                String reason = "capital too small for " + candidate.symbol() + " at " + entry.closePaisa();
                decisions.add(new TradeDecision(candidate.symbol(), "REJECT", reason));
                rejected.add(reason);
                continue;
            }

            matchingEngine.onTick(candidate.symbol(), entry.closePaisa());
            var buy = matchingEngine.match(order(candidate.symbol(), Side.BUY, quantity),
                    "BT-BUY-" + UUID.randomUUID());
            matchingEngine.onTick(candidate.symbol(), exit.closePaisa());
            var sell = matchingEngine.match(order(candidate.symbol(), Side.SELL, buy.order().filledQuantity()),
                    "BT-SELL-" + UUID.randomUUID());

            if (buy.rejected() || sell.rejected()) {
                String reason = candidate.symbol() + " simulated fill rejected: "
                        + (buy.reason() == null ? sell.reason() : buy.reason());
                decisions.add(new TradeDecision(candidate.symbol(), "REJECT", reason));
                rejected.add(reason);
                continue;
            }

            long pnl = (sell.order().pricePaisa() - buy.order().pricePaisa()) * sell.order().filledQuantity();
            capital += pnl;
            decisions.add(new TradeDecision(candidate.symbol(), "TRADE", "filled"));
            trades.add(new TradeResult(
                    candidate.symbol(),
                    buy.order().filledQuantity(),
                    buy.order().pricePaisa(),
                    sell.order().pricePaisa(),
                    pnl));
        }

        return new PortfolioRun(initialCapital, capital, decisions, trades, rejected);
    }

    private static OrderRequest order(String symbol, Side side, long quantity) {
        return new OrderRequest(
                symbol,
                ExchangeSegment.NSE_EQ,
                side,
                quantity,
                OrderType.MARKET,
                0L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                "execution-readiness");
    }

    private static GateDecision paperGate(
            ReadinessData data,
            InstitutionalScanResult scan,
            PortfolioRun portfolioRun
    ) {
        boolean open = data.symbolsWithData().size() >= requiredUniverseSize()
                && scan.candidates().size() == TOP_N
                && scan.provenance().get("fallbackReason") == null
                && portfolioRun.rejected().isEmpty()
                && !portfolioRun.trades().isEmpty();
        String reason = open
                ? "paper gate open for replay-forward validation"
                : "paper gate closed: data, scan, and portfolio evidence are incomplete";
        return new GateDecision(open, reason);
    }

    private static GateDecision liveGate(GateDecision paperGate) {
        if (!paperGate.open()) {
            return new GateDecision(false, "live gate closed because paper gate is closed");
        }
        return new GateDecision(false,
                "live gate closed until multiple paper sessions, broker operations, monitoring, and rollback are certified");
    }

    private static Path historicalEquityRoot() {
        String configured = System.getProperty("trade.historical.equity.root");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("TRADE_HISTORICAL_EQUITY_ROOT");
        }
        if (configured == null || configured.isBlank()) {
            configured = HistoricalEquityPaths.DEFAULT_ROOT;
        }
        Path root = Path.of(configured);
        if (!root.isAbsolute()) {
            root = workspaceRoot().resolve(root).normalize();
        }
        return HistoricalEquityPaths.root(root);
    }

    private static Path workspaceRoot() {
        String root = System.getProperty("trade.workspace.root");
        return root == null || root.isBlank() ? Path.of(".").toAbsolutePath().normalize() : Path.of(root);
    }

    private static int requiredUniverseSize() {
        return Integer.getInteger("trade.readiness.minUniverseSize", 500);
    }

    private static int maxLookbackDays() {
        return Integer.getInteger("trade.readiness.maxLookbackDays", 30);
    }

    private static long configuredCapitalPaisa() {
        return Long.getLong("trade.readiness.capitalPaisa", DEFAULT_CAPITAL_PAISA);
    }

    private static boolean hasBarAt(List<Candle> candles, LocalDate date, LocalTime time) {
        return candles.stream().anyMatch(candle -> isAt(candle.startTimeMs(), date, time));
    }

    private static Map<String, Long> symbolsWithBarAt(List<Candle> candles, LocalDate date, LocalTime time) {
        return candles.stream()
                .filter(candle -> isAt(candle.startTimeMs(), date, time))
                .collect(Collectors.toMap(Candle::symbol, Candle::closePaisa, (left, right) -> left, LinkedHashMap::new));
    }

    private static Candle firstBarAtOrAfter(List<Candle> candles, LocalDate date, LocalTime time) {
        return candles.stream()
                .filter(candle -> isOnDate(candle, date))
                .filter(candle -> !localTime(candle.startTimeMs()).isBefore(time))
                .min(Comparator.comparingLong(Candle::startTimeMs))
                .orElse(null);
    }

    private static Candle lastBarAtOrBefore(List<Candle> candles, LocalDate date, LocalTime time) {
        return candles.stream()
                .filter(candle -> isOnDate(candle, date))
                .filter(candle -> !localTime(candle.startTimeMs()).isAfter(time))
                .max(Comparator.comparingLong(Candle::startTimeMs))
                .orElse(null);
    }

    private static boolean exitAfterEntry(Candle exit, Candle entry) {
        return exit.startTimeMs() > entry.startTimeMs();
    }

    private static boolean isOnDate(Candle candle, LocalDate date) {
        return Instant.ofEpochMilli(candle.startTimeMs()).atZone(IST).toLocalDate().equals(date);
    }

    private static boolean isAt(Instant instant, LocalDate date, LocalTime time) {
        return instant.atZone(IST).toLocalDate().equals(date)
                && instant.atZone(IST).toLocalTime().equals(time);
    }

    private static boolean isAt(long epochMs, LocalDate date, LocalTime time) {
        return isAt(Instant.ofEpochMilli(epochMs), date, time);
    }

    private static LocalTime localTime(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(IST).toLocalTime();
    }

    private record ReadinessData(
            LocalDate scanDate,
            int universeSize,
            List<String> symbolsWithData,
            List<Candle> stockBars,
            List<Candle> benchmarkBars
    ) {
    }

    private record PortfolioRun(
            long initialCapitalPaisa,
            long finalCapitalPaisa,
            List<TradeDecision> decisions,
            List<TradeResult> trades,
            List<String> rejected
    ) {
    }

    private record TradeDecision(String symbol, String action, String reason) {
    }

    private record TradeResult(String symbol, long quantity, long entryPaisa, long exitPaisa, long pnlPaisa) {
    }

    private record GateDecision(boolean open, String reason) {
    }
}
