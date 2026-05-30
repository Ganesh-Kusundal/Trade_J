package com.tradej.pipeline.platform;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Unit tests for {@link PipelineVersion} value object.
 * INV-32: Version correctness — every public method preserves semantic versioning rules.
 */
@Tag("unit")
class PipelineVersionUnitTest {

    @Test
    void defaultVersionIsZeroZeroZero() {
        PipelineVersion v = new PipelineVersion(0, 0, 0);

        assertEquals(0, v.major());
        assertEquals(0, v.minor());
        assertEquals(0, v.patch());
    }

    @Test
    void fromIntPacksMajorMinorPatchCorrectly() {
        PipelineVersion v = PipelineVersion.fromInt(2_03_05);

        assertEquals(2, v.major());
        assertEquals(3, v.minor());
        assertEquals(5, v.patch());
    }

    @Test
    void fromIntHandlesSingleDigitComponents() {
        PipelineVersion v = PipelineVersion.fromInt(1_01_01);

        assertEquals(1, v.major());
        assertEquals(1, v.minor());
        assertEquals(1, v.patch());
    }

    @Test
    void nextMajorIncrementsMajorAndResetsMinorAndPatch() {
        PipelineVersion v = new PipelineVersion(1, 2, 3);
        PipelineVersion next = v.nextMajor();

        assertEquals(2, next.major());
        assertEquals(0, next.minor());
        assertEquals(0, next.patch());
    }

    @Test
    void nextMinorIncrementsMinorAndResetsPatch() {
        PipelineVersion v = new PipelineVersion(1, 2, 3);
        PipelineVersion next = v.nextMinor();

        assertEquals(1, next.major());
        assertEquals(3, next.minor());
        assertEquals(0, next.patch());
    }

    @Test
    void nextPatchIncrementsPatchOnly() {
        PipelineVersion v = new PipelineVersion(1, 2, 3);
        PipelineVersion next = v.nextPatch();

        assertEquals(1, next.major());
        assertEquals(2, next.minor());
        assertEquals(4, next.patch());
    }

    @Test
    void nextMethodsReturnNewInstances() {
        PipelineVersion original = new PipelineVersion(1, 0, 0);

        PipelineVersion major = original.nextMajor();
        PipelineVersion minor = original.nextMinor();
        PipelineVersion patch = original.nextPatch();

        assertNotEquals(original, major);
        assertNotEquals(original, minor);
        assertNotEquals(original, patch);
        // original is unchanged
        assertEquals(1, original.major());
        assertEquals(0, original.minor());
        assertEquals(0, original.patch());
    }

    @Test
    void fromIntRoundTripsThroughNextMethods() {
        PipelineVersion v = PipelineVersion.fromInt(3_02_01);
        PipelineVersion bumped = v.nextMajor().nextMinor().nextPatch();

        assertEquals(4, bumped.major());
        assertEquals(1, bumped.minor());
        assertEquals(1, bumped.patch());
    }

    @Test
    void twoVersionsWithSameComponentsAreEqual() {
        PipelineVersion a = new PipelineVersion(1, 2, 3);
        PipelineVersion b = new PipelineVersion(1, 2, 3);

        assertEquals(a, b);
    }

    @Test
    void twoVersionsWithDifferentComponentsAreNotEqual() {
        PipelineVersion a = new PipelineVersion(1, 2, 3);
        PipelineVersion b = new PipelineVersion(1, 2, 4);

        assertNotEquals(a, b);
    }
}
