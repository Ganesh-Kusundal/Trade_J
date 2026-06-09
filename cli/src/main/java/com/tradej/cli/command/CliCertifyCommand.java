package com.tradej.cli.command;

import com.tradej.brokergateway.certification.BrokerCertification;
import com.tradej.brokergateway.certification.CertificationArtifactStore;
import com.tradej.brokergateway.certification.CertificationReport;
import com.tradej.brokergateway.BrokerHandle;
import com.tradej.cli.CliContext;
import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import com.tradej.cli.output.RichTable;
import com.tradej.core.domain.value.ExchangeSegment;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "certify", description = "Run full broker certification suite and store evidence artifacts",
        subcommands = {
                CliCertifyCommand.ReplayCertifyCmd.class,
                CliCertifyCommand.SimulationCertifyCmd.class
        })
public final class CliCertifyCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli root;

    @Parameters(index = "0", arity = "0..1", defaultValue = "dhan")
    String brokerName;

    @Parameters(index = "1", arity = "0..1", defaultValue = "RELIANCE")
    String symbol;

    @Parameters(index = "2", arity = "0..1", defaultValue = "NSE_EQ")
    String segment;

    @Option(names = "--store", description = "Store certification artifacts to disk")
    boolean store;

    @Option(names = "--artifacts-dir", defaultValue = "CertificationArtifacts",
            description = "Directory for certification artifacts")
    String artifactsDir;

    @Override
    public Integer call() {
        CliContext ctx = root.ops().context();
        BrokerHandle broker = ctx.brokerHandle(brokerName);
        ExchangeSegment seg = ExchangeSegment.valueOf(segment.toUpperCase());

        System.out.println(Ansi.bold("\n  Running broker certification: " + brokerName + "\n"));

        CertificationReport report = BrokerCertification.runFull(broker, symbol, seg);

        if (root.json()) {
            System.out.printf("{\"broker\":\"%s\",\"overall\":\"%s\",\"passed\":%d,\"failed\":%d,\"total\":%d,\"latencyMs\":%d}%n",
                    report.broker().name(), report.overall(), report.passed(), report.failed(),
                    report.total(), report.totalLatencyMs());
            return report.isPass() ? 0 : 1;
        }

        System.out.println(report.formatReport());

        if (store) {
            try {
                CertificationArtifactStore artifactStore = new CertificationArtifactStore(Path.of(artifactsDir));
                artifactStore.storeAll(broker.source(), report);
                System.out.println(Ansi.green("  Artifacts stored to: " + artifactsDir + "/" + brokerName));
            } catch (IOException e) {
                System.err.println(Ansi.red("  Failed to store artifacts: " + e.getMessage()));
            }
        }

        return report.isPass() ? 0 : 1;
    }

    @Command(name = "replay", description = "Run replay certification — verify event replay parity and throughput")
    static final class ReplayCertifyCmd implements Callable<Integer> {
        @ParentCommand CliCertifyCommand parent;

        @Override
        public Integer call() {
            System.out.println(Ansi.bold("\n  Replay Certification\n"));
            return runCertificationSuite(
                    ":replay-engine:test",
                    "*ReplayEndToEndCertificationTest*",
                    "Replay E2E Certification"
            );
        }
    }

    @Command(name = "simulation", description = "Run simulation certification — verify matching engine and PnL")
    static final class SimulationCertifyCmd implements Callable<Integer> {
        @ParentCommand CliCertifyCommand parent;

        @Override
        public Integer call() {
            System.out.println(Ansi.bold("\n  Simulation Certification\n"));
            return runCertificationSuite(
                    ":trading-simulation:test",
                    "*SimulationEndToEndCertificationTest*",
                    "Simulation E2E Certification"
            );
        }
    }

    private static int runCertificationSuite(String gradleTask, String testFilter, String suiteName) {
        String workspaceRoot = System.getProperty("trade.workspace.root", ".");
        try {
            System.out.println("  Running: ./gradlew " + gradleTask + " --tests '" + testFilter + "'");
            System.out.println();

            ProcessBuilder pb = new ProcessBuilder("./gradlew", gradleTask,
                    "--tests", testFilter, "--quiet");
            pb.directory(new java.io.File(workspaceRoot));
            pb.redirectErrorStream(true);
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();

            System.out.println();
            if (exitCode == 0) {
                System.out.println(Ansi.green("  " + suiteName + ": PASS"));
                System.out.println("  All certification tests passed.");
            } else {
                System.out.println(Ansi.red("  " + suiteName + ": FAIL (exit code " + exitCode + ")"));
                System.out.println("  Some certification tests failed. Check output above.");
            }
            return exitCode;
        } catch (Exception e) {
            System.out.println(Ansi.red("  " + suiteName + ": ERROR"));
            System.out.println("  " + e.getMessage());
            return 1;
        }
    }
}
