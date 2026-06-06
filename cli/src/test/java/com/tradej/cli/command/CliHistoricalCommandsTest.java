package com.tradej.cli.command;

import com.tradej.cli.TestCliContext;
import com.tradej.cli.output.OutputFormatter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class CliHistoricalCommandsTest {

    private final CliHistoricalCommands cmd = new CliHistoricalCommands(
            TestCliContext.create(), new OutputFormatter(false));

    @Test
    void constructorWorks() {
        assertDoesNotThrow(() -> new CliHistoricalCommands(
                TestCliContext.create(), new OutputFormatter(false)));
    }

    @Test
    void candlesFailsGracefullyWithoutDuckDB() {
        assertThrows(Exception.class, () ->
                cmd.candles("RELIANCE", "5m", 0L, 1000L, 10));
    }

    @Test
    void ticksFailsGracefullyWithoutDuckDB() {
        assertThrows(Exception.class, () ->
                cmd.ticks("RELIANCE", 0L, 1000L, 10));
    }

    @Test
    void ordersFailsGracefullyWithoutDuckDB() {
        assertThrows(Exception.class, () ->
                cmd.orders("RELIANCE", 0L, 1000L, 10));
    }

    @Test
    void fillsFailsGracefullyWithoutDuckDB() {
        assertThrows(Exception.class, () ->
                cmd.fills("RELIANCE", 0L, 1000L, 10));
    }

    @Test
    void statsFailsGracefullyWithoutDuckDB() {
        assertThrows(Exception.class, () ->
                cmd.stats("RELIANCE", 0L, 1000L));
    }
}
