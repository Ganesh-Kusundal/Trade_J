package com.tradej.historical.ingest.planner;

import com.tradej.core.domain.instrument.RollingExpiryKind;
import com.tradej.core.domain.instrument.RollingExpiryRoll;
import com.tradej.core.domain.instrument.StrikeOffset;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.historical.ingest.model.RollingOptionDownloadConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class RollingOptionDownloadPlannerTest {

    @Test
    void plansCartesianProductWithDateChunks() {
        RollingOptionDownloadConfig config = new RollingOptionDownloadConfig(
                List.of("NIFTY", "BANKNIFTY"),
                ExchangeSegment.IDX_I,
                LocalDate.of(2021, 1, 1),
                LocalDate.of(2021, 3, 15),
                List.of(5),
                List.of(
                        new RollingExpiryRoll(RollingExpiryKind.WEEK, 1),
                        new RollingExpiryRoll(RollingExpiryKind.MONTH, 1)),
                StrikeOffset.atmPlusMinus(1),
                List.of(OptionType.CALL, OptionType.PUT),
                350L,
                true
        );
        var tasks = new RollingOptionDownloadPlanner().planTasks("job-x", config);
        long expected = RollingOptionDownloadPlanner.estimatedTaskCount(config);
        assertEquals(expected, tasks.size());
        assertEquals(2 * 2 * 3 * 2 * 1 * 3, tasks.size());
        assertEquals(-1, tasks.getFirst().strikeOffset());
        assertEquals("WEEK", tasks.getFirst().expiryKind());
    }

    @Test
    void phaseAEstimatedTaskCountMatchesPlan() {
        RollingOptionDownloadConfig config = new RollingOptionDownloadConfig(
                List.of("NIFTY", "BANKNIFTY"),
                ExchangeSegment.IDX_I,
                LocalDate.of(2021, 1, 1),
                LocalDate.now(),
                List.of(5),
                List.of(
                        new RollingExpiryRoll(RollingExpiryKind.WEEK, 1),
                        new RollingExpiryRoll(RollingExpiryKind.WEEK, 2),
                        new RollingExpiryRoll(RollingExpiryKind.MONTH, 1)),
                StrikeOffset.atmPlusMinus(10),
                List.of(OptionType.CALL, OptionType.PUT),
                350L,
                true
        );
        long tasks = RollingOptionDownloadPlanner.estimatedTaskCount(config);
        assertTrue(tasks >= 15_000L, "expected ~15k tasks, got " + tasks);
    }
}
