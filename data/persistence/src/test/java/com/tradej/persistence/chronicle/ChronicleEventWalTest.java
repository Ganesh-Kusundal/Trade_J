package com.tradej.persistence.chronicle;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.PnlUpdatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ChronicleEventWalTest {

    @TempDir
    Path tempDir;

    private ChronicleEventWal wal;

    @BeforeEach
    void setUp() {
        wal = new ChronicleEventWal(tempDir.resolve("wal"));
    }

    @AfterEach
    void tearDown() {
        wal.close();
    }

    private PnlUpdatedEvent pnlEvent(long realized, long unrealized) {
        return new PnlUpdatedEvent(EventMetadata.root(), realized, unrealized, realized + unrealized);
    }

    @Test
    void writeAndReplay_singleEvent() {
        PnlUpdatedEvent event = pnlEvent(1000L, 500L);
        wal.write(event);

        List<ChronicleEventWal.WalRecord> records = new ArrayList<>();
        long replayed = wal.replayRaw(records::add);

        assertEquals(1, replayed);
        assertEquals(1, records.size());
        assertEquals(event.eventId(), records.get(0).eventId());
        assertEquals(PnlUpdatedEvent.class.getName(), records.get(0).eventClass());
    }

    @Test
    void writeAndReplay_multipleEvents() {
        for (int i = 0; i < 10; i++) {
            wal.write(pnlEvent(i * 100L, i * 50L));
        }

        assertEquals(10, wal.writeCount());

        List<ChronicleEventWal.WalRecord> records = new ArrayList<>();
        long replayed = wal.replayRaw(records::add);

        assertEquals(10, replayed);
        assertEquals(10, records.size());
    }

    @Test
    void replay_emptyWal_returnsZero() {
        List<ChronicleEventWal.WalRecord> records = new ArrayList<>();
        long replayed = wal.replayRaw(records::add);
        assertEquals(0, replayed);
        assertTrue(records.isEmpty());
    }

    @Test
    void drain_returnsAllRecords() {
        wal.write(pnlEvent(100L, 50L));
        wal.write(pnlEvent(200L, 100L));

        List<ChronicleEventWal.WalRecord> records = wal.drain();
        assertEquals(2, records.size());
    }

    @Test
    void writeCount_incrementsOnEachWrite() {
        assertEquals(0, wal.writeCount());
        wal.write(pnlEvent(100L, 50L));
        assertEquals(1, wal.writeCount());
        wal.write(pnlEvent(200L, 100L));
        assertEquals(2, wal.writeCount());
    }

    @Test
    void walEntry_containsTimestampAndClass() {
        wal.write(pnlEvent(100L, 50L));
        ChronicleEventWal.WalRecord record = wal.drain().get(0);

        assertTrue(record.timestampMs() > 0, "Timestamp should be positive");
        assertEquals(PnlUpdatedEvent.class.getName(), record.eventClass());
        assertNotNull(record.eventJson());
        assertFalse(record.eventJson().isBlank());
    }
}
