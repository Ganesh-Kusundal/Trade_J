package com.tradej.pipeline.platform;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class PipelineVersionTest {

    @Test
    void nextMajorIncrementsMajorAndResetsMinorPatch() {
        PipelineVersion v1 = new PipelineVersion(1, 2, 3);
        PipelineVersion v2 = v1.nextMajor();

        assertEquals(2, v2.major());
        assertEquals(0, v2.minor());
        assertEquals(0, v2.patch());
        assertEquals(new PipelineVersion(1, 2, 3), v1);
    }

    @Test
    void nextMinorIncrementsMinorAndResetsPatch() {
        PipelineVersion v1 = new PipelineVersion(1, 2, 3);
        PipelineVersion v2 = v1.nextMinor();

        assertEquals(1, v2.major());
        assertEquals(3, v2.minor());
        assertEquals(0, v2.patch());
        assertEquals(new PipelineVersion(1, 2, 3), v1);
    }

    @Test
    void nextPatchIncrementsPatchOnly() {
        PipelineVersion v1 = new PipelineVersion(1, 2, 3);
        PipelineVersion v2 = v1.nextPatch();

        assertEquals(1, v2.major());
        assertEquals(2, v2.minor());
        assertEquals(4, v2.patch());
        assertEquals(new PipelineVersion(1, 2, 3), v1);
    }

    @Test
    void fromIntDecodesCorrectly() {
        PipelineVersion v = PipelineVersion.fromInt(2_03_05);

        assertEquals(2, v.major());
        assertEquals(3, v.minor());
        assertEquals(5, v.patch());
    }

    @Test
    void fromIntRoundTrip() {
        PipelineVersion original = new PipelineVersion(3, 7, 12);
        int encoded = original.major() * 10_000 + original.minor() * 100 + original.patch();
        PipelineVersion decoded = PipelineVersion.fromInt(encoded);

        assertEquals(original.major(), decoded.major());
        assertEquals(original.minor(), decoded.minor());
        assertEquals(original.patch(), decoded.patch());
    }

    @Test
    void versionsAreImmutable() {
        PipelineVersion v1 = new PipelineVersion(1, 0, 0);
        PipelineVersion v2 = v1.nextMajor();
        PipelineVersion v3 = v1.nextMinor();
        PipelineVersion v4 = v1.nextPatch();

        assertEquals(new PipelineVersion(1, 0, 0), v1);
        assertEquals(new PipelineVersion(2, 0, 0), v2);
        assertEquals(new PipelineVersion(1, 1, 0), v3);
        assertEquals(new PipelineVersion(1, 0, 1), v4);
    }
}