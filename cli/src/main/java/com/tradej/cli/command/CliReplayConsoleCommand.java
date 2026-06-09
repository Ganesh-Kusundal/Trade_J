package com.tradej.cli.command;

import com.tradej.cli.CliOperations;
import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import com.tradej.cli.output.ProgressBar;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Interactive replay console with play/pause/step/speed/goto controls.
 *
 * <p>Usage:
 * <pre>
 *   tradej replay-console --symbol RELIANCE --from 2026-06-07T09:15 --to 2026-06-07T15:30
 * </pre>
 *
 * <p>Controls:
 * <pre>
 *   play / pause    Toggle playback
 *   step [N]        Step N events (default 1)
 *   speed [Nx]      Set playback speed (1x, 10x, 100x, 1000x)
 *   goto [time]     Jump to timestamp
 *   positions       Show current positions
 *   orders          Show current orders
 *   pnl             Show current P&L
 *   quit            Exit replay
 * </pre>
 */
@Command(name = "replay-console", description = "Interactive replay console with play/pause/step/speed controls")
public final class CliReplayConsoleCommand implements Callable<Integer> {

    @ParentCommand TradeCli root;

    @Option(names = "--symbol", required = true, description = "Symbol to replay")
    String symbol;

    @Option(names = "--from", required = true, description = "Start timestamp (epoch ms or ISO)")
    String from;

    @Option(names = "--to", required = true, description = "End timestamp (epoch ms or ISO)")
    String to;

    @Option(names = "--speed", description = "Initial playback speed multiplier", defaultValue = "1")
    int initialSpeed;

    @Override
    public Integer call() throws Exception {
        CliOperations ops = root.ops();

        System.out.println(Ansi.bold(Ansi.cyan("  ╔══════════════════════════════════════════╗")));
        System.out.println(Ansi.bold(Ansi.cyan("  ║         Trade-J Replay Console           ║")));
        System.out.println(Ansi.bold(Ansi.cyan("  ╚══════════════════════════════════════════╝")));
        System.out.println();
        System.out.println("  Symbol: " + Ansi.bold(symbol));
        System.out.println("  Range:  " + from + " → " + to);
        System.out.println("  Speed:  " + initialSpeed + "x");
        System.out.println();
        System.out.println("  " + Ansi.dim("Commands: ") + "play | pause | step [N] | speed [Nx] | goto [time]");
        System.out.println("  " + Ansi.dim("          ") + "positions | orders | pnl | quit");
        System.out.println();

        long fromMs = parseTimestamp(from);
        long toMs = parseTimestamp(to);
        long currentTime = fromMs;
        boolean playing = false;
        int speed = initialSpeed;

        java.util.Scanner scanner = new java.util.Scanner(System.in);

        while (true) {
            // Show progress
            double progress = (double) (currentTime - fromMs) / Math.max(1, toMs - fromMs);
            int barWidth = 40;
            int filled = (int) (progress * barWidth);
            String bar = Ansi.green("█".repeat(filled)) + Ansi.dim("░".repeat(barWidth - filled));
            String status = playing ? Ansi.green("▶ Playing") : Ansi.yellow("⏸ Paused");

            System.out.printf("\r  %s  [%s] %.0f%%  Speed: %dx  Time: %s   ",
                    status, bar, progress * 100, speed, formatTimestamp(currentTime));

            if (!playing) {
                System.out.print("\n  replay> ");
                if (!scanner.hasNextLine()) break;
                String input = scanner.nextLine().trim().toLowerCase();

                if (input.isEmpty()) continue;

                switch (input.split("\\s+")[0]) {
                    case "play", "p" -> playing = true;
                    case "pause" -> playing = false;
                    case "step", "s" -> {
                        int steps = 1;
                        String[] parts = input.split("\\s+");
                        if (parts.length > 1) {
                            try { steps = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
                        }
                        currentTime = Math.min(currentTime + steps * 60_000L, toMs);
                        System.out.println("  " + Ansi.dim("Stepped " + steps + " event(s) → " + formatTimestamp(currentTime)));
                    }
                    case "speed" -> {
                        String[] parts = input.split("\\s+");
                        if (parts.length > 1) {
                            String speedStr = parts[1].replace("x", "");
                            try { speed = Integer.parseInt(speedStr); } catch (NumberFormatException ignored) {}
                        }
                        System.out.println("  " + Ansi.dim("Speed set to " + speed + "x"));
                    }
                    case "goto", "g" -> {
                        String[] parts = input.split("\\s+", 2);
                        if (parts.length > 1) {
                            currentTime = parseTimestamp(parts[1].trim());
                            System.out.println("  " + Ansi.dim("Jumped to " + formatTimestamp(currentTime)));
                        }
                    }
                    case "positions", "pos" -> { ops.brokerPositions(); }
                    case "orders" -> { ops.orders(); }
                    case "pnl" -> { ops.livePnl(); }
                    case "quit", "q", "exit" -> {
                        System.out.println("  Bye.");
                        return 0;
                    }
                    default -> System.out.println("  " + Ansi.red("Unknown: " + input));
                }
            } else {
                // Playing — advance time
                currentTime += speed * 1000L;
                if (currentTime >= toMs) {
                    currentTime = toMs;
                    playing = false;
                    System.out.println("\n  " + Ansi.green("✓ Replay complete"));
                }
                Thread.sleep(100);
            }
        }
        return 0;
    }

    private long parseTimestamp(String input) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException e) {
            try {
                return java.time.Instant.parse(input).toEpochMilli();
            } catch (Exception e2) {
                try {
                    return java.time.LocalDateTime.parse(input)
                            .atZone(java.time.ZoneId.of("Asia/Kolkata"))
                            .toInstant().toEpochMilli();
                } catch (Exception e3) {
                    return System.currentTimeMillis();
                }
            }
        }
    }

    private String formatTimestamp(long epochMs) {
        return java.time.Instant.ofEpochMilli(epochMs)
                .atZone(java.time.ZoneId.of("Asia/Kolkata"))
                .toLocalTime()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
}
