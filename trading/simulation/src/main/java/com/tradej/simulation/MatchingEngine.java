package com.tradej.simulation;

import com.tradej.core.domain.instrument.ExchangeTickSizeRegistry;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.Side;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple in-process matching engine for simulated (REPLAY/BACKTEST) execution.
 * Maintains a last-traded-price map per symbol and resolves fill prices
 * from limit price or last traded price with optional slippage modeling.
 *
 * <p>Slippage model features:
 * <ul>
 *   <li><b>Spread cost</b>: Half-spread applied to aggressive (taker) orders</li>
 *   <li><b>Volatility-adjusted slippage</b>: Configurable bps that scales with recent price variance</li>
 *   <li><b>Partial fills</b>: Optional partial fill simulation for large orders</li>
 * </ul>
 */
public final class MatchingEngine {

    private final Map<String, Long> lastPricePaisaBySymbol = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<Long>> lastPriceVarianceBySymbol = new ConcurrentHashMap<>();
    private final SlippageConfig slippageConfig;

    public MatchingEngine() {
        this(SlippageConfig.DEFAULT);
    }

    public MatchingEngine(SlippageConfig slippageConfig) {
        this.slippageConfig = Objects.requireNonNullElse(slippageConfig, SlippageConfig.DEFAULT);
    }

