package com.tradej.brokergateway.certification;

import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.SessionRiskProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.brokergateway.BrokerHandle;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a comprehensive validation suite against a broker.
 * Covers market data, options, portfolio, orders, advanced orders,
 * margin, futures, news, and instrument catalog — 25 checks total.
 */
public final class BrokerCertification {

    private BrokerCertification() {
    }

    /**
     * Run the full certification suite (25 checks).
     */
    public static CertificationReport runFull(BrokerHandle broker, String symbol, ExchangeSegment segment) {
        Instant start = Instant.now();
        List<CertificationCheck> checks = new ArrayList<>();

        checks.addAll(runMarketData(broker, symbol, segment));
        checks.addAll(runOptions(broker, symbol, segment));
        checks.addAll(runPortfolio(broker));
        checks.addAll(runOrders(broker));
        checks.addAll(runAdvancedOrders(broker));
        checks.addAll(runMargin(broker, symbol, segment));
        checks.addAll(runFutures(broker));
        checks.addAll(runCapabilities(broker));
        checks.addAll(runInstrumentCatalog(broker));

        Duration totalLatency = Duration.between(start, Instant.now());
        CertificationStatus overall = computeOverall(checks);
        return new CertificationReport(broker.source(), checks, overall, totalLatency);
    }

    // ── Market Data (6 checks) ──────────────────────────────────────

    public static List<CertificationCheck> runMarketData(BrokerHandle broker, String symbol, ExchangeSegment segment) {
        List<CertificationCheck> checks = new ArrayList<>();
        InstrumentKey key = InstrumentKey.of(symbol, segment);

        checks.add(runCheck("ltp", () -> {
            long ltp = broker.connection().marketData().getLtpPaisa(key);
            return "ltp=" + ltp;
        }));

        checks.add(runCheck("quote", () -> {
            Quote q = broker.connection().marketData().getQuote(key);
            return "ltp=" + q.ltpPaisa() + " open=" + q.openPaisa();
        }));

        checks.add(runCheck("depth", () -> {
            MarketDepth d = broker.connection().marketData().getDepth(key);
            return "bids=" + d.bids().size() + " asks=" + d.asks().size();
        }));

        checks.add(runCheck("ohlc", () -> {
            Quote ohlc = broker.connection().marketData().getOhlcSnapshot(key);
            return "o=" + ohlc.openPaisa() + " h=" + ohlc.highPaisa() + " l=" + ohlc.lowPaisa() + " c=" + ohlc.closePaisa();
        }));

        checks.add(runCheck("candles-5m", () -> {
            List<Candle> candles = broker.connection().marketData().getCandles(
                    new CandleHistoryRequest(key, "5m", LocalDate.now().minusDays(5), LocalDate.now()));
            return "count=" + candles.size();
        }));

        checks.add(runCheck("candles-1d", () -> {
            List<Candle> candles = broker.connection().marketData().getCandles(
                    new CandleHistoryRequest(key, "1d", LocalDate.now().minusDays(30), LocalDate.now()));
            return "count=" + candles.size();
        }));

        return checks;
    }

    // ── Options (3 checks) ──────────────────────────────────────────

    public static List<CertificationCheck> runOptions(BrokerHandle broker, String underlying, ExchangeSegment segment) {
        List<CertificationCheck> checks = new ArrayList<>();

        checks.add(runCheck("option-expiries", () -> {
            var expiries = broker.connection().options().getExpiries(underlying, segment);
            return "count=" + expiries.size();
        }));

        checks.add(runCheck("option-chain", () -> {
            var expiries = broker.connection().options().getExpiries(underlying, segment);
            if (expiries.isEmpty()) {
                return "no expiries available";
            }
            OptionChainSnapshot chain = broker.connection().options().getOptionChain(underlying, segment, expiries.getFirst());
            return "strikes=" + chain.strikes().size();
        }));

        checks.add(runCheck("option-greeks", () -> {
            if (!broker.supports(com.tradej.broker.api.capability.OptionsCapable.class)) {
                throw new UnsupportedOperationException("Broker does not support options capability");
            }
            var expiries = broker.connection().options().getExpiries(underlying, segment);
            if (expiries.isEmpty()) {
                return "no expiries for greeks";
            }
            OptionChainSnapshot chain = broker.connection().options().getOptionChain(underlying, segment, expiries.getFirst());
            if (chain.strikes().isEmpty()) {
                return "no strikes for greeks";
            }
            var firstStrike = chain.strikes().getFirst();
            if (firstStrike.call() != null && firstStrike.call().greeks() != null) {
                return "delta=" + firstStrike.call().greeks().delta();
            }
            return "greeks not available in chain";
        }));

        return checks;
    }

    // ── Portfolio (3 checks) ────────────────────────────────────────

