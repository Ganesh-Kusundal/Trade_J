package com.tradej.cli.command;

import com.tradej.cli.TestCliContext;
import com.tradej.cli.output.OutputFormatter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Tag("unit")
class CliDataCommandsTest {

    @Test
    void constructorWorks() {
        assertDoesNotThrow(() -> new CliDataCommands(
                TestCliContext.create(), new OutputFormatter(false)));
    }
}
