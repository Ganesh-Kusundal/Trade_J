package com.tradej.brokergateway.explorer;

import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.brokergateway.BrokerHandle;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.core.domain.instrument.IndexSymbols;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.StrikeSelectionKind;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link BrokerInspector} that makes live calls to each
 * broker capability and measures latency.
 *
 * <p>Probes 20 capabilities:
 * <ul>
 *   <li>Market data: ltp, quote, depth, ohlc, candles-5m</li>
 *   <li>Options: expiries, option-chain, option-greeks, option-contracts, select-strike</li>
 *   <li>Portfolio: balance, positions, holdings</li>
 *   <li>Orders: order-book, trade-book</li>
 *   <li>Margin: estimate-margin</li>
 *   <li>Futures: futures-contracts</li>
 *   <li>Advanced orders: bracket, gtt, slice (capability check only)</li>
 *   <li>News: news (capability check only)</li>
 *   <li>WebSocket: websocket-connected</li>
 *   <li>Catalog: instrument-catalog</li>
 * </ul>
 */
public final class DefaultBrokerInspector implements BrokerInspector {

    @Override
    public BrokerInspectionReport inspect(BrokerHandle broker, String symbol, ExchangeSegment segment) {
        // Static capabilities from BrokerExplorer
        BrokerInspectionReport staticReport = BrokerExplorer.inspect(broker);

        // Live probes
        List<CapabilityProbe> probes = new ArrayList<>();

        // ── Market Data (5 probes) ──
        probes.add(probeMarketData(broker, symbol, segment));
        probes.add(probeQuote(broker, symbol, segment));
        probes.add(probeDepth(broker, symbol, segment));
        probes.add(probeOhlc(broker, symbol, segment));
        probes.add(probeCandles(broker, symbol, segment));

        // ── Options (5 probes) ──
        String underlying = isIndex(symbol) ? symbol : "NIFTY";
        ExchangeSegment optionSegment = isIndex(symbol) ? ExchangeSegment.IDX_I : segment;
        probes.add(probeExpiries(broker, underlying, optionSegment));
        probes.add(probeOptionChain(broker, underlying, optionSegment));
        probes.add(probeOptionContracts(broker, underlying, optionSegment));
        probes.add(probeSelectStrike(broker, underlying, optionSegment));
        probes.add(probeOptionGreeks(broker, underlying, optionSegment));

        // ── Portfolio (3 probes) ──
        probes.add(probeBalance(broker));
        probes.add(probePositions(broker));
        probes.add(probeHoldings(broker));

        // ── Orders (2 probes) ──
        probes.add(probeOrderBook(broker));
        probes.add(probeTradeBook(broker));

        // ── Margin (1 probe) ──
        probes.add(probeMargin(broker, symbol, segment));

        // ── Futures (1 probe) ──
        probes.add(probeFutures(broker));

        // ── Advanced Orders (3 capability checks) ──
        probes.add(probeCapability(broker, "bracket-orders", BracketOrderProvider.class));
        probes.add(probeCapability(broker, "gtt-orders", GttOrderProvider.class));
        probes.add(probeCapability(broker, "slice-orders", SliceOrderCommand.class));

        // ── News (1 capability check) ──
        probes.add(probeCapability(broker, "news", NewsProvider.class));

        // ── WebSocket (1 probe) ──
        probes.add(probeWebSocket(broker));

        // ── Catalog (1 probe) ──
        probes.add(probeCatalog(broker));

        return new BrokerInspectionReport(
                broker.source(),
                staticReport.capabilities(),
                probes,
                staticReport.metadata(),
                staticReport.catalogLoaded(),
                staticReport.instrumentCount()
        );
    }

    // ── Market Data Probes ──────────────────────────────────────────

    private CapabilityProbe probeMarketData(BrokerHandle broker, String symbol, ExchangeSegment segment) {
        return timedProbe("ltp", () -> {
            GatewayResult<Long> result = broker.ltp(symbol, segment);
            return "ltp=" + result.data();
        });
    }

