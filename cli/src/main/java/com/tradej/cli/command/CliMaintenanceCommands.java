package com.tradej.cli.command;

import com.tradej.broker.dhan.config.DhanConfigPaths;
import com.tradej.cli.CliContext;
import com.tradej.cli.output.OutputFormatter;

import java.io.IOException;
import java.util.List;

public final class CliMaintenanceCommands extends CliCommandSupport {

    public CliMaintenanceCommands(CliContext context, OutputFormatter out) {
        super(context, out);
    }

    public int runProcess(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(DhanConfigPaths.resolve(".").toFile());
        builder.inheritIO();
        Process process = builder.start();
        return process.waitFor();
    }

    public void tokenRefresh() throws IOException, InterruptedException {
        int code = runProcess(List.of("bash", "scripts/refresh-dhan-token.sh"));
        if (code != 0) {
            throw new IllegalStateException("Token refresh failed with exit code " + code);
        }
    }

    public void runGradleTest(String task) throws IOException, InterruptedException {
        int code = runProcess(List.of("./gradlew", task, "--no-daemon"));
        if (code != 0) {
            throw new IllegalStateException("Gradle task failed: " + task);
        }
    }
}