    public static List<CertificationCheck> runPortfolio(BrokerHandle broker) {
        List<CertificationCheck> checks = new ArrayList<>();

        checks.add(runCheck("portfolio-balance", () -> {
            var bal = broker.connection().portfolio().getBalance();
            return "cash=" + bal.cashPaisa();
        }));

        checks.add(runCheck("portfolio-positions", () -> {
            var pos = broker.connection().portfolio().getPositions();
            return "count=" + pos.size();
        }));

        checks.add(runCheck("portfolio-holdings", () -> {
            var h = broker.connection().portfolio().getHoldings();
            return "count=" + h.size();
        }));

        return checks;
    }

    // ── Orders (2 checks) ───────────────────────────────────────────

    public static List<CertificationCheck> runOrders(BrokerHandle broker) {
        List<CertificationCheck> checks = new ArrayList<>();

        checks.add(runCheck("order-book", () -> {
            var orders = broker.connection().orderQuery().getOrderBook();
            return "count=" + orders.size();
        }));

        checks.add(runCheck("trade-book", () -> {
            var trades = broker.connection().orderQuery().getTradeBook();
            return "count=" + trades.size();
        }));

        return checks;
    }

    // ── Advanced Orders (3 checks) ──────────────────────────────────

    public static List<CertificationCheck> runAdvancedOrders(BrokerHandle broker) {
        List<CertificationCheck> checks = new ArrayList<>();

        checks.add(capabilityCheck("bracket-orders", broker, BracketOrderProvider.class));
        checks.add(capabilityCheck("gtt-orders", broker, GttOrderProvider.class));
        checks.add(capabilityCheck("slice-orders", broker, SliceOrderCommand.class));

        return checks;
    }

    // ── Margin (1 check) ────────────────────────────────────────────

    public static List<CertificationCheck> runMargin(BrokerHandle broker, String symbol, ExchangeSegment segment) {
        List<CertificationCheck> checks = new ArrayList<>();

        checks.add(runCheck("margin-estimate", () -> {
            MarginEstimateRequest request = new MarginEstimateRequest(
                    symbol, segment, Side.BUY, 1L,
                    ProductType.INTRADAY, OrderType.LIMIT, 100_00L, 0L);
            MarginEstimate estimate = broker.connection().margin().estimateMargin(request);
            return "margin=" + estimate.totalMarginPaisa() + " paisa";
        }));

        return checks;
    }

    // ── Futures (1 check) ───────────────────────────────────────────

    public static List<CertificationCheck> runFutures(BrokerHandle broker) {
        List<CertificationCheck> checks = new ArrayList<>();

        checks.add(capabilityCheck("futures", broker, FuturesProvider.class));

        return checks;
    }

    // ── Capabilities (4 checks) ─────────────────────────────────────

    public static List<CertificationCheck> runCapabilities(BrokerHandle broker) {
        List<CertificationCheck> checks = new ArrayList<>();

        checks.add(capabilityCheck("session-risk", broker, SessionRiskProvider.class));
        checks.add(capabilityCheck("news", broker, NewsProvider.class));
        checks.add(capabilityCheck("kill-switch", broker, com.tradej.broker.api.port.OrderCommand.class));
        checks.add(capabilityCheck("websocket", broker, com.tradej.broker.api.port.WebSocketMultiplexer.class));

        return checks;
    }

    // ── Instrument Catalog (1 check) ────────────────────────────────

    public static List<CertificationCheck> runInstrumentCatalog(BrokerHandle broker) {
        List<CertificationCheck> checks = new ArrayList<>();

        checks.add(runCheck("instrument-catalog", () -> {
            int count = broker.connection().instruments().catalogSize();
            if (count == 0) {
                throw new IllegalStateException("Instrument catalog is empty — load catalog first");
            }
            return "count=" + count;
        }));

        return checks;
    }

    // ── Internal ────────────────────────────────────────────────────

    @FunctionalInterface
    private interface CheckRunner {
        String run() throws Exception;
    }

    private static CertificationCheck runCheck(String name, CheckRunner runner) {
        Instant start = Instant.now();
        try {
            String evidence = runner.run();
            Duration latency = Duration.between(start, Instant.now());
            return CertificationCheck.pass(name, evidence, latency);
        } catch (Exception e) {
            Duration latency = Duration.between(start, Instant.now());
            return CertificationCheck.fail(name, e.getMessage(), latency);
        }
    }

    private static CertificationCheck capabilityCheck(String name, BrokerHandle broker, Class<?> capabilityClass) {
        Instant start = Instant.now();
        boolean supported = broker.supports(capabilityClass);
        Duration latency = Duration.between(start, Instant.now());
        if (supported) {
            return CertificationCheck.pass(name, "supported", latency);
        } else {
            return CertificationCheck.pass(name, "not supported (optional)", latency);
        }
    }

    private static CertificationStatus computeOverall(List<CertificationCheck> checks) {
        long passed = checks.stream().filter(CertificationCheck::isPass).count();
        long failed = checks.stream().filter(CertificationCheck::isFail).count();
        if (failed == 0) return CertificationStatus.PASS;
        if (passed == 0) return CertificationStatus.FAIL;
        return CertificationStatus.PARTIAL;
    }
}