    private CapabilityProbe probeQuote(BrokerHandle broker, String symbol, ExchangeSegment segment) {
        return timedProbe("quote", () -> {
            GatewayResult<Quote> result = broker.quote(symbol, segment);
            Quote q = result.data();
            return "ltp=" + q.ltpPaisa() + " open=" + q.openPaisa();
        });
    }

    private CapabilityProbe probeDepth(BrokerHandle broker, String symbol, ExchangeSegment segment) {
        return timedProbe("depth", () -> {
            GatewayResult<MarketDepth> result = broker.depth(symbol, segment);
            MarketDepth d = result.data();
            return "bids=" + d.bids().size() + " asks=" + d.asks().size();
        });
    }

    private CapabilityProbe probeOhlc(BrokerHandle broker, String symbol, ExchangeSegment segment) {
        return timedProbe("ohlc", () -> {
            GatewayResult<Quote> result = broker.ohlc(symbol, segment);
            Quote q = result.data();
            return "o=" + q.openPaisa() + " h=" + q.highPaisa() + " l=" + q.lowPaisa() + " c=" + q.closePaisa();
        });
    }

    private CapabilityProbe probeCandles(BrokerHandle broker, String symbol, ExchangeSegment segment) {
        return timedProbe("candles-5m", () -> {
            GatewayResult<List<Candle>> result = broker.historical(
                    symbol, segment, "5m", LocalDate.now().minusDays(5), LocalDate.now());
            return "count=" + result.data().size();
        });
    }

    // ── Options Probes ──────────────────────────────────────────────

    private CapabilityProbe probeExpiries(BrokerHandle broker, String underlying, ExchangeSegment segment) {
        return timedProbe("option-expiries", () -> {
            GatewayResult<List<LocalDate>> result = broker.expiries(underlying, segment);
            return "count=" + result.data().size();
        });
    }

    private CapabilityProbe probeOptionChain(BrokerHandle broker, String underlying, ExchangeSegment segment) {
        return timedProbe("option-chain", () -> {
            GatewayResult<OptionChainSnapshot> result = broker.optionChain(underlying);
            if (result.data() == null) return "no chain available";
            return "strikes=" + result.data().strikes().size();
        });
    }

    private CapabilityProbe probeOptionContracts(BrokerHandle broker, String underlying, ExchangeSegment segment) {
        return timedProbe("option-contracts", () -> {
            List<LocalDate> expiries = broker.expiries(underlying, segment).data();
            if (expiries.isEmpty()) return "no expiries";
            GatewayResult<List<Instrument>> result = broker.optionContracts(underlying, segment, expiries.getFirst());
            return "count=" + result.data().size();
        });
    }

    private CapabilityProbe probeSelectStrike(BrokerHandle broker, String underlying, ExchangeSegment segment) {
        return timedProbe("select-strike", () -> {
            GatewayResult<Long> result = broker.selectStrike(
                    underlying, segment, 25000_00L, OptionType.CALL, StrikeSelectionKind.ATM, 0);
            return "strike=" + result.data();
        });
    }

    private CapabilityProbe probeOptionGreeks(BrokerHandle broker, String underlying, ExchangeSegment segment) {
        return timedProbe("option-greeks", () -> {
            GatewayResult<OptionChainSnapshot> chainResult = broker.optionChain(underlying);
            if (chainResult.data() == null || chainResult.data().strikes().isEmpty()) {
                return "no chain for greeks";
            }
            var firstStrike = chainResult.data().strikes().getFirst();
            if (firstStrike.call() != null && firstStrike.call().greeks() != null) {
                return "delta=" + firstStrike.call().greeks().delta();
            }
            return "greeks not available in chain";
        });
    }

    // ── Portfolio Probes ────────────────────────────────────────────

    private CapabilityProbe probeBalance(BrokerHandle broker) {
        return timedProbe("balance", () -> {
            GatewayResult<Balance> result = broker.balance();
            return "cash=" + result.data().cashPaisa();
        });
    }

    private CapabilityProbe probePositions(BrokerHandle broker) {
        return timedProbe("positions", () -> {
            GatewayResult<List<Position>> result = broker.positions();
            return "count=" + result.data().size();
        });
    }

    private CapabilityProbe probeHoldings(BrokerHandle broker) {
        return timedProbe("holdings", () -> {
            GatewayResult<List<Holding>> result = broker.holdings();
            return "count=" + result.data().size();
        });
    }

