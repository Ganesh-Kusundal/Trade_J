package com.tradej.indicators;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tick-level Cumulative Volume Delta (CVD).
 * Classifies trades as buy (uptick) or sell (downtick) based on
 * price movement relative to the previous tick.
 *
 * <p>Usage:
 * <pre>{@code
 * TickLevelCVD cvd = new TickLevelCVD();
 *
 * // On each tick:
 * cvd.onTick(250000, 100);  // price=2500.00, volume=100
 * cvd.onTick(250100, 50);   // price=2501.00, volume=50 (uptick → buy)
 * cvd.onTick(249900, 200);  // price=2499.00, volume=200 (downtick → sell)
 *
 * long buyVol = cvd.buyVolume();
 * long sellVol = cvd.sellVolume();
 * long delta = cvd.delta();         // buyVol - sellVol
 * long cvdValue = cvd.cvd();        // cumulative delta
 * }</pre>
 */
public final class TickLevelCVD {

    private final AtomicLong buyVolume = new AtomicLong();
    private final AtomicLong sellVolume = new AtomicLong();
    private final AtomicLong cumulativeDelta = new AtomicLong();
    private final AtomicReference<Long> lastPrice = new AtomicReference<>(null);

    /**
     * Process a tick. Classifies as buy (uptick) or sell (downtick)
     * based on price movement.
     *
     * @param pricePaisa current price in paisa
     * @param volume trade volume
     */
    public void onTick(long pricePaisa, long volume) {
        Long prev = lastPrice.getAndSet(pricePaisa);

        if (prev == null) {
            // First tick — classify as buy (convention)
            buyVolume.addAndGet(volume);
            cumulativeDelta.addAndGet(volume);
            return;
        }

        if (pricePaisa > prev) {
            // Uptick → buy
            buyVolume.addAndGet(volume);
            cumulativeDelta.addAndGet(volume);
        } else if (pricePaisa < prev) {
            // Downtick → sell
            sellVolume.addAndGet(volume);
            cumulativeDelta.addAndGet(-volume);
        } else {
            // Unchanged → classify as buy (convention)
            buyVolume.addAndGet(volume);
            cumulativeDelta.addAndGet(volume);
        }
    }

    /**
     * Total buy volume.
     */
    public long buyVolume() {
        return buyVolume.get();
    }

    /**
     * Total sell volume.
     */
    public long sellVolume() {
        return sellVolume.get();
    }

    /**
     * Current delta (buy - sell for the last tick).
     */
    public long delta() {
        Long price = lastPrice.get();
        if (price == null) return 0;
        // Delta is the last contribution
        return cumulativeDelta.get();
    }

    /**
     * Cumulative Volume Delta (running total of buy - sell).
     */
    public long cvd() {
        return cumulativeDelta.get();
    }

    /**
     * Reset all counters.
     */
    public void reset() {
        buyVolume.set(0);
        sellVolume.set(0);
        cumulativeDelta.set(0);
        lastPrice.set(null);
    }

    /**
     * Snapshot of current CVD state.
     */
    public CvdSnapshot snapshot() {
        return new CvdSnapshot(buyVolume(), sellVolume(), cvd());
    }

    public record CvdSnapshot(long buyVolume, long sellVolume, long cvd) {}
}
