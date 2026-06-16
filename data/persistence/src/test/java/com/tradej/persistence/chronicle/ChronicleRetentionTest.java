package com.tradej.persistence.chronicle;

import net.openhft.chronicle.queue.ChronicleQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChronicleRetentionTest {

    private static final Logger log = LoggerFactory.getLogger(ChronicleRetentionTest.class);

    @TempDir
    Path tempDir;

    @Test
    void noOldFiles_returnsZero() throws Exception {
        ChronicleQueue queue = createQueue("no-old-files");
        try {
            int deleted = ChronicleRetention.cleanupOldFiles(queue, 7, log);
            assertEquals(0, deleted, "No files should be deleted when queue is fresh");
        } finally {
            queue.close();
        }
    }

    @Test
    void oldFilesBeyondRetention_areDeleted() throws Exception {
        ChronicleQueue queue = createQueue("old-files");
        Path queueDir = queueDir(queue);
        try {
            // Create 3 .cq4 files aged 40, 60, and 90 days
            Path file40d = createOldCq4File(queueDir, "20200101", 40);
            Path file60d = createOldCq4File(queueDir, "20200201", 60);
            Path file90d = createOldCq4File(queueDir, "20200301", 90);

            // Retention = 30 days — all 3 should be deleted
            int deleted = ChronicleRetention.cleanupOldFiles(queue, 30, log);
            assertEquals(3, deleted, "All 3 files older than 30 days should be deleted");

            assertTrue(Files.notExists(file40d), "40-day-old file should be deleted");
            assertTrue(Files.notExists(file60d), "60-day-old file should be deleted");
            assertTrue(Files.notExists(file90d), "90-day-old file should be deleted");
        } finally {
            queue.close();
        }
    }

    @Test
    void activeFile_isNeverDeleted() throws Exception {
        ChronicleQueue queue = createQueue("active-file");
        Path queueDir = queueDir(queue);
        try {
            // Create old files plus the active queue file
            Path oldFile = createOldCq4File(queueDir, "20200101", 60);
            Path activeFile = queue.file().toPath().toAbsolutePath().normalize();

            // Also set the active file's age to be old — it should STILL not be deleted
            Files.setLastModifiedTime(activeFile, FileTime.from(Instant.now().minus(60, ChronoUnit.DAYS)));

            int deleted = ChronicleRetention.cleanupOldFiles(queue, 30, log);
            assertEquals(1, deleted, "Only the old non-active file should be deleted");
            assertTrue(Files.notExists(oldFile), "Old non-active file should be deleted");
            assertTrue(Files.exists(activeFile), "Active file must never be deleted, even if old");
        } finally {
            queue.close();
        }
    }

    @Test
    void filesWithinRetention_areNotDeleted() throws Exception {
        ChronicleQueue queue = createQueue("recent-files");
        Path queueDir = queueDir(queue);
        try {
            // Create a file that's only 5 days old — within 30-day retention
            Path recentFile = createOldCq4File(queueDir, "20250610", 5);
            // Create a file that's 60 days old — beyond retention
            Path oldFile = createOldCq4File(queueDir, "20200401", 60);

            int deleted = ChronicleRetention.cleanupOldFiles(queue, 30, log);
            assertEquals(1, deleted, "Only the file beyond 30 days should be deleted");
            assertTrue(Files.exists(recentFile), "Recent file should not be deleted");
            assertTrue(Files.notExists(oldFile), "Old file should be deleted");
        } finally {
            queue.close();
        }
    }

    @Test
    void nonCq4Files_areIgnored() throws Exception {
        ChronicleQueue queue = createQueue("mixed-files");
        Path queueDir = queueDir(queue);
        try {
            // Create a .cq4 file that's old
            Path oldCq4 = createOldCq4File(queueDir, "20200101", 60);
            // Create .txt and .metadata files that are old — should be ignored
            Path txtFile = queueDir.resolve("readme.txt");
            Files.writeString(txtFile, "hello");
            Files.setLastModifiedTime(txtFile, FileTime.from(Instant.now().minus(60, ChronoUnit.DAYS)));

            Path metaFile = queueDir.resolve("metadata.cq4t");
            Files.writeString(metaFile, "meta");
            Files.setLastModifiedTime(metaFile, FileTime.from(Instant.now().minus(60, ChronoUnit.DAYS)));

            int deleted = ChronicleRetention.cleanupOldFiles(queue, 30, log);
            assertEquals(1, deleted, "Only .cq4 files should be deleted");
            assertTrue(Files.notExists(oldCq4), "Old .cq4 file should be deleted");
            assertTrue(Files.exists(txtFile), ".txt file should be ignored");
            assertTrue(Files.exists(metaFile), ".cq4t file should be ignored");
        } finally {
            queue.close();
        }
    }

    @Test
    void retentionDaysZero_deletesAllButActive() throws Exception {
        ChronicleQueue queue = createQueue("zero-retention");
        Path queueDir = queueDir(queue);
        try {
            // Create 2 old .cq4 files
            Path file1 = createOldCq4File(queueDir, "20200101", 10);
            Path file2 = createOldCq4File(queueDir, "20200201", 15);

            // retentionDays=0 means "delete everything older than right now"
            // The 2 manually-created old files should be deleted but the active file preserved
            int deleted = ChronicleRetention.cleanupOldFiles(queue, 0, log);
            assertEquals(2, deleted, "All non-active files should be deleted with 0-day retention");
            assertTrue(Files.notExists(file1));
            assertTrue(Files.notExists(file2));
            assertTrue(Files.exists(queue.file().toPath()), "Active file must survive even 0-day retention");
        } finally {
            queue.close();
        }
    }

    @Test
    void onlyActiveFile_exists_nothingDeleted() throws Exception {
        ChronicleQueue queue = createQueue("single-file");
        try {
            // Only the active queue file exists in the directory
            int deleted = ChronicleRetention.cleanupOldFiles(queue, 1, log);
            assertEquals(0, deleted, "Nothing should be deleted when only the active file exists");
        } finally {
            queue.close();
        }
    }

    // ── Helpers ──

    private ChronicleQueue createQueue(String subdir) throws IOException {
        Path queuePath = tempDir.resolve(subdir);
        Files.createDirectories(queuePath);
        return ChronicleQueue.singleBuilder(queuePath.toFile()).build();
    }

    /**
     * Returns the directory containing the queue's .cq4 files.
     */
    private static Path queueDir(ChronicleQueue queue) {
        return queue.file().toPath().getParent();
    }

    /**
     * Creates a .cq4 file with the given name in the queue directory,
     * aged by the specified number of days.
     */
    private Path createOldCq4File(Path queueDir, String baseName, int ageDays) throws IOException {
        Path file = queueDir.resolve(baseName + ".cq4");
        Files.writeString(file, "mock chronicle data");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(ageDays, ChronoUnit.DAYS)));
        return file;
    }
}
