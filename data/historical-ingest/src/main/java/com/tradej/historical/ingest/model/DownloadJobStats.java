package com.tradej.historical.ingest.model;

public record DownloadJobStats(
        long totalTasks,
        long pendingTasks,
        long runningTasks,
        long completedTasks,
        long failedTasks,
        long rowsWritten
) {
    public static DownloadJobStats empty() {
        return new DownloadJobStats(0L, 0L, 0L, 0L, 0L, 0L);
    }
}
