package com.tradej.historical.ingest.model;

import java.time.LocalDate;

public record DownloadTaskRecord(
        String taskId,
        String jobId,
        String fingerprint,
        String underlying,
        String expiryKind,
        int expiryCode,
        int strikeOffset,
        String optionType,
        int intervalMin,
        LocalDate chunkFrom,
        LocalDate chunkTo,
        DownloadTaskStatus status,
        long rowsWritten,
        String error,
        Long completedAtMs
) {
}
