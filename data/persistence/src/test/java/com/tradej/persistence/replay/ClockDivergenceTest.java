package com.tradej.persistence.replay;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderPartiallyFilled;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.persistence.duckdb.DuckDbEventStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NR-07: historical queries must filter by domain {@code event_time_ms}, not wall-clock ingestion.
 */
@Tag("unit")
class ClockDivergenceTest {

    private static final long WALL_INGEST_BASE_MS = 1_700_000_000_000L;

    private Path dbPath;
    private DuckDbEventStore eventStore;
    private HistoricalRangeService historicalRangeService;
    private AtomicLong ingestClock;

    @BeforeEach
    void setUp() throws Exception {
        dbPath = Files.createTempFile("clock-divergence-", ".duckdb");
        Files.deleteIfExists(dbPath);
        ingestClock = new AtomicLong(WALL_INGEST_BASE_MS);
        eventStore = new DuckDbEventStore(dbPath, () -> ingestClock.addAndGet(60_000L));
        historicalRangeService = new HistoricalRangeService(dbPath);
    }

    @AfterEach
    void tearDown() throws Exception {
        historicalRangeService.close();
        eventStore.close();
        Files.deleteIfExists(dbPath);
    }

    @Test
    void queryRangeUsesVirtualEventTimeNotWallIngestion() throws Exception {
        Order order = new Order(
                "ORD-CLK",
                "corr-clk",
                "SBIN",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                ProductType.INTRADAY,
                OrderType.MARKET,
                OrderStatus.PART_TRADED,
                500L,
                100L,
                150_00L,
                0L,
                0L,
                ""
        );
        for (long eventTimeMs = 1_000L; eventTimeMs <= 5_000L; eventTimeMs += 1_000L) {
            var metadata = new EventMetadata(
                    UUID.randomUUID().toString(),
                    eventTimeMs,
                    System.nanoTime(),
                    eventTimeMs,
                    "corr-clk",
                    1
            );
            List<Trade> fills = List.of(
                    new Trade(
                            "T-" + eventTimeMs,
                            "ORD-CLK",
                            "SBIN",
                            ExchangeSegment.NSE_EQ,
                            Side.BUY,
                            20L,
                            150_00L,
                            eventTimeMs
                    )
            );
            eventStore.onEvent(new OrderPartiallyFilled(metadata, order, fills));
        }

        List<HistoricalRangeService.HistoricalFillEvent> inVirtualWindow =
                historicalRangeService.queryFillEvents("SBIN", 2_500L, 4_500L, 10);

        assertEquals(2, inVirtualWindow.size(), "events at 3000ms and 4000ms must match virtual-time window");
    }
}
