package com.tradej.cli.command;

import com.tradej.cli.TestCliContext;
import com.tradej.cli.output.OutputFormatter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Tag("unit")
class CliBrokerCertCommandsTest {

    @Test
    void constructorWorks() {
        assertDoesNotThrow(() -> new CliBrokerCertCommands(
                TestCliContext.create(), new OutputFormatter(false)));
    }
}
