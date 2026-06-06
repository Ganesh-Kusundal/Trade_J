package com.tradej.brokergateway.query;

import com.tradej.brokergateway.BrokerHandle;
import com.tradej.brokergateway.MarketGateway;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.value.ExchangeSegment;

import java.util.List;

/**
 * High-level query engine that combines broker data access with analytics.
 *
 * <p>Usage:
 * <pre>
 *   QueryEngine qe = new QueryEngine(marketGateway);
 *   OptionAnalytics.PcrResult pcr = qe.pcr("NIFTY");
 *   List&lt;OptionAnalytics.StrikeOi&gt; topOi = qe.topOi("NIFTY", 10);
 * </pre>
 */
public final class QueryEngine {

    private final MarketGateway marketGateway;

    public QueryEngine(MarketGateway marketGateway) {
        this.marketGateway = marketGateway;
    }

    // ── Option Analytics ────────────────────────────────────────────

    public OptionAnalytics.PcrResult pcr(String underlying) {
        return pcr(underlying, ExchangeSegment.IDX_I);
    }

    public OptionAnalytics.PcrResult pcr(String underlying, ExchangeSegment segment) {
        GatewayResult<OptionChainSnapshot> result = marketGateway.optionChain(underlying, segment, null);
        return OptionAnalytics.pcr(result.data());
    }

    public List<OptionAnalytics.StrikeOi> topOi(String underlying, int n) {
        return topOi(underlying, ExchangeSegment.IDX_I, n);
    }

    public List<OptionAnalytics.StrikeOi> topOi(String underlying, ExchangeSegment segment, int n) {
        GatewayResult<OptionChainSnapshot> result = marketGateway.optionChain(underlying, segment, null);
        return OptionAnalytics.topOi(result.data(), n);
    }

    public List<OptionAnalytics.StrikeOi> topVolume(String underlying, int n) {
        return topVolume(underlying, ExchangeSegment.IDX_I, n);
    }

    public List<OptionAnalytics.StrikeOi> topVolume(String underlying, ExchangeSegment segment, int n) {
        GatewayResult<OptionChainSnapshot> result = marketGateway.optionChain(underlying, segment, null);
        return OptionAnalytics.topVolume(result.data(), n);
    }

    public long maxPain(String underlying) {
        return maxPain(underlying, ExchangeSegment.IDX_I);
    }

    public long maxPain(String underlying, ExchangeSegment segment) {
        GatewayResult<OptionChainSnapshot> result = marketGateway.optionChain(underlying, segment, null);
        return OptionAnalytics.maxPainStrike(result.data());
    }

    public OptionAnalytics.SupportResistance supportResistance(String underlying) {
        return supportResistance(underlying, ExchangeSegment.IDX_I);
    }

    public OptionAnalytics.SupportResistance supportResistance(String underlying, ExchangeSegment segment) {
        GatewayResult<OptionChainSnapshot> result = marketGateway.optionChain(underlying, segment, null);
        return OptionAnalytics.supportResistance(result.data());
    }

    // ── Access to underlying gateway ────────────────────────────────

    public MarketGateway marketGateway() {
        return marketGateway;
    }
}
