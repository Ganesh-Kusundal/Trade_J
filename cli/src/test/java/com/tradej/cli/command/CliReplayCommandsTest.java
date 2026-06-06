package com.tradej.cli.command;

import com.tradej.cli.TestCliContext;
import com.tradej.cli.output.OutputFormatter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Tag("unit")
class CliReplayCommandsTest {

    @Test
    void constructorWorks() {
        assertDoesNotThrow(() -> new CliReplayCommands(
                TestCliContext.create(), new OutputFormatter(false)));
    }
}
