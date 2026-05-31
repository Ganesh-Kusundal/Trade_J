package com.tradej.historical.ingest.model;

public record DownloadJobRecord(
        String jobId,
        DownloadSourceType sourceType,
        DownloadJobStatus status,
        String configJson,
        long createdAtMs,
        Long startedAtMs,
        Long finishedAtMs,
        String statsJson
) {
}
