package com.tradej.strategy.portfolio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.value.Side;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultExposureTracker implements ExposureTracker {

    private static final Logger log = LoggerFactory.getLogger(DefaultExposureTracker.class);

    private final ConcurrentHashMap<String, Long> netPositions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> signalDeltas = new ConcurrentHashMap<>();

    private final long maxNetExposurePaisa;

    public DefaultExposureTracker(long maxNetExposurePaisa) {
        this.maxNetExposurePaisa = maxNetExposurePaisa;
    }

    public String checkAndReserve(String symbol, long signalDelta, long pricePaisa) {
        boolean[] approved = {false};
        netPositions.compute(symbol, (sym, current) -> {
            long cur = current != null ? current : 0L;
            long newNet = cur + signalDelta;
            long newExposurePaisa = Math.abs(newNet) * pricePaisa;
            if (newExposurePaisa > maxNetExposurePaisa) {
                return current;
            }
            approved[0] = true;
            return newNet;
        });

        if (!approved[0]) {
            long curNet = netPositions.getOrDefault(symbol, 0L);
            log.warn("Symbol {} net exposure limit exceeded currentNet={}", symbol, curNet);
            return "Symbol net exposure limit exceeded: " + symbol;
        }
        return null;
    }

    @Override
    public void applySignalDelta(String symbol, long delta) {
        netPositions.merge(symbol, delta, Long::sum);
    }

    @Override
    public void reverseSignalDelta(String symbol, long delta) {
        netPositions.merge(symbol, -delta, Long::sum);
    }

    public void storeSignalDelta(String signalId, long delta) {
        signalDeltas.put(signalId, delta);
    }

    public Long removeSignalDelta(String signalId) {
        return signalDeltas.remove(signalId);
    }

    @Override
    public void onTradeOpened(TradeOpened trade, long actualDelta, long signalDelta) {
        netPositions.merge(trade.symbol(), -signalDelta + actualDelta, Long::sum);
    }

    @Override
    public void onTradeClosed(TradeClosed trade, String symbol, long netDelta) {
        netPositions.merge(symbol, -netDelta, Long::sum);
    }

    @Override
    public long netPosition(String symbol) {
        return netPositions.getOrDefault(symbol, 0L);
    }

    @Override
    public Map<String, Long> netPositionsSnapshot() {
        return Collections.unmodifiableMap(new ConcurrentHashMap<>(netPositions));
    }

    @Override
    public void reset() {
        netPositions.clear();
        signalDeltas.clear();
    }

    ConcurrentHashMap<String, Long> netPositions() {
        return netPositions;
    }

    ConcurrentHashMap<String, Long> signalDeltas() {
        return signalDeltas;
    }

    long maxNetExposurePaisa() {
        return maxNetExposurePaisa;
    }
}
