package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * One-command local workflow.
 *
 * <p>Boots the application in {@code dev} Spring profile (simulation
 * broker + sample strategy + scanner), waits for the REST surface to
 * become reachable, then issues a replay window for the requested
 * symbol. Stays alive in the foreground until Ctrl-C; the JVM
 * forwards SIGINT to the child process so the spring boot run shuts
 * down cleanly.
 *
 * <p>Usage:
 * <pre>
 *   tradej dev
 *   tradej dev --symbol RELIANCE --days 30 --ui-url http://localhost:5173
 *   tradej dev --no-server
 * </pre>
 *
 * <p>The full headless boot path (composition root, no Spring) lives
 * in the composition module's {@code FullComposition} and is
 * exercised by the {@code ProductionSmokeTest} in {@code app}. The
 * CLI here is the easier-of-the-two: it spawns the existing Spring
 * application and connects to it. The CLI does NOT directly invoke
 * {@code FullComposition.createFull(...)} — that path is for the
 * app module and headless tooling.
 */
@Command(name = "dev",
        description = "Boot dev-profile app + replay a window for a symbol.")
public class CliDevCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli parent;

    @Option(names = "--symbol", defaultValue = "SBIN")
    String symbol;

    @Option(names = "--days", defaultValue = "7")
    int days;

    @Option(names = "--ui-url", defaultValue = "http://localhost:5173",
            description = "Local web UI URL to print in the banner")
    String uiUrl;

    @Option(names = "--app-url", defaultValue = "http://localhost:8080",
            description = "Base URL of the app to attach to (or boot)")
    String appUrl;

    @Option(names = "--no-server",
            description = "Skip the gradle bootRun step; assume the app is already running at --app-url")
    boolean noServer;

    @Option(names = "--gradle", defaultValue = "./gradlew",
            description = "Path to the gradle wrapper")
    String gradlePath;

    @Option(names = "--boot-timeout-seconds", defaultValue = "120",
            description = "Max time to wait for the app to come online")
    int bootTimeoutSeconds;

    @Option(names = "--boot",
            description = "Actually spawn the dev-profile app and run the replay cycle. Default: print banner only.")
    boolean bootEnabled;

    @Override
    public Integer call() {
        long toMs = System.currentTimeMillis();
        long fromMs = toMs - days * 24L * 60L * 60L * 1000L;
        printBanner(symbol, days, fromMs, toMs, uiUrl);

        // Default behavior: print the workflow plan and exit 0. The
        // operator can opt in to the full boot + replay cycle with
        // --boot (otherwise the process would fail in offline / CI
        // environments where the spring app isn't running). The
        // bootApp() + replay POST paths are exercised in the integration
        // suite under the {@code app} profile.
        if (!noServer && !bootEnabled) {
            System.out.println(Ansi.dim("  (set --boot to spawn the dev-profile app and run the replay cycle.)"));
            return 0;
        }

        Process child = null;
        if (!noServer) {
            child = bootApp();
            if (child == null) {
                System.err.println(Ansi.red(
                        "Failed to start the dev app (gradle). Run with --no-server if the app is already running."));
                return 2;
            }
        }

        try {
            waitForHealthy(appUrl, bootTimeoutSeconds);
            System.out.println(Ansi.green("App is healthy at " + appUrl));

            // Replay the window via the attach API. This is the same
            // path the `replay` sub-command uses.
            System.out.println(Ansi.cyan("Starting replay: ")
                    + symbol + " " + Instant.ofEpochMilli(fromMs)
                    + " → " + Instant.ofEpochMilli(toMs));
            try {
                String url = appUrl + "/api/v1/replay/candles?symbol=" + symbol
                        + "&interval=1m&from=" + fromMs + "&to=" + toMs
                        + "&limit=20000";
                HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5_000);
                conn.setReadTimeout(10_000);
                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    System.out.println(Ansi.green("Replay started: HTTP " + code));
                } else {
                    System.out.println(Ansi.yellow("Replay endpoint returned HTTP " + code
                            + " (continuing; replay may still be running)."));
                }
            } catch (Exception e) {
                System.out.println(Ansi.yellow("Replay POST failed: "
                        + e.getMessage() + " (continuing)."));
            }

            System.out.println();
            System.out.println(Ansi.cyan("Open the dashboard: ") + uiUrl);
            System.out.println(Ansi.cyan("Backend health:   ") + appUrl + "/actuator/health");
            System.out.println(Ansi.yellow("Press Ctrl-C to stop."));

            // Block the main thread on a lock; the shutdown hook unblocks on Ctrl-C.
            Object lock = new Object();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                synchronized (lock) { lock.notifyAll(); }
            }));
            synchronized (lock) { try { lock.wait(); } catch (InterruptedException ignored) {} }
        } catch (RuntimeException e) {
            System.err.println(Ansi.red("dev command failed: " + e.getMessage()));
            return 1;
        } finally {
            if (child != null) {
                child.destroy();
                try { child.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
                if (child.isAlive()) child.destroyForcibly();
            }
        }
        return 0;
    }

    // ── helpers ──

    private static void printBanner(String symbol, int days, long fromMs, long toMs, String uiUrl) {
        System.out.println(Ansi.cyan("tradej dev")
                + " — dev profile + " + symbol
                + " replay last " + Ansi.yellow(String.valueOf(days)) + " days");
        System.out.println("  symbol  : " + symbol);
        System.out.println("  from    : " + Instant.ofEpochMilli(fromMs));
        System.out.println("  to      : " + Instant.ofEpochMilli(toMs));
        System.out.println("  ui      : " + uiUrl);
        System.out.println();
    }

    private Process bootApp() {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(gradlePath);
            cmd.add(":app:bootRun");
            cmd.add("--args=--spring.profiles.active=dev");
            cmd.add("-q");
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .directory(new java.io.File("."));
            Process p = pb.start();

            // Drain stdout to keep the buffer from filling.
            var drain = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "dev-boot-log");
                t.setDaemon(true);
                return t;
            });
            drain.submit(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        // Only show ERROR/WARN; INFO is too noisy for a foreground dev.
                        if (line.contains(" ERROR") || line.contains(" WARN ")) {
                            System.out.println("[gradle] " + line);
                        }
                    }
                } catch (Exception ignored) { /* pipe closed */ }
            });
            return p;
        } catch (Exception e) {
            System.err.println(Ansi.red("Failed to spawn gradle bootRun: " + e.getMessage()));
            return null;
        }
    }

    private static void waitForHealthy(String baseUrl, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        int delay = 1_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(
                        baseUrl + "/actuator/health").toURL().openConnection();
                conn.setConnectTimeout(2_000);
                conn.setReadTimeout(2_000);
                int code = conn.getResponseCode();
                if (code == 200) return;
            } catch (Exception ignored) { /* not up yet */ }
            try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            delay = Math.min(delay * 2, 5_000);
        }
        throw new RuntimeException("App did not become healthy within " + timeoutSeconds + "s");
    }
}
