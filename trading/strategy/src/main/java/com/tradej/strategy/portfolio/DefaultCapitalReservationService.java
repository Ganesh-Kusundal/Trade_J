package com.tradej.strategy.portfolio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tradej.core.domain.event.SignalGenerated;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultCapitalReservationService implements CapitalReservationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCapitalReservationService.class);

    private final ConcurrentHashMap<String, PortfolioEngine.StrategyAllocation> allocations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> signalToStrategy = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> signalToEstimatedCapital = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> orderIdToSignalId = new ConcurrentHashMap<>();

    private final long defaultCapitalPaisa;

    public DefaultCapitalReservationService(long defaultCapitalPaisa) {
        this.defaultCapitalPaisa = defaultCapitalPaisa;
    }

    @Override
    public String reserveSignal(SignalGenerated signal) {
        String strategyName = extractStrategyName(signal);
        long requiredCapital = requiredCapital(signal);

        boolean[] approved = {false};
        allocations.compute(strategyName, (name, alloc) -> {
            PortfolioEngine.StrategyAllocation a = alloc != null ? alloc
                    : new PortfolioEngine.StrategyAllocation(defaultCapitalPaisa, 0L);
            long used = a.usedCapitalPaisa();
            if (used + requiredCapital > a.allocatedCapitalPaisa()) {
                return a;
            }
            approved[0] = true;
            return new PortfolioEngine.StrategyAllocation(a.allocatedCapitalPaisa(), used + requiredCapital);
        });

        if (!approved[0]) {
            PortfolioEngine.StrategyAllocation current = allocations.get(strategyName);
            long remaining = current != null
                    ? current.allocatedCapitalPaisa() - current.usedCapitalPaisa()
                    : defaultCapitalPaisa;
            log.warn("Strategy {} capital limit exceeded required={} remaining={} symbol={}",
                    strategyName, requiredCapital, remaining, signal.symbol());
            return "Strategy capital limit exceeded: " + requiredCapital
                    + " paisa required, " + Math.max(0, remaining) + " paisa remaining";
        }

        signalToStrategy.put(signal.signalId(), strategyName);
        signalToEstimatedCapital.put(signal.signalId(), requiredCapital);

        return null;
    }

    @Override
    public String freeSignalCapital(String signalId, String symbol) {
        String strategyName = signalToStrategy.remove(signalId);
        Long estimatedCapital = signalToEstimatedCapital.remove(signalId);

        if (strategyName != null && estimatedCapital != null && estimatedCapital > 0L) {
            allocations.compute(strategyName, (name, alloc) -> {
                if (alloc == null) {
                    return alloc;
                }
                long freed = Math.max(0L, alloc.usedCapitalPaisa() - estimatedCapital);
                return new PortfolioEngine.StrategyAllocation(alloc.allocatedCapitalPaisa(), freed);
            });
        }
        return strategyName;
    }

    public void revertReservation(String signalId) {
        String strategyName = signalToStrategy.remove(signalId);
        Long estimatedCapital = signalToEstimatedCapital.remove(signalId);
        if (strategyName != null && estimatedCapital != null && estimatedCapital > 0L) {
            allocations.compute(strategyName, (name, alloc) -> {
                if (alloc == null) return alloc;
                long freed = Math.max(0, alloc.usedCapitalPaisa() - estimatedCapital);
                return new PortfolioEngine.StrategyAllocation(alloc.allocatedCapitalPaisa(), freed);
            });
        }
    }

    public void adjustOnTradeOpened(String strategyName, long estimatedCapital, long actualCapital) {
        allocations.compute(strategyName, (name, alloc) -> {
            if (alloc == null) {
                return new PortfolioEngine.StrategyAllocation(defaultCapitalPaisa, actualCapital);
            }
            long adjusted = alloc.usedCapitalPaisa() - estimatedCapital + actualCapital;
            return new PortfolioEngine.StrategyAllocation(alloc.allocatedCapitalPaisa(), Math.max(0L, adjusted));
        });
    }

    public void freeTradeCapital(String strategyName, long capitalPaisa) {
        allocations.compute(strategyName, (name, alloc) -> {
            if (alloc == null) {
                return new PortfolioEngine.StrategyAllocation(defaultCapitalPaisa, 0L);
            }
            long freed = Math.max(0L, alloc.usedCapitalPaisa() - capitalPaisa);
            return new PortfolioEngine.StrategyAllocation(alloc.allocatedCapitalPaisa(), freed);
        });
    }

    public void trackOrderId(String orderId, String signalId) {
        if (signalId != null && !signalId.isBlank() && orderId != null && !orderId.isBlank()) {
            orderIdToSignalId.put(orderId, signalId);
        }
    }

    public String signalIdForOrder(String orderId) {
        return orderIdToSignalId.get(orderId);
    }

    @Override
    public long usedCapitalPaisa(String strategyName) {
        PortfolioEngine.StrategyAllocation alloc = allocations.get(strategyName);
        return alloc != null ? alloc.usedCapitalPaisa() : 0L;
    }

    @Override
    public long allocatedCapitalPaisa(String strategyName) {
        PortfolioEngine.StrategyAllocation alloc = allocations.get(strategyName);
        return alloc != null ? alloc.allocatedCapitalPaisa() : defaultCapitalPaisa;
    }

    @Override
    public Map<String, PortfolioEngine.StrategyAllocation> allocationsSnapshot() {
        return Collections.unmodifiableMap(new ConcurrentHashMap<>(allocations));
    }

    @Override
    public void reset() {
        allocations.clear();
        signalToStrategy.clear();
        signalToEstimatedCapital.clear();
        orderIdToSignalId.clear();
    }

    // ── Package-private access for snapshot/restore ──

    ConcurrentHashMap<String, PortfolioEngine.StrategyAllocation> allocations() {
        return allocations;
    }

    ConcurrentHashMap<String, String> signalToStrategy() {
        return signalToStrategy;
    }

    ConcurrentHashMap<String, Long> signalToEstimatedCapital() {
        return signalToEstimatedCapital;
    }

    ConcurrentHashMap<String, String> orderIdToSignalId() {
        return orderIdToSignalId;
    }

    long defaultCapitalPaisa() {
        return defaultCapitalPaisa;
    }

    // ── Helpers ──

    private static String extractStrategyName(SignalGenerated signal) {
        Object name = signal.attributes().get(ATTR_STRATEGY_NAME);
        if (name instanceof String s && !s.isBlank()) {
            return s;
        }
        return "unknown";
    }

    private static long extractQuantity(SignalGenerated signal) {
        Object qty = signal.attributes().get(ATTR_QUANTITY);
        if (qty instanceof Number n) {
            return Math.max(0L, n.longValue());
        }
        return 0L;
    }

    private static long requiredCapital(SignalGenerated signal) {
        return extractQuantity(signal) * signal.entryPricePaisa();
    }
}
