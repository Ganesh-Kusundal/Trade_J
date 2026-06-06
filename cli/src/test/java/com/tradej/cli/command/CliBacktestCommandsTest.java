package com.tradej.cli.command;

import com.tradej.cli.TestCliContext;
import com.tradej.cli.output.OutputFormatter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Tag("unit")
class CliBacktestCommandsTest {

    @Test
    void constructorWorks() {
        assertDoesNotThrow(() -> new CliBacktestCommands(
                TestCliContext.create(), new OutputFormatter(false)));
    }

    @Test
    void listReturnsSafeMessage() {
        var cmd = new CliBacktestCommands(TestCliContext.create(), new OutputFormatter(false));
        assertDoesNotThrow(() -> cmd.list(10));
    }

    @Test
    void statusReturnsSafeMessage() {
        var cmd = new CliBacktestCommands(TestCliContext.create(), new OutputFormatter(false));
        assertDoesNotThrow(() -> cmd.status("test-run-id"));
    }
}
