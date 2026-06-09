package com.tradej.core.domain.port;

/**
 * Port for historical data import operations.
 * Implementations handle broker-specific import logic.
 */
public interface HistoricalImportPort {

    /**
     * Starts a historical data import job.
     *
     * @param request the import request parameters
     * @return a job ID that can be used to query status
     */
    String startImport(ImportRequest request);

    /**
     * Queries the status of an import job.
     *
     * @param jobId the job ID returned by {@link #startImport}
     * @return current status of the import
     */
    ImportStatus getStatus(String jobId);

    record ImportRequest(
            String source,
            String symbol,
            String segment,
            String interval,
            long fromMs,
            long toMs
    ) {}

    record ImportStatus(
            String jobId,
            State state,
            int completedTasks,
            int totalTasks,
            String message
    ) {}

    enum State {
        PENDING, RUNNING, COMPLETED, FAILED
    }
}
