package com.tradej.scanner.spi;

import com.tradej.scanner.criterion.CriterionGroup;
import com.tradej.scanner.criterion.MaxOiStrikeCriterion;
import com.tradej.scanner.criterion.PcrRangeCriterion;
import com.tradej.scanner.criterion.PctChangeFromOpenCriterion;
import com.tradej.scanner.criterion.PctChangeFromPrevCloseCriterion;
import com.tradej.scanner.criterion.ScanCriterion;
import com.tradej.scanner.criterion.VolumeSpikeCriterion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class ScanCriterionRegistryTest {

    @Test
    void discoverAll() {
        int providerCount = 0;
        for (ScanCriterionProvider p : ServiceLoader.load(ScanCriterionProvider.class)) {
            providerCount++;
            assertTrue(p.type() != null && !p.type().isEmpty(),
                    "Provider " + p.getClass().getName() + " has blank type()");
        }
        // 5 leaf providers + group-and
        assertEquals(6, providerCount,
                "Expected 6 ScanCriterionProvider implementations on classpath");
    }

    @Test
    void createByType() {
        ScanCriterionRegistry registry = new ScanCriterionRegistry();

        ScanCriterion vol = registry.create("volume-spike",
                Map.of("multiplier", 3.0, "min-volume", 1000L));
        assertInstanceOf(VolumeSpikeCriterion.class, vol);
        assertEquals("volume-spike", vol.type());

        ScanCriterion open = registry.create("pct-change-from-open",
                Map.of("min", 1.5, "max", 5.0));
        assertInstanceOf(PctChangeFromOpenCriterion.class, open);
        assertEquals("pct-change-from-open", open.type());

        ScanCriterion prev = registry.create("pct-change-from-prev-close",
                Map.of("min", 0.5));
        assertInstanceOf(PctChangeFromPrevCloseCriterion.class, prev);
        assertEquals("pct-change-from-prev-close", prev.type());

        ScanCriterion oi = registry.create("max-oi-strike",
                Map.of("min-total-oi", 50000L));
        assertInstanceOf(MaxOiStrikeCriterion.class, oi);
        assertEquals("max-oi-strike", oi.type());

        ScanCriterion pcr = registry.create("pcr-range",
                Map.of("min", 0.5, "max", 2.0));
        assertInstanceOf(PcrRangeCriterion.class, pcr);
        assertEquals("pcr-range", pcr.type());

        ScanCriterion group = registry.create("group-and",
                Map.of("criteria", java.util.List.of(
                        Map.of("type", "volume-spike", "multiplier", 2.0, "min-volume", 0L))));
        assertInstanceOf(CriterionGroup.class, group);
    }

    @Test
    void unknownTypeThrows() {
        ScanCriterionRegistry registry = new ScanCriterionRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> registry.create("nope", Map.of()));
    }

    @Test
    void hasReturnsFalseForUnknownType() {
        ScanCriterionRegistry registry = new ScanCriterionRegistry();
        assertFalse(registry.has("nope"));
        assertTrue(registry.has("volume-spike"));
    }
}
