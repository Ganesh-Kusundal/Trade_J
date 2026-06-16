package com.tradej.persistence.chronicle;

import net.openhft.chronicle.queue.ChronicleQueue;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Shared Chronicle Queue retention cleanup utility.
 *
 * <p>Deletes old {@code .cq4} files from a Chronicle queue directory while
 * preserving the currently active file (identified via {@link ChronicleQueue#file()}).
 */
public final class ChronicleRetention {

    private ChronicleRetention() {
        // utility class
    }

    /**
     * Deletes Chronicle queue files older than the specified number of days,
     * preserving the currently active file.
     *
     * @param queue         the Chronicle queue whose files to clean up
     * @param retentionDays files older than this many days are deleted
     * @param log           logger for progress and error messages
     * @return number of files deleted
     */
    public static int cleanupOldFiles(ChronicleQueue queue, long retentionDays, Logger log) {
        long cutoffMs = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L);
        Path queueDir = queue.file().toPath().getParent();
        if (queueDir == null || !Files.isDirectory(queueDir)) {
            return 0;
        }
        // Identify the active Chronicle file explicitly — do not delete it
        Path currentFile = queue.file().toPath().toAbsolutePath().normalize();
        int deleted = 0;
        try (Stream<Path> files = Files.list(queueDir)) {
            deleted = (int) files
                    .filter(f -> f.getFileName().toString().endsWith(".cq4"))
                    .filter(f -> !f.toAbsolutePath().normalize().equals(currentFile))
                    .filter(f -> {
                        try {
                            return Files.getLastModifiedTime(f).toMillis() < cutoffMs;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .peek(f -> {
                        try {
                            Files.delete(f);
                            log.info("Chronicle retention: deleted {}", f.getFileName());
                        } catch (IOException e) {
                            log.warn("Chronicle retention: failed to delete {} — {}", f.getFileName(), e.getMessage());
                        }
                    })
                    .mapToInt(f -> 1)
                    .sum();
        } catch (IOException e) {
            log.warn("Chronicle retention: failed to list directory {} — {}", queueDir, e.getMessage());
        }
        if (deleted > 0) {
            log.info("Chronicle retention: deleted {} files older than {} days from {}", deleted, retentionDays, queueDir);
        }
        return deleted;
    }
}
