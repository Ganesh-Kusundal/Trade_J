package com.tradej.execution.risk;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;
import com.tradej.core.domain.model.OrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Pre-trade margin gate: compares broker margin estimate against available balance.
 * Disabled by default ({@code enforceMargin=false}) for safe rollout.
 */
public final class MarginEnforcementHandler {

    private static final Logger log = LoggerFactory.getLogger(MarginEnforcementHandler.class);

    private final boolean enforceMargin;
    private final MarginProvider marginProvider;
    private final PortfolioProvider portfolioProvider;
    private final Cache<String, Long> estimateCache;

    public MarginEnforcementHandler(
            boolean enforceMargin,
            MarginProvider marginProvider,
            PortfolioProvider portfolioProvider,
            Duration cacheTtl
    ) {
        this.enforceMargin = enforceMargin;
        this.marginProvider = marginProvider;
        this.portfolioProvider = portfolioProvider;
        this.estimateCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Objects.requireNonNullElse(cacheTtl, Duration.ofMinutes(5)))
                .build();
    }

    /**
     * @return rejection reason if margin check fails; empty if pass or skipped
     */
    public Optional<String> checkMargin(OrderRequest order) {
        if (!enforceMargin) {
            return Optional.empty();
        }
        if (marginProvider == null || portfolioProvider == null) {
            log.debug("Margin enforcement enabled but broker margin/portfolio ports unavailable — skipping");
            return Optional.empty();
        }
        try {
            long required = cachedMarginEstimate(order);
            long available = availableMarginPaisa();
            if (required > available) {
                log.warn(
                        "Insufficient margin symbol={} requiredPaisa={} availablePaisa={}",
                        order.symbol(),
                        required,
                        available);
                return Optional.of("insufficient_margin");
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Margin check failed for symbol={} — rejecting order: {}", order.symbol(), e.getMessage());
            return Optional.of("margin_check_failed");
        }
    }

    private long cachedMarginEstimate(OrderRequest order) {
        String key = order.symbol()
                + "|" + order.exchangeSegment()
                + "|" + order.side()
                + "|" + order.quantity()
                + "|" + order.pricePaisa()
                + "|" + order.productType()
                + "|" + order.orderType();
        Long cached = estimateCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        MarginEstimateRequest request = new MarginEstimateRequest(
                order.symbol(),
                order.exchangeSegment(),
                order.side(),
                order.quantity(),
                order.productType(),
                order.orderType(),
                order.pricePaisa(),
                order.triggerPricePaisa());
        MarginEstimate estimate = marginProvider.estimateMargin(request);
        long total = Math.max(0L, estimate.totalMarginPaisa());
        estimateCache.put(key, total);
        return total;
    }

    private long availableMarginPaisa() {
        var balance = portfolioProvider.getBalance();
        if (balance == null) {
            return 0L;
        }
        long withdrawable = balance.withdrawablePaisa();
        if (withdrawable > 0) {
            return withdrawable;
        }
        long cash = balance.cashPaisa();
        long utilized = balance.utilizedPaisa();
        return Math.max(0L, cash + balance.collateralPaisa() - utilized);
    }

    public boolean isEnforceMargin() {
        return enforceMargin;
    }
}
