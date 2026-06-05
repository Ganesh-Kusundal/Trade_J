package com.tradej.core.domain.scan;

import java.util.UUID;

public record ScanRun(
        String runId,
        String profileId,
        long startedAtMs,
        long finishedAtMs,
        ScanRunStatus status,
        int universeSize,
        int hitCount,
        int partialFailureCount,
        String errorMessage
) {
    public static ScanRun started(String profileId, int universeSize) {
        return new ScanRun(
                UUID.randomUUID().toString(),
                profileId,
                System.currentTimeMillis(),
                0L,
                ScanRunStatus.RUNNING,
                universeSize,
                0,
                0,
                null
        );
    }

    public ScanRun completed(int hitCount, int partialFailureCount) {
        ScanRunStatus status = partialFailureCount > 0 ? ScanRunStatus.PARTIAL : ScanRunStatus.COMPLETED;
        return new ScanRun(
                runId, profileId, startedAtMs, System.currentTimeMillis(),
                status, universeSize, hitCount, partialFailureCount, null
        );
    }

    public ScanRun failed(String message) {
        return new ScanRun(
                runId, profileId, startedAtMs, System.currentTimeMillis(),
                ScanRunStatus.FAILED, universeSize, 0, partialFailureCount, message
        );
    }
}
