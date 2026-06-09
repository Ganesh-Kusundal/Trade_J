package com.tradej.brokergateway;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.brokergateway.result.ResultMetadata;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.ExchangeSegment;

import java.time.LocalDate;
import java.util.List;

/**
 * Unified market access layer. Consumers do not need to know which broker is active.
 *
 * <p>Usage:
 * <pre>
 *   MarketGateway market = MarketGateway.create(gateway);
 *   GatewayResult&lt;Quote&gt; result = market.quote("RELIANCE");
 *   // result.source() tells you which broker produced the data
 * </pre>
 *
 * <p>Delegates all operations to the active broker via {@link BrokerRouter}.
 */
public final class MarketGateway {

    private final BrokerRouter router;

    private MarketGateway(BrokerRouter router) {
        this.router = router;
    }

    public static MarketGateway create(BrokerGateway gateway) {
        return new MarketGateway(new BrokerRouter(gateway));
    }

    public static MarketGateway create(BrokerRouter router) {
        return new MarketGateway(router);
    }

    // ── Market Data ─────────────────────────────────────────────────

    public GatewayResult<Long> ltp(String symbol) {
        return router.active().ltp(symbol);
    }

    public GatewayResult<Quote> quote(String symbol) {
        return router.active().quote(symbol);
    }

    public GatewayResult<Quote> quote(String symbol, ExchangeSegment segment) {
        return router.active().quote(symbol, segment);
    }

    public GatewayResult<MarketDepth> depth(String symbol) {
        return router.active().depth(symbol);
    }

    public GatewayResult<MarketDepth> depth(String symbol, ExchangeSegment segment) {
        return router.active().depth(symbol, segment);
    }

    public GatewayResult<Quote> ohlc(String symbol) {
        return router.active().ohlc(symbol);
    }

    public GatewayResult<List<Candle>> historical(String symbol, String interval, LocalDate from, LocalDate to) {
        return router.active().historical(symbol, interval, from, to);
    }

    // ── Options ─────────────────────────────────────────────────────

    public GatewayResult<OptionChainSnapshot> optionChain(String underlying) {
        return router.active().optionChain(underlying);
    }

    public GatewayResult<OptionChainSnapshot> optionChain(String underlying, ExchangeSegment segment, LocalDate expiry) {
        return router.active().optionChain(underlying, segment, expiry);
    }

    public GatewayResult<List<LocalDate>> expiries(String underlying) {
        return router.active().expiries(underlying);
    }

    // ── Portfolio ───────────────────────────────────────────────────

    public GatewayResult<Balance> balance() {
        return router.active().balance();
    }

    public GatewayResult<List<Position>> positions() {
        return router.active().positions();
    }

    public GatewayResult<List<Holding>> holdings() {
        return router.active().holdings();
    }

    // ── Router access ───────────────────────────────────────────────

    public BrokerRouter router() {
        return router;
    }

    public BrokerHandle activeBroker() {
        return router.active();
    }

    // ── Pragmatic bridge ─────────────────────────────────────────────

    // ── Capability discovery ──────────────────────────────────────────

    /**
     * Returns the capabilities of the active broker.
     */
    public GatewayResult<com.tradej.broker.api.model.BrokerCapabilities> capabilities() {
        BrokerHandle active = router.active();
        var caps = active.connection().getCapability(com.tradej.broker.api.model.BrokerCapabilities.class);
        var metadata = new ResultMetadata(
                java.time.Duration.ZERO, java.time.Instant.now(), "capabilities", java.util.Map.of());
        return caps.map(c -> GatewayResult.success(c, active.source(), metadata))
                .orElse(null);
    }
}
