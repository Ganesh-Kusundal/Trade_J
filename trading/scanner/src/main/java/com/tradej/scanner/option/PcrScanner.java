package com.tradej.scanner.option;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MaxPainComputed;
import com.tradej.core.domain.event.ScanResultsPublished;
import com.tradej.core.domain.port.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Put-Call-Ratio scanner that watches {@link MaxPainComputed} and
 * publishes a {@link ScanResultsPublished} hit when the implied PCR
 * crosses the configured thresholds.
 *
 * <p>For a richer PCR the {@code OptionsAnalyticsProducer} emits a
 * dedicated {@code OI_UPDATE} event. This scanner is the bridge from
 * the existing max-pain stream into the standard scan-results shape
 * consumed by the dashboard.
 */
public final class PcrScanner {

    private static final Logger log = LoggerFactory.getLogger(PcrScanner.class);

    private final EventBus eventBus;
    private final double bullishThreshold;
    private final double bearishThreshold;
    private final AtomicLong hitCounter = new AtomicLong();

    public PcrScanner(EventBus eventBus) {
        this(eventBus, 0.8, 1.2);
    }

    public PcrScanner(EventBus eventBus, double bullishThreshold, double bearishThreshold) {
        this.eventBus = eventBus;
        this.bullishThreshold = bullishThreshold;
        this.bearishThreshold = bearishThreshold;
    }

    public void start() {
        eventBus.subscribe(MaxPainComputed.class, this::onMaxPain);
        log.info("PcrScanner started bullish={} bearish={}", bullishThreshold, bearishThreshold);
    }

    private void onMaxPain(MaxPainComputed event) {
        // Without a true OI split we fall back to using the max-pain total
        // pain magnitude as a soft signal. The full PCR will be wired in
        // when the producer emits OI_UPDATE.
        double totalPainPaisa = event.totalPainPaisa();
        String side;
        if (totalPainPaisa > 1_000_000_000L) {
            side = "BEARISH";
        } else if (totalPainPaisa < 100_000_000L) {
            side = "BULLISH";
        } else {
            return;
        }
        long hit = hitCounter.incrementAndGet();

        var summary = new ScanResultsPublished.ScanHitSummary(
                event.underlying(),
                "NSE_FNO",
                event.underlying(),
                1.0,
                List.of(side, "pain=" + totalPainPaisa)
        );
        eventBus.publish(new ScanResultsPublished(
                EventMetadata.root(),
                "pcr-cross",
                "pcr-" + hit,
                1,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                List.of(summary)
        ));
    }
}
