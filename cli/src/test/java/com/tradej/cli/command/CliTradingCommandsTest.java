package com.tradej.cli.command;

import com.tradej.cli.TestCliContext;
import com.tradej.cli.output.OutputFormatter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Tag("unit")
class CliTradingCommandsTest {

    @Test
    void constructorWorks() {
        assertDoesNotThrow(() -> new CliTradingCommands(
                TestCliContext.create(), new OutputFormatter(false)));
    }

    @Test
    void orderBookReturnsEmptySafely() {
        var cmd = new CliTradingCommands(TestCliContext.create(), new OutputFormatter(false));
        assertDoesNotThrow(cmd::orderBook);
    }
}
