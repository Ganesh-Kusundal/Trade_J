package com.tradej.persistence.replay;

/**
 * Result summary from a {@link ReplayRunner} replay operation.
 * Provides observable counters so callers can detect silent data loss
 * (corrupt entries) or unexpected event types in the queue.
 *
 * @param totalRead Number of raw entries read from the Chronicle Queue
 * @param replayed  Number of entries successfully deserialized and published
 * @param failed    Number of entries that failed deserialization (corrupt or wrong type)
 */
public record ReplayResult(long totalRead, long replayed, long failed) {

    /** Number of entries that are neither replayed nor failed — a derived field. */
    public long skipped() {
        return totalRead - replayed - failed;
    }

    /** True when no entries were read at all. */
    public boolean isEmpty() {
        return totalRead == 0L;
    }

    /** True when every entry was successfully replayed. */
    public boolean isComplete() {
        return totalRead > 0L && replayed == totalRead;
    }

    /** True when at least one entry failed or was skipped. */
    public boolean hasIssues() {
        return failed > 0L || skipped() > 0L;
    }

    /**
     * Compact one-line summary for logging.
     */
    public String summary() {
        if (isEmpty()) {
            return "ReplayResult{empty}";
        }
        return String.format("ReplayResult{total=%d, replayed=%d, failed=%d, skipped=%d}",
                totalRead, replayed, failed, skipped());
    }
}
