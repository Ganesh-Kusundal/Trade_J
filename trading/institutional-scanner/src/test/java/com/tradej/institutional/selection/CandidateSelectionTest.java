package com.tradej.institutional.selection;

import com.tradej.institutional.features.FeaturePipeline;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CandidateSelectionTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    void usesExactCutoffWhenBarExists() {
        LocalDate date = LocalDate.of(2026, 5, 22);
        Instant cutoff = date.atTime(9, 45).atZone(IST).toInstant();
        Instant other = date.atTime(10, 0).atZone(IST).toInstant();

        List<FeaturePipeline.BarFeatures> bars = List.of(
                feature("AAA", cutoff, 1.0),
                feature("BBB", other, 0.5)
        );

        CandidateSelection selection = new CandidateSelection(5, Map.of(), 2);
        CandidateSelection.SelectionResult result = selection.selectCandidates(bars, date, "09:45:00");

        assertEquals("09:45:00", result.effectiveScanTime());
        assertFalse(result.candidates().isEmpty());
        assertEquals("AAA", result.candidates().getFirst().symbol());
    }

    @Test
    void usesFirstBarAfterCutoffWhenMorningBarsMissing() {
        LocalDate date = LocalDate.of(2026, 5, 29);
        Instant cutoff = date.atTime(9, 45).atZone(IST).toInstant();
        Instant firstAfter = date.atTime(14, 45).atZone(IST).toInstant();

        List<FeaturePipeline.BarFeatures> bars = List.of(
                feature("AAA", firstAfter, 1.0),
                feature("BBB", firstAfter, 0.8)
        );

        CandidateSelection selection = new CandidateSelection(5, Map.of(), 2);
        CandidateSelection.SelectionResult result = selection.selectCandidates(bars, date, "09:45:00");

        assertEquals("14:45:00", result.effectiveScanTime());
        assertEquals("used_first_bar_after_cutoff", result.provenance().get("fallbackReason"));
        assertEquals(2, result.candidates().size());
    }

    private static FeaturePipeline.BarFeatures feature(String symbol, Instant barTime, double masterScore) {
        return new FeaturePipeline.BarFeatures(
                symbol,
                barTime,
                1000L,
                1100L,
                900L,
                950L,
                100L,
                0.1,
                0.2,
                0.3,
                0.4,
                0.0,
                0.0,
                0.0,
                0.0,
                masterScore
        );
    }
}