    /**
     * Configuration for slippage modeling in simulated fills.
     *
     * @param spreadBps          half-spread in basis points (50 = 0.50%). Applied to aggressive orders.
     * @param volatilitySlippageBps additional slippage bps scaled by recent price volatility (0 = disabled)
     * @param partialFillEnabled if true, simulate partial fills for orders exceeding minFillSize
     * @param partialFillRatio   fraction of quantity to fill when partial fill triggers (0.3–1.0)
     * @param minFillSize        minimum order size to trigger partial fill simulation
     * @param maxSlippageBps     hard cap on total slippage in bps to prevent runaway slippage on illiquid names
     */
    public record SlippageConfig(
            long spreadBps,
            long volatilitySlippageBps,
            boolean partialFillEnabled,
            double partialFillRatio,
            int minFillSize,
            long maxSlippageBps
    ) {
        public static final SlippageConfig DEFAULT = new SlippageConfig(0L, 0L, false, 1.0, 0, 0L);

        public static final SlippageConfig CONSERVATIVE = new SlippageConfig(10L, 5L, true, 0.7, 100, 50L);

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private long spreadBps = 0L;
            private long volatilitySlippageBps = 0L;
            private boolean partialFillEnabled = false;
            private double partialFillRatio = 1.0;
            private int minFillSize = 0;
            private long maxSlippageBps = 0L;

            public Builder spreadBps(long bps) { this.spreadBps = bps; return this; }
            public Builder volatilitySlippageBps(long bps) { this.volatilitySlippageBps = bps; return this; }
            public Builder partialFillEnabled(boolean enabled) { this.partialFillEnabled = enabled; return this; }
            public Builder partialFillRatio(double ratio) { this.partialFillRatio = ratio; return this; }
            public Builder minFillSize(int size) { this.minFillSize = size; return this; }
            public Builder maxSlippageBps(long bps) { this.maxSlippageBps = bps; return this; }

            public SlippageConfig build() {
                return new SlippageConfig(spreadBps, volatilitySlippageBps, partialFillEnabled,
                        partialFillRatio, minFillSize, maxSlippageBps);
            }
        }
    }

    /**
     * Record the last traded price for a symbol (called from market data pipeline).
     * Also updates the exponential weighted variance estimate for volatility-based slippage.
     */
    public void onTick(String symbol, long ltpPaisa) {
        if (ltpPaisa <= 0L) {
            return;
        }
        lastPricePaisaBySymbol.put(symbol, ltpPaisa);
        updateVarianceEstimate(symbol, ltpPaisa);
    }

    private void updateVarianceEstimate(String symbol, long ltpPaisa) {
        AtomicReference<Long> varRef = lastPriceVarianceBySymbol.computeIfAbsent(
                symbol, k -> new AtomicReference<>(0L));
        long prevLtp = lastPricePaisaBySymbol.get(symbol);
        if (prevLtp > 0 && ltpPaisa > 0) {
            long priceDelta = Math.abs(ltpPaisa - prevLtp);
            long prevVar = varRef.get();
            long newVar = (prevVar * 7 + priceDelta) / 8;
            varRef.set(newVar);
        }
    }

    /**
     * Attempt to match an order against the simulated order book.
     *
     * @param request   the order request to match
     * @param orderId   the generated order ID
     * @return the match result containing the order, fills, reject status, and reason
     */
    public MatchResult match(OrderRequest request, String orderId) {
        long fillPrice = resolveFillPrice(request);
        if (fillPrice <= 0L) {
            return MatchResult.rejected(orderId, request, "No fill price available for " + request.symbol());
        }

        long baseQty = request.quantity();
        long fillQty = baseQty;

        if (slippageConfig.partialFillEnabled() && baseQty >= slippageConfig.minFillSize()) {
            fillQty = (long) (baseQty * slippageConfig.partialFillRatio());
            fillQty = Math.max(1, fillQty);
        }

        Order order = new Order(
                orderId,
                request.correlationId(),
                request.symbol(),
                request.exchangeSegment(),
                request.side(),
                request.productType(),
                request.orderType(),
                OrderStatus.TRADED,
                baseQty,
                fillQty,
                fillPrice,
                request.triggerPricePaisa(),
                System.currentTimeMillis(),
                null
        );

        List<Trade> fills = new ArrayList<>();
        fills.add(new Trade(
                "FILL-" + UUID.randomUUID(),
                orderId,
                request.symbol(),
                request.exchangeSegment(),
                request.side(),
                fillQty,
                fillPrice,
                System.currentTimeMillis()
        ));

        boolean partialFill = slippageConfig.partialFillEnabled() && fillQty < baseQty;
        return new MatchResult(order, fills, false, null, partialFill ? "partial fill: " + fillQty + "/" + baseQty : null);
    }

    private long resolveFillPrice(OrderRequest request) {
        long basePrice;
        if (request.orderType() == OrderType.LIMIT && request.pricePaisa() > 0L) {
            basePrice = request.pricePaisa();
        } else {
            Long ltp = lastPricePaisaBySymbol.get(request.symbol());
            if (ltp == null || ltp <= 0L) {
                return request.pricePaisa();
            }
            basePrice = ltp;
        }

        long slippagePaisa = computeSlippage(request.symbol(), basePrice, request.quantity());
        long rawFillPrice;

        if (request.side() == Side.BUY || request.side() == Side.SHORT) {
            rawFillPrice = basePrice + slippagePaisa;
        } else {
            rawFillPrice = basePrice - slippagePaisa;
        }

        // Apply exchange-specific tick-rounding using ExchangeTickSizeRegistry
        long tickSizePaisa = ExchangeTickSizeRegistry.tickSizePaisa(request.exchangeSegment());
        long remainder = rawFillPrice % tickSizePaisa;
        if (remainder != 0) {
            if (request.side() == Side.BUY || request.side() == Side.SHORT) {
                return rawFillPrice + (tickSizePaisa - remainder); // Round up for buy slippage
            } else {
                return rawFillPrice - remainder; // Round down for sell slippage
            }
        }
        return rawFillPrice;
    }

    private long computeSlippage(String symbol, long basePricePaisa, long quantity) {
        if (basePricePaisa <= 0) {
            return 0L;
        }
        long totalBps = 0L;

        if (slippageConfig.spreadBps() > 0) {
            totalBps += slippageConfig.spreadBps();
        }

        if (slippageConfig.volatilitySlippageBps() > 0) {
            AtomicReference<Long> varRef = lastPriceVarianceBySymbol.get(symbol);
            long variancePaisa = varRef != null ? varRef.get() : 0L;
            long volSlippageBps = (variancePaisa * 10000) / Math.max(1, basePricePaisa);
            totalBps += Math.min(volSlippageBps, slippageConfig.volatilitySlippageBps());
        }

        // Volume-aware scaling: 1 basis point of additional slippage for every 50 lots
        long volumeSlippageBps = (quantity / 50);
        totalBps += volumeSlippageBps;

        if (slippageConfig.maxSlippageBps() > 0) {
            totalBps = Math.min(totalBps, slippageConfig.maxSlippageBps());
        }

        return (basePricePaisa * totalBps) / 10_000L;
    }

    /**
     * Result of a simulated match operation.
     * @param order           the matched order
     * @param fills            list of fills (may be partial)
     * @param rejected         true if the order was rejected
     * @param reason           rejection reason if rejected
     * @param partialFillInfo  human-readable partial fill description if applicable
     */
    public record MatchResult(
            Order order,
            List<Trade> fills,
            boolean rejected,
            String reason,
            String partialFillInfo
    ) {
        static MatchResult rejected(String orderId, OrderRequest request, String reason) {
            Order rejectedOrder = new Order(
                    orderId,
                    request.correlationId(),
                    request.symbol(),
                    request.exchangeSegment(),
                    request.side(),
                    request.productType(),
                    request.orderType(),
                    OrderStatus.REJECTED,
                    request.quantity(),
                    0L,
                    request.pricePaisa(),
                    request.triggerPricePaisa(),
                    System.currentTimeMillis(),
                    reason
            );
            return new MatchResult(rejectedOrder, List.of(), true, reason, null);
        }
    }
}