    // ── Order Probes ────────────────────────────────────────────────

    private CapabilityProbe probeOrderBook(BrokerHandle broker) {
        return timedProbe("order-book", () -> {
            GatewayResult<List<Order>> result = broker.orders();
            return "count=" + result.data().size();
        });
    }

    private CapabilityProbe probeTradeBook(BrokerHandle broker) {
        return timedProbe("trade-book", () -> {
            GatewayResult<List<Trade>> result = broker.trades();
            return "count=" + result.data().size();
        });
    }

    // ── Margin Probe ────────────────────────────────────────────────

    private CapabilityProbe probeMargin(BrokerHandle broker, String symbol, ExchangeSegment segment) {
        return timedProbe("estimate-margin", () -> {
            MarginEstimateRequest request = new MarginEstimateRequest(
                    symbol, segment, Side.BUY, 1L,
                    ProductType.INTRADAY, OrderType.LIMIT, 100_00L, 0L);
            GatewayResult<MarginEstimate> result = broker.estimateMargin(request);
            return "margin=" + result.data().totalMarginPaisa() + " paisa";
        });
    }

    // ── Futures Probe ───────────────────────────────────────────────

    private CapabilityProbe probeFutures(BrokerHandle broker) {
        return timedProbe("futures-contracts", () -> {
            GatewayResult<List<Instrument>> result = broker.futuresContracts("NIFTY", ExchangeSegment.NSE_FNO);
            return "count=" + result.data().size();
        });
    }

    // ── Capability Check Probes ─────────────────────────────────────

    private CapabilityProbe probeCapability(BrokerHandle broker, String name, Class<?> capabilityClass) {
        Instant start = Instant.now();
        boolean supported = broker.supports(capabilityClass);
        Duration latency = Duration.between(start, Instant.now());
        if (supported) {
            return CapabilityProbe.pass(name, "supported", latency);
        } else {
            return CapabilityProbe.skip(name, "not supported by this broker");
        }
    }

    // ── WebSocket Probe ─────────────────────────────────────────────

    private CapabilityProbe probeWebSocket(BrokerHandle broker) {
        Instant start = Instant.now();
        try {
            boolean connected = broker.isWebSocketConnected();
            Duration latency = Duration.between(start, Instant.now());
            return CapabilityProbe.pass("websocket-connected",
                    connected ? "connected" : "disconnected", latency);
        } catch (Exception e) {
            Duration latency = Duration.between(start, Instant.now());
            return CapabilityProbe.fail("websocket-connected", e.getMessage(), latency);
        }
    }

    // ── Catalog Probe ───────────────────────────────────────────────

    private CapabilityProbe probeCatalog(BrokerHandle broker) {
        Instant start = Instant.now();
        try {
            int count = broker.instrumentCount();
            Duration latency = Duration.between(start, Instant.now());
            if (count > 0) {
                return CapabilityProbe.pass("instrument-catalog", "count=" + count, latency);
            } else {
                return CapabilityProbe.fail("instrument-catalog", "catalog is empty", latency);
            }
        } catch (Exception e) {
            Duration latency = Duration.between(start, Instant.now());
            return CapabilityProbe.fail("instrument-catalog", e.getMessage(), latency);
        }
    }

    // ── Internal ────────────────────────────────────────────────────

    @FunctionalInterface
    private interface ProbeAction {
        String run() throws Exception;
    }

    private CapabilityProbe timedProbe(String name, ProbeAction action) {
        Instant start = Instant.now();
        try {
            String evidence = action.run();
            Duration latency = Duration.between(start, Instant.now());
            return CapabilityProbe.pass(name, evidence, latency);
        } catch (UnsupportedOperationException e) {
            Duration latency = Duration.between(start, Instant.now());
            return CapabilityProbe.skip(name, e.getMessage());
        } catch (Exception e) {
            Duration latency = Duration.between(start, Instant.now());
            return CapabilityProbe.fail(name, e.getMessage(), latency);
        }
    }

    private static boolean isIndex(String symbol) {
        return IndexSymbols.isIndexUnderlying(symbol);
    }
}
