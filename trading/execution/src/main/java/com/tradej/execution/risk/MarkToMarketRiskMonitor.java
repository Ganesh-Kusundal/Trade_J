package com.tradej.execution.risk;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.UnrealizedPnLUpdated;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.port.NetPositionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Maintains mark-to-market unrealized P&amp;L from ticks and enforces daily loss including unrealized.
 */
public final class MarkToMarketRiskMonitor {

    private static final Logger log = LoggerFactory.getLogger(MarkToMarketRiskMonitor.class);
    private static final long PUBLISH_INTERVAL_MS = 5_000L;

    private final boolean enforceUnrealizedLoss;
    private final NetPositionProvider netPositionProvider;
    private final long maxDailyLossPaisa;
    private volatile EventBus eventBus;
    private final ConcurrentHashMap<String, Long> lastLtpPaisa = new ConcurrentHashMap<>();
    private final AtomicLong lastPublishMs = new AtomicLong();
    private volatile PositionRiskHandler riskHandler;

    public MarkToMarketRiskMonitor(
            boolean enforceUnrealizedLoss,
            NetPositionProvider netPositionProvider,
            long maxDailyLossPaisa
    ) {
        this.enforceUnrealizedLoss = enforceUnrealizedLoss;
        this.netPositionProvider = netPositionProvider;
        this.maxDailyLossPaisa = maxDailyLossPaisa;
    }

    /**
     * Injects the {@link EventBus} after construction to break the circular
     * dependency: eventBus → pipeline → positionRiskHandler → markToMarketRiskMonitor → eventBus.
     * Called from {@code RiskEventBusConfiguration} during {@code @PostConstruct}.
     */
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void registerRiskHandler(PositionRiskHandler handler) {
        this.riskHandler = handler;
    }

    public void onMarketTick(MarketTickEvent tick) {
        if (tick.ltpPaisa() <= 0) {
            return;
        }
        lastLtpPaisa.put(tick.symbol(), tick.ltpPaisa());
        long netQty = netPositionProvider.getNetPosition(tick.symbol());
        if (netQty == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPublishMs.get() < PUBLISH_INTERVAL_MS) {
            return;
        }
        lastPublishMs.set(now);
        publishMtmSnapshot();
    }

    private void publishMtmSnapshot() {
        Map<String, Long> positions = netPositionProvider.getNetPositions();
        Map<String, Long> symbolUnrealized = new HashMap<>();
        long totalUnrealized = 0L;
        for (var entry : positions.entrySet()) {
            long qty = entry.getValue();
            if (qty == 0) {
                continue;
            }
            Long ltp = lastLtpPaisa.get(entry.getKey());
            if (ltp == null || ltp <= 0) {
                continue;
            }
            long unrealized = estimateUnrealizedPaisa(entry.getKey(), qty, ltp);
            symbolUnrealized.put(entry.getKey(), unrealized);
            totalUnrealized += unrealized;
        }
        long unrealizedLoss = totalUnrealized < 0 ? -totalUnrealized : 0L;
        if (riskHandler != null) {
            riskHandler.updateUnrealizedLoss(unrealizedLoss);
            if (enforceUnrealizedLoss) {
                riskHandler.checkCombinedLossLimit(maxDailyLossPaisa);
            }
        }
        if (eventBus != null) {
            long realizedLoss = riskHandler != null ? riskHandler.getRealizedLossPaisa() : 0L;
            eventBus.publish(new UnrealizedPnLUpdated(
                    EventMetadata.root(),
                    totalUnrealized,
                    realizedLoss,
                    realizedLoss + unrealizedLoss,
                    Map.copyOf(symbolUnrealized)));
        }
    }

    private long estimateUnrealizedPaisa(String symbol, long netQty, long ltpPaisa) {
        // Without avg entry in NetPositionProvider, use sign of position * LTP notional as proxy
        // until cost basis is added to position provider.
        return netQty * ltpPaisa;
    }
}
