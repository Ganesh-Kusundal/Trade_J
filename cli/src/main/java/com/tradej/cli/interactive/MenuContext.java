package com.tradej.cli.interactive;

import com.tradej.cli.CliContext;
import com.tradej.cli.CliOperations;
import com.tradej.cli.config.CliConfig;
import org.jline.reader.LineReader;

public final class MenuContext {
    private final CliContext cliContext;
    private final CliOperations operations;
    private final LineReader reader;

    public MenuContext(CliContext cliContext, CliOperations operations, LineReader reader) {
        this.cliContext = cliContext;
        this.operations = operations;
        this.reader = reader;
    }

    public CliContext cliContext() {
        return cliContext;
    }

    public CliOperations operations() {
        return operations;
    }

    public LineReader reader() {
        return reader;
    }

    public String prompt(String label, String defaultValue) {
        String line = reader.readLine(label + " [" + defaultValue + "]: ");
        if (line == null || line.isBlank()) {
            return defaultValue;
        }
        return line.trim();
    }

    public String headerLine() {
        String attach = cliContext.attachReachable()
                ? cliContext.attachUrl() + " ✓"
                : cliContext.attachUrl() + " ✗";
        return "Trade-J CLI  [attach: " + attach + "]  [broker: " + cliContext.brokerType()
                + "/" + cliContext.profile() + "]  [mode: " + operations.configuredRuntimeMode() + "]";
    }
}
