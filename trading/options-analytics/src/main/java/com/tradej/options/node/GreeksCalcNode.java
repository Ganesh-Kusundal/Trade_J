package com.tradej.options.node;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.GreeksComputed;
import com.tradej.core.domain.event.OptionChainUpdated;
import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.OptionGreeks;
import com.tradej.core.domain.model.OptionQuote;
import com.tradej.core.domain.value.OptionType;
import com.tradej.options.calculator.BlackScholesCalculator;
import com.tradej.options.calculator.IVSolver;
import com.tradej.options.greeks.OptionsAnalyticsCache;
import com.tradej.pipeline.runtime.BasePipelineNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class GreeksCalcNode extends BasePipelineNode {

    private static final Logger log = LoggerFactory.getLogger(GreeksCalcNode.class);
    private static final double DEFAULT_RISK_FREE_RATE = 0.065;

    private final OptionsAnalyticsCache cache;
    private final double riskFreeRate;

    public GreeksCalcNode(OptionsAnalyticsCache cache) {
        this(cache, DEFAULT_RISK_FREE_RATE);
    }

    public GreeksCalcNode(OptionsAnalyticsCache cache, double riskFreeRate) {
        this.cache = cache;
        this.riskFreeRate = riskFreeRate;
    }

    @Override
    protected void onInit() {
    }

    @Override
    protected void processEvent(DomainEvent event) {
        if (event instanceof OptionChainUpdated updated) {
            processChain(updated);
        }
    }

    private void processChain(OptionChainUpdated event) {
        OptionChainSnapshot chain = event.chain();
        if (chain == null || chain.strikes() == null) {
            return;
        }
        String underlying = chain.underlying() != null ? chain.underlying().symbol() : "UNKNOWN";
        long expiryMs = chain.expiry().atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
        double spot = chain.spotPricePaisa() / 100.0;
        double tte = Math.max(1.0 / 365.0, ChronoUnit.DAYS.between(LocalDate.now(), chain.expiry()) / 365.0);

        for (OptionChainEntry strike : chain.strikes()) {
            computeLeg(event, underlying, expiryMs, spot, tte, strike, OptionType.CALL, strike.call());
            computeLeg(event, underlying, expiryMs, spot, tte, strike, OptionType.PUT, strike.put());
        }
        log.debug("Computed Greeks for {} expiry={} strikes={}", underlying, chain.expiry(), chain.strikes().size());
    }

    private void computeLeg(
            OptionChainUpdated event,
            String underlying,
            long expiryMs,
            double spot,
            double tte,
            OptionChainEntry entry,
            OptionType optionType,
            OptionQuote quote
    ) {
        if (quote == null || quote.ltpPaisa() <= 0) {
            return;
        }
        double strike = entry.strikePricePaisa() / 100.0;
        double marketPrice = quote.ltpPaisa() / 100.0;
        double iv = quote.greeks() != null && quote.greeks().impliedVolatility() != null
                ? quote.greeks().impliedVolatility()
                : IVSolver.solve(marketPrice, spot, strike, tte, riskFreeRate, optionType);
        if (Double.isNaN(iv) || iv <= 0) {
            return;
        }
        OptionGreeks greeks = BlackScholesCalculator.compute(spot, strike, tte, iv, riskFreeRate, optionType);
        OptionsAnalyticsCache.GreeksKey key = new OptionsAnalyticsCache.GreeksKey(
                underlying, expiryMs, entry.strikePricePaisa());
        cache.putGreeks(key, greeks);
        if (quote.instrument() != null) {
            context.publish(new GreeksComputed(event.metadata(), quote.instrument().key(), greeks));
        }
    }
}
