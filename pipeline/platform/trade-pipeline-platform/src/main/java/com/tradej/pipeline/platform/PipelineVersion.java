package com.tradej.pipeline.platform;

/**
 * Semantic version for a pipeline definition (major.minor.patch).
 * Immutable value object — all increment methods return new instances.
 * <p>
 * INV-32: Version correctness — increment methods follow semantic versioning rules.
 */
public record PipelineVersion(int major, int minor, int patch) {

    /**
     * Factory method that packs a single integer into major.minor.patch components.
     * <p>
     * Encoding: {@code major * 10_000 + minor * 100 + patch}
     * <p>
     * Example: {@code fromInt(2_03_05)} yields major=2, minor=3, patch=5.
     */
    public static PipelineVersion fromInt(int version) {
        int major = version / 10_000;
        int minor = (version % 10_000) / 100;
        int patch = version % 100;
        return new PipelineVersion(major, minor, patch);
    }

    /**
     * Returns a new version with major incremented, minor and patch reset to 0.
     */
    public PipelineVersion nextMajor() {
        return new PipelineVersion(major + 1, 0, 0);
    }

    /**
     * Returns a new version with minor incremented and patch reset to 0.
     */
    public PipelineVersion nextMinor() {
        return new PipelineVersion(major, minor + 1, 0);
    }

    /**
     * Returns a new version with patch incremented.
     */
    public PipelineVersion nextPatch() {
        return new PipelineVersion(major, minor, patch + 1);
    }
}
