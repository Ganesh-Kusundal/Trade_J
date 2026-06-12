package com.tradej.scanner.option;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.GammaExposureComputed;
import com.tradej.core.domain.event.MaxPainComputed;
import com.tradej.core.domain.event.ScanResultsPublished;
import com.tradej.core.domain.event.SimpleEventBus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class OptionsScannersTest {

    @Test
    void pcrEmitsBearishOnLargeTotalPain() {
        SimpleEventBus bus = new SimpleEventBus();
        List<ScanResultsPublished> captured = new CopyOnWriteArrayList<>();
        bus.subscribe(ScanResultsPublished.class, captured::add);
        PcrScanner scanner = new PcrScanner(bus);
        scanner.start();
        bus.start();

        bus.publish(new MaxPainComputed(
                EventMetadata.root(), "NIFTY",
                LocalDate.of(2026, 6, 30),
                25_000L, 2_000_000_000L)); // 2B pain → BEARISH

        assertEquals(1, captured.size());
        ScanResultsPublished hit = captured.get(0);
        assertEquals("pcr-cross", hit.profileId());
        assertTrue(hit.hits().get(0).reasons().contains("BEARISH"));
    }

    @Test
    void pcrEmitsBullishOnSmallTotalPain() {
        SimpleEventBus bus = new SimpleEventBus();
        List<ScanResultsPublished> captured = new CopyOnWriteArrayList<>();
        bus.subscribe(ScanResultsPublished.class, captured::add);
        PcrScanner scanner = new PcrScanner(bus);
        scanner.start();
        bus.start();

        bus.publish(new MaxPainComputed(
                EventMetadata.root(), "BANKNIFTY",
                LocalDate.of(2026, 6, 30),
                51_000L, 50_000_000L)); // 50M pain → BULLISH

        assertEquals(1, captured.size());
        assertTrue(captured.get(0).hits().get(0).reasons().contains("BULLISH"));
    }

    @Test
    void pcrSuppressesMidRangeTotalPain() {
        SimpleEventBus bus = new SimpleEventBus();
        List<ScanResultsPublished> captured = new CopyOnWriteArrayList<>();
        bus.subscribe(ScanResultsPublished.class, captured::add);
        PcrScanner scanner = new PcrScanner(bus);
        scanner.start();
        bus.start();

        bus.publish(new MaxPainComputed(
                EventMetadata.root(), "SBIN",
                LocalDate.of(2026, 6, 30),
                75_000L, 500_000_000L)); // 500M pain → neutral

        assertTrue(captured.isEmpty(), "Expected no hit on neutral pain, got " + captured.size());
    }

    @Test
    void maxPainProximityEmitsHitOnEveryGamma() {
        SimpleEventBus bus = new SimpleEventBus();
        List<ScanResultsPublished> captured = new CopyOnWriteArrayList<>();
        bus.subscribe(ScanResultsPublished.class, captured::add);
        MaxPainProximityScanner scanner = new MaxPainProximityScanner(bus);
        scanner.start();
        bus.start();

        bus.publish(new GammaExposureComputed(
                EventMetadata.root(), "NIFTY", 0.42,
                LocalDate.of(2026, 6, 30)
                        .atStartOfDay(java.time.ZoneId.of("Asia/Kolkata"))
                        .toInstant().toEpochMilli()));

        assertEquals(1, captured.size());
        assertEquals("max-pain-proximity", captured.get(0).profileId());
        assertTrue(captured.get(0).hits().get(0).reasons().contains("WATCH"));
    }
}
