package com.tradej.broker.upstox.reconciliation;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;
import com.tradej.broker.upstox.mapper.UpstoxDomainMapper;
import com.tradej.broker.upstox.rest.UpstoxOrderRestClient;
import com.tradej.broker.upstox.rest.UpstoxPortfolioRestClient;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reconciles the local OMS state with the Upstox broker state on startup and
 * on demand.
 * <p>
 * The risk: missed fills, missed cancels, position drift between the local
 * OMS and the broker. The fix: at startup (and periodically), fetch orders,
 * trades, positions, and holdings from the broker, compare against the OMS
 * state passed in, and return a structured diff that the OMS can use to
 * repair.
 * <p>
 * The service is broker-local and uses only the public REST surface
 * (orders, trades, positions, holdings) plus the broker mapper. It does
 * <em>not</em> know about OMS internals — the caller passes in a
 * {@link OmsSnapshot} captured at startup time and acts on the resulting
 * {@link ReconciliationReport}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   var oms = new OmsSnapshot(List.of(openOrder1, openOrder2), List.of(), List.of(), List.of());
 *   var report = reconciliationService.reconcile(oms);
 *   report.driftItems().forEach(drift -> omsRepair.accept(drift));
 * }</pre>
 */
public final class UpstoxReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(UpstoxReconciliationService.class);

    private final UpstoxOrderRestClient orderRestClient;
    private final UpstoxPortfolioRestClient portfolioRestClient;
    private final UpstoxDomainMapper mapper;
    private final String sourceLabel;

    public UpstoxReconciliationService(UpstoxOrderRestClient orderRestClient,
                                      UpstoxPortfolioRestClient portfolioRestClient,
                                      UpstoxDomainMapper mapper,
                                      String sourceLabel) {
        this.orderRestClient = Objects.requireNonNull(orderRestClient, "orderRestClient");
        this.portfolioRestClient = Objects.requireNonNull(portfolioRestClient, "portfolioRestClient");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.sourceLabel = Objects.requireNonNull(sourceLabel, "sourceLabel");
    }

    public UpstoxReconciliationService(UpstoxOrderRestClient orderRestClient,
                                      UpstoxPortfolioRestClient portfolioRestClient,
                                      String sourceLabel) {
        this(orderRestClient, portfolioRestClient, new UpstoxDomainMapper(), sourceLabel);
    }

    /**
     * Performs a full reconciliation: fetches orders, trades, positions, and
     * holdings from the broker, compares against the supplied OMS snapshot,
     * and returns a {@link ReconciliationReport}.
     */
    public ReconciliationReport reconcile(OmsSnapshot oms) {
        Objects.requireNonNull(oms, "oms");
        long startedAtMs = System.currentTimeMillis();
        List<DriftItem> drifts = new ArrayList<>();
        int orderCount = 0, tradeCount = 0, positionCount = 0, holdingCount = 0;

        try {
            var orderBook = orderRestClient.getOrderBook();
            var brokerOrders = mapper.toOrderList(orderBook);
            orderCount = brokerOrders.size();
            drifts.addAll(diffOrders(oms.orders(), brokerOrders));
        } catch (Exception e) {
            log.warn("Reconciliation: failed to fetch order book: {}", e.getMessage());
        }

        try {
            var trades = orderRestClient.getTrades();
            var brokerTrades = mapper.toTradeList(trades);
            tradeCount = brokerTrades.size();
            drifts.addAll(diffTrades(oms.trades(), brokerTrades));
        } catch (Exception e) {
            log.warn("Reconciliation: failed to fetch trade book: {}", e.getMessage());
        }

        try {
            var positions = portfolioRestClient.getPositions();
            var brokerPositions = parsePositions(positions);
            positionCount = brokerPositions.size();
            drifts.addAll(diffPositions(oms.positions(), brokerPositions));
        } catch (Exception e) {
            log.warn("Reconciliation: failed to fetch positions: {}", e.getMessage());
        }

        try {
            var holdings = portfolioRestClient.getHoldings();
            var brokerHoldings = parseHoldings(holdings);
            holdingCount = brokerHoldings.size();
            drifts.addAll(diffHoldings(oms.holdings(), brokerHoldings));
        } catch (Exception e) {
            log.warn("Reconciliation: failed to fetch holdings: {}", e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - startedAtMs;
        log.info("Reconciliation[{}]: {} drift(s) in {}ms (orders={}, trades={}, positions={}, holdings={})",
                sourceLabel, drifts.size(), elapsed, orderCount, tradeCount, positionCount, holdingCount);

        return new ReconciliationReport(
                sourceLabel,
                Instant.ofEpochMilli(startedAtMs),
                elapsed,
                orderCount,
                tradeCount,
                positionCount,
                holdingCount,
                List.copyOf(drifts)
        );
    }

    // ─── Diff helpers ──────────────────────────────────────────────────────

    private List<DriftItem> diffOrders(List<Order> omsOrders, List<Order> brokerOrders) {
        List<DriftItem> drifts = new ArrayList<>();
        var omsById = omsOrders.stream().filter(o -> o.orderId() != null)
                .collect(java.util.stream.Collectors.toMap(Order::orderId, o -> o, (a, b) -> a));
        var brokerById = brokerOrders.stream().filter(o -> o.orderId() != null)
                .collect(java.util.stream.Collectors.toMap(Order::orderId, o -> o, (a, b) -> a));

        for (var entry : brokerById.entrySet()) {
            Order broker = entry.getValue();
            Order local = omsById.get(entry.getKey());
            if (local == null) {
                drifts.add(DriftItem.missingOrder(broker, sourceLabel));
            } else if (local.status() != broker.status()) {
                drifts.add(DriftItem.statusMismatch(local, broker, sourceLabel));
            } else if (local.filledQuantity() != broker.filledQuantity()) {
                drifts.add(DriftItem.fillMismatch(local, broker, sourceLabel));
            }
        }
        for (var entry : omsById.entrySet()) {
            if (!brokerById.containsKey(entry.getKey())) {
                drifts.add(DriftItem.unexpectedOrder(entry.getValue(), sourceLabel));
            }
        }
        return drifts;
    }

    private List<DriftItem> diffTrades(List<Trade> omsTrades, List<Trade> brokerTrades) {
        List<DriftItem> drifts = new ArrayList<>();
        var omsById = omsTrades.stream().filter(t -> t.tradeId() != null)
                .collect(java.util.stream.Collectors.toMap(Trade::tradeId, t -> t, (a, b) -> a));
        var brokerById = brokerTrades.stream().filter(t -> t.tradeId() != null)
                .collect(java.util.stream.Collectors.toMap(Trade::tradeId, t -> t, (a, b) -> a));

        for (var entry : brokerById.entrySet()) {
            if (!omsById.containsKey(entry.getKey())) {
                drifts.add(DriftItem.missingTrade(entry.getValue(), sourceLabel));
            }
        }
        for (var entry : omsById.entrySet()) {
            if (!brokerById.containsKey(entry.getKey())) {
                drifts.add(DriftItem.unexpectedTrade(entry.getValue(), sourceLabel));
            }
        }
        return drifts;
    }

    private List<DriftItem> diffPositions(List<Position> omsPositions, List<Position> brokerPositions) {
        List<DriftItem> drifts = new ArrayList<>();
        int omsNonZero = (int) omsPositions.stream().filter(p -> p.quantity() != 0).count();
        int brokerNonZero = (int) brokerPositions.stream().filter(p -> p.quantity() != 0).count();
        if (omsNonZero != brokerNonZero) {
            drifts.add(DriftItem.positionCountMismatch(omsNonZero, brokerNonZero, sourceLabel));
        }
        return drifts;
    }

    private List<DriftItem> diffHoldings(List<Holding> omsHoldings, List<Holding> brokerHoldings) {
        List<DriftItem> drifts = new ArrayList<>();
        if (omsHoldings.size() != brokerHoldings.size()) {
            drifts.add(DriftItem.holdingCountMismatch(omsHoldings.size(), brokerHoldings.size(), sourceLabel));
        }
        return drifts;
    }

    private List<Position> parsePositions(JsonNode positions) {
        List<Position> result = new ArrayList<>();
        JsonNode data = positions.get("data");
        if (data == null || !data.isArray()) {
            return result;
        }
        for (JsonNode node : data) {
            try {
                long qty = node.has("quantity") ? node.get("quantity").asLong() : 0L;
                if (qty == 0) {
                    continue;
                }
                com.tradej.core.domain.value.Side side = qty > 0
                        ? com.tradej.core.domain.value.Side.BUY
                        : com.tradej.core.domain.value.Side.SELL;
                result.add(new Position(
                        node.has("trading_symbol") ? node.get("trading_symbol").asText() : "",
                        UpstoxDomainMapper.parseSegment(node),
                        side,
                        Math.abs(qty),
                        node.has("average_price") ? (long) (node.get("average_price").asDouble() * 100) : 0L,
                        node.has("last_price") ? (long) (node.get("last_price").asDouble() * 100) : 0L,
                        0L
                ));
            } catch (Exception e) {
                log.debug("Failed to parse position node: {}", e.getMessage());
            }
        }
        return result;
    }

    private List<Holding> parseHoldings(JsonNode holdings) {
        List<Holding> result = new ArrayList<>();
        JsonNode data = holdings.get("data");
        if (data == null || !data.isArray()) {
            return result;
        }
        for (JsonNode node : data) {
            try {
                long qty = node.has("quantity") ? node.get("quantity").asLong() : 0L;
                result.add(new Holding(
                        node.has("trading_symbol") ? node.get("trading_symbol").asText() : "",
                        UpstoxDomainMapper.parseSegment(node),
                        qty,
                        qty,    // available
                        0L,     // collateral
                        node.has("average_price") ? (long) (node.get("average_price").asDouble() * 100) : 0L
                ));
            } catch (Exception e) {
                log.debug("Failed to parse holding node: {}", e.getMessage());
            }
        }
        return result;
    }

    // ─── Data types ────────────────────────────────────────────────────────

    /**
     * Snapshot of OMS state at the moment of reconciliation.
     */
    public record OmsSnapshot(
            List<Order> orders,
            List<Trade> trades,
            List<Position> positions,
            List<Holding> holdings
    ) {
        public OmsSnapshot {
            orders = orders == null ? List.of() : List.copyOf(orders);
            trades = trades == null ? List.of() : List.copyOf(trades);
            positions = positions == null ? List.of() : List.copyOf(positions);
            holdings = holdings == null ? List.of() : List.copyOf(holdings);
        }

        public static OmsSnapshot empty() {
            return new OmsSnapshot(List.of(), List.of(), List.of(), List.of());
        }
    }

    /**
     * Result of a reconciliation run.
     */
    public record ReconciliationReport(
            String source,
            Instant startedAt,
            long elapsedMs,
            int orderCount,
            int tradeCount,
            int positionCount,
            int holdingCount,
            List<DriftItem> driftItems
    ) {
        public boolean hasDrift() {
            return !driftItems.isEmpty();
        }
    }

    /**
     * A single detected drift between OMS and broker.
     */
    public record DriftItem(Kind kind, String description, String source) {

        public enum Kind {
            MISSING_ORDER,        // broker has order that OMS doesn't
            UNEXPECTED_ORDER,     // OMS has order that broker doesn't
            STATUS_MISMATCH,      // same orderId, different status
            FILL_MISMATCH,         // same orderId, different filled qty
            MISSING_TRADE,         // broker has trade that OMS doesn't
            UNEXPECTED_TRADE,      // OMS has trade that broker doesn't
            POSITION_COUNT_MISMATCH,
            HOLDING_COUNT_MISMATCH
        }

        static DriftItem missingOrder(Order o, String src) {
            return new DriftItem(Kind.MISSING_ORDER,
                    "Order " + o.orderId() + " present at broker, absent in OMS", src);
        }
        static DriftItem unexpectedOrder(Order o, String src) {
            return new DriftItem(Kind.UNEXPECTED_ORDER,
                    "Order " + o.orderId() + " present in OMS, absent at broker", src);
        }
        static DriftItem statusMismatch(Order local, Order broker, String src) {
            return new DriftItem(Kind.STATUS_MISMATCH,
                    "Order " + local.orderId() + " status " + local.status() + " -> " + broker.status(), src);
        }
        static DriftItem fillMismatch(Order local, Order broker, String src) {
            return new DriftItem(Kind.FILL_MISMATCH,
                    "Order " + local.orderId() + " filledQuantity " + local.filledQuantity() + " -> " + broker.filledQuantity(), src);
        }
        static DriftItem missingTrade(Trade t, String src) {
            return new DriftItem(Kind.MISSING_TRADE,
                    "Trade " + t.tradeId() + " present at broker, absent in OMS", src);
        }
        static DriftItem unexpectedTrade(Trade t, String src) {
            return new DriftItem(Kind.UNEXPECTED_TRADE,
                    "Trade " + t.tradeId() + " present in OMS, absent at broker", src);
        }
        static DriftItem positionCountMismatch(int oms, int broker, String src) {
            return new DriftItem(Kind.POSITION_COUNT_MISMATCH,
                    "Position count " + oms + " (OMS) vs " + broker + " (broker)", src);
        }
        static DriftItem holdingCountMismatch(int oms, int broker, String src) {
            return new DriftItem(Kind.HOLDING_COUNT_MISMATCH,
                    "Holding count " + oms + " (OMS) vs " + broker + " (broker)", src);
        }
    }
}
