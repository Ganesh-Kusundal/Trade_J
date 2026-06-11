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
                CliCertifyCommand.SimulationCertifyCmd.class,
                CliCertifyCommand.BuildCertifyCmd.class,
                CliCertifyCommand.PlatformCertifyCmd.class,
                CliCertifyCommand.BrokerCertifyCmd.class,
                CliCertifyCommand.GatewayCertifyCmd.class,
                CliCertifyCommand.CredentialsCertifyCmd.class,
                CliCertifyCommand.DataPlatformCertifyCmd.class,
                CliCertifyCommand.DataIntegrityCertifyCmd.class,
                CliCertifyCommand.ReplayDeterminismCertifyCmd.class,
                CliCertifyCommand.RuntimeCertifyCmd.class,
                CliCertifyCommand.CapabilitiesCertifyCmd.class,
                CliCertifyCommand.StrategyCertifyCmd.class,
                CliCertifyCommand.OperationalCertifyCmd.class
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
                    ":app:test",
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

    @Command(name = "build", description = "Run build certification — compilation, architecture, static analysis")
    static final class BuildCertifyCmd implements Callable<Integer> {
        @ParentCommand CliCertifyCommand parent;

        @Override
        public Integer call() {
            System.out.println(Ansi.bold("\n  Build Certification (Level -1)\n"));
            return runScriptCertification(
                    "scripts/certify-level-minus1.sh",
                    "Build Certification"
            );
        }
    }

    @Command(name = "platform", description = "Run platform foundation certification — config, storage, events")
    static final class PlatformCertifyCmd implements Callable<Integer> {
        @ParentCommand CliCertifyCommand parent;

        @Override
        public Integer call() {
            System.out.println(Ansi.bold("\n  Platform Foundation Certification (Level 0)\n"));
            return runScriptCertification(
                    "scripts/certify-level-0.sh",
                    "Platform Foundation Certification"
            );
        }
    }

    @Command(name = "brokers", description = "Run Level 1 broker certification — authentication, market data, WebSocket, orders, resilience")
    static final class BrokerCertifyCmd implements Callable<Integer> {
        @ParentCommand CliCertifyCommand parent;

        @Override
        public Integer call() {
            System.out.println(Ansi.bold("\n  Level 1: Broker Certification\n"));
            return runScriptCertification(
                    "scripts/certify-level-1.sh",
                    "Broker Certification (Level 1)"
            );
        }
    }

    @Command(name = "gateway", description = "Run Level 2.5 gateway certification — BrokerGateway, BrokerHandle, capabilities, extras")
    static final class GatewayCertifyCmd implements Callable<Integer> {
        @ParentCommand CliCertifyCommand parent;

        @Override
        public Integer call() {
            System.out.println(Ansi.bold("\n  Level 2.5: Gateway Certification\n"));
            return runScriptCertification(
                    "scripts/certify-level-2-5.sh",
                    "Gateway Certification (Level 2.5)"
            );
        }
    }

    @Command(name = "credentials", description = "Validate all broker credentials — token freshness, config files, sessions")
    static final class CredentialsCertifyCmd implements Callable<Integer> {
        @ParentCommand CliCertifyCommand parent;

        @Override
        public Integer call() {
            System.out.println(Ansi.bold("\n  Broker Credential Validation\n"));
            return runScriptCertification(
                    "scripts/validate-credentials.sh",
                    "Credential Validation"
            );
        }
    }

    @Command(name = "data", description = "Run Level 2 data platform certification — download, Parquet, DuckDB, analytics")
    static final class DataPlatformCertifyCmd implements Callable<Integer> {
        @ParentCommand CliCertifyCommand parent;

        @Override
        public Integer call() {
            System.out.println(Ansi.bold("\n  Level 2: Data Platform Certification\n"));
            return runScriptCertification(
                    "scripts/certify-level-2.sh",
                    "Data Platform Certification (Level 2)"
            );
        }
    }

    @Command(name = "data-integrity", description = "Run data integrity certification — verify Broker=Parquet=DuckDB=Replay consistency")
    static final class DataIntegrityCertifyCmd implements Callable<Integer> {
        @ParentCommand CliCertifyCommand parent;

        @Override
        public Integer call() {
            System.out.println(Ansi.bold("\n  Data Integrity Certification\n"));
            return runCertificationSuite(
                    ":data-historical-ingest:test",
                    "*DataPlatformCertificationTest.dataIntegrityAcrossStorageLayers",
                    "Data Integrity Certification"
            );
        }
    }

    @Command(name = "replay-determinism", description = "Run replay determinism certification (MOST IMPORTANT) — run twice, verify identical results")
    static final class ReplayDeterminismCertifyCmd implements Callable<Integer> {
        @ParentCommand CliCertifyCommand parent;

        @Override
        public Integer call() {
            System.out.println(Ansi.bold("\n  Replay Determinism Certification (CRITICAL)\n"));
            System.out.println(Ansi.yellow("  If this test FAILS, strategy results are NOT trustworthy!"));
            return runCertificationSuite(
                    ":app:test",
                    "*ReplayDeterminismCertificationTest*",
                    "Replay Determinism Certification"
            );
        }
    }

    @Command(name = "runtime", description = "Run Level 3 runtime certification — replay, simulation, execution, event flow")
    static final class RuntimeCertifyCmd implements Callable<Integer> {
        @ParentCommand CliCertifyCommand parent;

        @Override
        public Integer call() {
            System.out.println(Ansi.bold("\n  Level 3: Runtime Certification\n"));
            return runScriptCertification(
                    "scripts/certify-level-3.sh",
                    "Runtime Certification (Level 3)"
            );
        }
    }

    @Command(name = "capabilities", description = "Run Level 4 capability certification — scanner, options, paper trading, live trading infra, performance")
    static final class CapabilitiesCertifyCmd implements Callable<Integer> {
        @ParentCommand CliCertifyCommand parent;

        @Override
        public Integer call() {
            System.out.println(Ansi.bold("\n  Level 4: Capability Certification\n"));
            return runScriptCertification(
                    "scripts/certify-level-4.sh",
                    "Capability Certification (Level 4)"
            );
        }
    }

    @Command(name = "strategy", description = "Run Level 5 strategy certification — Half Trend, scanner strategy, research platform, risk management")
    static final class StrategyCertifyCmd implements Callable<Integer> {
        @ParentCommand CliCertifyCommand parent;

        @Override
        public Integer call() {
            System.out.println(Ansi.bold("\n  Level 5: Strategy Certification\n"));
            System.out.println(Ansi.yellow("  ⭐ Strategy Research Platform is CRITICAL"));
            return runScriptCertification(
                    "scripts/certify-level-5.sh",
                    "Strategy Certification (Level 5)"
            );
        }
    }

    @Command(name = "operational", description = "Run Level 6 operational readiness certification — health, metrics, logging, recovery, backup, retention")
    static final class OperationalCertifyCmd implements Callable<Integer> {
        @ParentCommand CliCertifyCommand parent;

        @Override
        public Integer call() {
            System.out.println(Ansi.bold("\n  Level 6: Operational Readiness Certification\n"));
            return runScriptCertification(
                    "scripts/certify-level-6.sh",
                    "Operational Readiness Certification (Level 6)"
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

    private static int runScriptCertification(String scriptPath, String suiteName) {
        String workspaceRoot = System.getProperty("trade.workspace.root", ".");
        try {
            System.out.println("  Running: " + scriptPath);
            System.out.println();

            ProcessBuilder pb = new ProcessBuilder("bash", scriptPath);
            pb.directory(new java.io.File(workspaceRoot));
            pb.redirectErrorStream(true);
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();

            System.out.println();
            if (exitCode == 0) {
                System.out.println(Ansi.green("  " + suiteName + ": PASS"));
            } else {
                System.out.println(Ansi.red("  " + suiteName + ": FAIL (exit code " + exitCode + ")"));
            }
            return exitCode;
        } catch (Exception e) {
            System.out.println(Ansi.red("  " + suiteName + ": ERROR"));
            System.out.println("  " + e.getMessage());
            return 1;
        }
    }
}
