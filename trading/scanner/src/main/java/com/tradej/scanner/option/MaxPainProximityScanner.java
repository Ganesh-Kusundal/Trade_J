package com.tradej.scanner.option;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.GammaExposureComputed;
import com.tradej.core.domain.event.ScanResultsPublished;
import com.tradej.core.domain.port.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gamma-exposure watcher. Emits a {@link ScanResultsPublished} hit
 * for every {@link GammaExposureComputed} event the producer pushes
 * onto the bus. The dashboard uses the hits to drive the
 * {@code WATCH} alert column.
 */
public final class MaxPainProximityScanner {

    private static final Logger log = LoggerFactory.getLogger(MaxPainProximityScanner.class);

    private final EventBus eventBus;
    private final AtomicLong hitCounter = new AtomicLong();

    public MaxPainProximityScanner(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void start() {
        eventBus.subscribe(GammaExposureComputed.class, this::onGamma);
        log.info("MaxPainProximityScanner started");
    }

    private void onGamma(GammaExposureComputed event) {
        long hit = hitCounter.incrementAndGet();
        var summary = new ScanResultsPublished.ScanHitSummary(
                event.underlying(),
                "NSE_FNO",
                event.underlying(),
                Math.abs(event.netGamma()),
                List.of("WATCH", "gamma=" + event.netGamma())
        );
        eventBus.publish(new ScanResultsPublished(
                EventMetadata.root(),
                "max-pain-proximity",
                "mpp-" + hit,
                1,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                List.of(summary)
        ));
    }
}
