package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import com.tradej.cli.output.RichTable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "doctor", description = "System health check — verifies Java, drivers, SPI providers, and runtime paths")
public final class CliDoctorCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli root;

    @Override
    public Integer call() {
        List<CliDoctorOperations.CheckResult> results = CliDoctorOperations.runAllChecks();

        if (root.json()) {
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < results.size(); i++) {
                if (i > 0) json.append(",");
                var r = results.get(i);
                json.append(String.format(
                        "{\"name\":\"%s\",\"status\":\"%s\",\"detail\":\"%s\"}",
                        r.name(), r.status(), r.detail().replace("\"", "\\\"")));
            }
            json.append("]");
            System.out.println(json);
            return 0;
        }

        System.out.println(Ansi.bold("\n  Trade-J System Health Check\n"));

        RichTable table = RichTable.of("Check", "Status", "Detail")
                .title("System Diagnostics")
                .rowFormatter(row -> {
                    String status = row[1];
                    String colored = switch (status) {
                        case "PASS" -> Ansi.green(status);
                        case "WARN" -> Ansi.yellow(status);
                        case "FAIL" -> Ansi.red(status);
                        default -> Ansi.dim(status);
                    };
                    return new String[]{row[0], colored, row[2]};
                });

        for (CliDoctorOperations.CheckResult r : results) {
            table.addRow(r.name(), r.status(), r.detail());
        }
        table.print();

        long pass = results.stream().filter(r -> "PASS".equals(r.status())).count();
        long warn = results.stream().filter(r -> "WARN".equals(r.status())).count();
        long fail = results.stream().filter(r -> "FAIL".equals(r.status())).count();
        System.out.printf("  %d passed, %d warnings, %d failed%n%n", pass, warn, fail);

        return fail > 0 ? 1 : 0;
    }
}
