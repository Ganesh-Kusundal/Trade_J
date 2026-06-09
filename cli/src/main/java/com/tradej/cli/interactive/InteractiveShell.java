package com.tradej.cli.interactive;

import com.tradej.cli.CliContext;
import com.tradej.cli.CliOperations;
import com.tradej.cli.TradeCli;
import com.tradej.cli.config.AliasStore;
import com.tradej.cli.config.MacroStore;
import com.tradej.cli.config.SavedQueryStore;
import com.tradej.cli.output.Ansi;
import com.tradej.cli.output.CommandSuggester;
import org.jline.console.SystemRegistry;
import org.jline.console.impl.SystemRegistryImpl;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliCommands;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Interactive REPL shell using Picocli + JLine integration.
 *
 * <p>Provides:
 * <ul>
 *   <li>Command-based REPL — type {@code quote RELIANCE} directly</li>
 *   <li>Tab completion for all 80+ commands and subcommands</li>
 *   <li>Persistent command history in {@code ~/.tradej/history}</li>
 *   <li>Built-in {@code help} and {@code clear} commands</li>
 *   <li>Status bar showing broker, profile, and connection state</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   tradej> quote RELIANCE
 *   tradej> option-chain NIFTY --expiry 2026-06-26
 *   tradej> broker validate dhan
 *   tradej> portfolio summary
 *   tradej> help
 *   tradej> exit
 * </pre>
 */
public final class InteractiveShell {

    private static final String PROMPT = Ansi.bold(Ansi.cyan("tradej")) + "> ";

    private final CliContext context;
    private final CliOperations operations;
    private final StatusBar statusBar;
    private final AliasStore aliasStore;
    private final MacroStore macroStore;
    private final SavedQueryStore queryStore;

    public InteractiveShell(CliContext context, CliOperations operations) {
        this.context = context;
        this.operations = operations;
        this.statusBar = new StatusBar(context);
        this.aliasStore = AliasStore.load();
        this.macroStore = MacroStore.load();
        this.queryStore = SavedQueryStore.load();
    }

    public void run() throws IOException {
        Terminal terminal = buildTerminal();

        if ("dumb".equals(terminal.getType()) && System.console() == null) {
            operations.output().error(
                    "No interactive terminal detected. Use ./scripts/tradej from a real terminal, "
                            + "or run non-interactive commands (e.g. ./scripts/tradej status).");
            return;
        }

        // Build Picocli CommandLine with a fresh TradeCli root command.
        // Commands create their own CliContext via TradeCli.ops() using
        // --attach/--profile/--broker options or config file defaults.
        TradeCli tradeCli = new TradeCli();
        CommandLine commandLine = new CommandLine(tradeCli);
        commandLine.setExecutionExceptionHandler(new ReplExceptionHandler());

        // Build JLine components
        PicocliCommands picocliCommands = new PicocliCommands(commandLine);

        // Persistent history
        Path historyFile = historyPath();

        DefaultParser parser = new DefaultParser();
        parser.setEscapeChars(new char[]{});

        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(picocliCommands.compileCompleters())
                .parser(parser)
                .variable(LineReader.LIST_MAX, 50)
                .variable(LineReader.HISTORY_FILE, historyFile)
                .build();

        // System registry for built-in commands (help, clear, exit)
        SystemRegistry systemRegistry = new SystemRegistryImpl(parser, terminal, () -> historyFile, null);
        systemRegistry.setCommandRegistries(picocliCommands);

        // Print welcome banner
        printBanner(terminal);

        // REPL loop
        while (true) {
            String line;
            try {
                line = reader.readLine(PROMPT);
            } catch (UserInterruptException e) {
                // Ctrl+C — ignore and continue
                continue;
            } catch (EndOfFileException e) {
                // Ctrl+D — exit
                operations.output().println("Bye.");
                return;
            }

            if (line == null || line.isBlank()) {
                continue;
            }

            String trimmed = line.trim();

            // Handle built-in REPL commands
            if ("exit".equalsIgnoreCase(trimmed) || "quit".equalsIgnoreCase(trimmed)) {
                operations.output().println("Bye.");
                return;
            }

            if ("clear".equalsIgnoreCase(trimmed)) {
                terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
                terminal.flush();
                continue;
            }

            // Handle alias management commands
            if (trimmed.startsWith("alias ")) {
                handleAliasCommand(trimmed.substring(6).trim());
                continue;
            }

            // Handle macro commands
            if (trimmed.startsWith("macro ")) {
                handleMacroCommand(trimmed.substring(6).trim());
                continue;
            }

            // Handle query save/load/list/remove
            if (trimmed.startsWith("query save ") || trimmed.startsWith("query load ")
                    || trimmed.equals("query list") || trimmed.startsWith("query remove ")) {
                handleQueryCommand(trimmed.substring(6).trim());
                continue;
            }

            if ("help".equalsIgnoreCase(trimmed)) {
                printCategorizedHelp();
                continue;
            }

            if (trimmed.startsWith("help ")) {
                String topic = trimmed.substring(5).trim().split("\\s+")[0];
                try {
                    TradeCli cli = new TradeCli();
                    CommandLine sub = new CommandLine(cli).getSubcommands().get("help");
                    if (sub != null) {
                        sub.execute(topic);
                    } else {
                        operations.output().println(Ansi.red("  Unknown topic: " + topic));
                        operations.output().println(Ansi.dim("  Run 'help' for category listing."));
                    }
                } catch (Exception e) {
                    operations.output().println(Ansi.red("  Unknown topic: " + topic));
                    operations.output().println(Ansi.dim("  Run 'help' for category listing."));
                }
                continue;
            }

            // Expand aliases before execution
            String expanded = aliasStore.expand(trimmed);

            // Check if it's a macro
            String[] firstToken = expanded.split("\\s+", 2);
            if (macroStore.has(firstToken[0])) {
                java.util.List<String> macroCommands = macroStore.get(firstToken[0]);
                if (macroCommands != null) {
                    operations.output().println(Ansi.dim("  Running macro: " + firstToken[0] + " (" + macroCommands.size() + " commands)"));
                    for (String cmd : macroCommands) {
                        operations.output().println(Ansi.dim("  > ") + cmd);
                        try { systemRegistry.execute(cmd); } catch (Exception ignored) {}
                    }
                    continue;
                }
            }

            // Execute through Picocli
            try {
                systemRegistry.execute(expanded);
            } catch (Exception e) {
                // Errors are handled by ReplExceptionHandler
            }
        }
    }

    private static Terminal buildTerminal() throws IOException {
        if (System.console() != null) {
            return TerminalBuilder.builder()
                    .system(true)
                    .color(true)
                    .build();
        }
        return TerminalBuilder.builder()
                .dumb(true)
                .streams(System.in, System.out)
                .build();
    }

    private void printBanner(Terminal terminal) {
        operations.output().println("");
        operations.output().println("  " + statusBar.renderHeader());
        operations.output().println("  " + Ansi.dim("Type ") + Ansi.bold("help") + Ansi.dim(" for commands, ") + Ansi.bold("TAB") + Ansi.dim(" for completion, ") + Ansi.bold("exit") + Ansi.dim(" to quit"));
        operations.output().println("");
    }

    private static Path historyPath() {
        String home = System.getProperty("user.home", ".");
        Path tradejDir = Paths.get(home, ".tradej");
        try {
            java.nio.file.Files.createDirectories(tradejDir);
        } catch (IOException ignored) {
        }
        return tradejDir.resolve("history");
    }

    // ── Alias/Macro/Query management ──────────────────────────────────

    private void handleAliasCommand(String args) {
        String[] parts = args.split("\\s+", 3);
        if (parts.length == 0 || "list".equalsIgnoreCase(parts[0])) {
            var all = aliasStore.all();
            if (all.isEmpty()) {
                operations.output().println("  " + Ansi.dim("No aliases defined. Use: alias add <name> <command>"));
            } else {
                operations.output().println("  " + Ansi.bold("Aliases:"));
                all.forEach((name, cmd) -> operations.output().println("    " + Ansi.cyan(name) + " → " + cmd));
            }
        } else if ("add".equalsIgnoreCase(parts[0]) && parts.length >= 3) {
            aliasStore.add(parts[1], parts[2]);
            operations.output().println("  " + Ansi.green("✓") + " Alias " + Ansi.cyan(parts[1]) + " → " + parts[2]);
        } else if ("remove".equalsIgnoreCase(parts[0]) && parts.length >= 2) {
            if (aliasStore.remove(parts[1])) {
                operations.output().println("  " + Ansi.green("✓") + " Removed alias " + parts[1]);
            } else {
                operations.output().println("  " + Ansi.red("✗") + " Alias not found: " + parts[1]);
            }
        } else {
            operations.output().println("  Usage: alias list | alias add <name> <command> | alias remove <name>");
        }
    }

    private void handleMacroCommand(String args) {
        String[] parts = args.split("\\s+", 2);
        if (parts.length == 0 || "list".equalsIgnoreCase(parts[0])) {
            var all = macroStore.all();
            if (all.isEmpty()) {
                operations.output().println("  " + Ansi.dim("No macros defined. Use: macro add <name> \"cmd1\" \"cmd2\" ..."));
            } else {
                operations.output().println("  " + Ansi.bold("Macros:"));
                all.forEach((name, cmds) -> operations.output().println("    " + Ansi.cyan(name) + " → " + String.join(" | ", cmds)));
            }
        } else if ("add".equalsIgnoreCase(parts[0]) && parts.length >= 2) {
            // Parse quoted commands: macro add morning "quote RELIANCE" "balance"
            String rest = parts[1];
            String name = rest.split("\\s+")[0];
            String cmdsStr = rest.substring(name.length()).trim();
            java.util.List<String> commands = new java.util.ArrayList<>();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(cmdsStr);
            while (m.find()) commands.add(m.group(1));
            if (commands.isEmpty()) commands.add(cmdsStr);
            macroStore.add(name, commands);
            operations.output().println("  " + Ansi.green("✓") + " Macro " + Ansi.cyan(name) + " (" + commands.size() + " commands)");
        } else if ("remove".equalsIgnoreCase(parts[0]) && parts.length >= 2) {
            String name = parts[1].split("\\s+")[0];
            if (macroStore.remove(name)) {
                operations.output().println("  " + Ansi.green("✓") + " Removed macro " + name);
            } else {
                operations.output().println("  " + Ansi.red("✗") + " Macro not found: " + name);
            }
        } else {
            operations.output().println("  Usage: macro list | macro add <name> \"cmd1\" \"cmd2\" | macro remove <name>");
        }
    }

    private void handleQueryCommand(String args) {
        if ("list".equalsIgnoreCase(args)) {
            var all = queryStore.all();
            if (all.isEmpty()) {
                operations.output().println("  " + Ansi.dim("No saved queries. Use: query save <name> \"SELECT ...\""));
            } else {
                operations.output().println("  " + Ansi.bold("Saved queries:"));
                all.forEach((name, sql) -> operations.output().println("    " + Ansi.cyan(name) + ": " + Ansi.dim(sql.substring(0, Math.min(sql.length(), 60)) + (sql.length() > 60 ? "..." : ""))));
            }
        } else if (args.startsWith("save ")) {
            String rest = args.substring(5).trim();
            String name = rest.split("\\s+")[0];
            String sql = rest.substring(name.length()).trim();
            if (sql.startsWith("\"") && sql.endsWith("\"")) sql = sql.substring(1, sql.length() - 1);
            queryStore.save(name, sql);
            operations.output().println("  " + Ansi.green("✓") + " Saved query " + Ansi.cyan(name));
        } else if (args.startsWith("load ")) {
            String name = args.substring(5).trim();
            String sql = queryStore.get(name);
            if (sql != null) {
                operations.output().println("  " + Ansi.dim("Loaded: ") + sql);
            } else {
                operations.output().println("  " + Ansi.red("✗") + " Query not found: " + name);
            }
        } else if (args.startsWith("remove ")) {
            String name = args.substring(7).trim();
            if (queryStore.remove(name)) {
                operations.output().println("  " + Ansi.green("✓") + " Removed query " + name);
            } else {
                operations.output().println("  " + Ansi.red("✗") + " Query not found: " + name);
            }
        } else {
            operations.output().println("  Usage: query list | query save <name> \"SQL\" | query load <name> | query remove <name>");
        }
    }

    // ── Categorized help ─────────────────────────────────────────────

    private void printCategorizedHelp() {
        operations.output().println("");
        operations.output().println(Ansi.bold("  Trade-J Commands\n"));

        operations.output().println(Ansi.yellow("  System"));
        for (String cmd : List.of("exit", "help", "clear", "alias", "macro", "query")) {
            operations.output().println("    " + Ansi.cyan(cmd));
        }

        operations.output().println("");
        operations.output().println(Ansi.yellow("  Broker / Market"));
        for (String cmd : List.of("quote", "ltp", "depth", "ohlc", "candles", "chain", "expiries",
                "strike", "margin", "rolling-option", "broker-validate", "brokers", "catalog")) {
            operations.output().println("    " + Ansi.cyan(cmd));
        }

        operations.output().println("");
        operations.output().println(Ansi.yellow("  Portfolio / P&L"));
        for (String cmd : List.of("balance", "holdings", "broker-positions", "live-pnl",
                "orders", "positions", "read-model", "orderbook", "trades", "order",
                "place", "cancel", "modify")) {
            operations.output().println("    " + Ansi.cyan(cmd));
        }

        operations.output().println("");
        operations.output().println(Ansi.yellow("  Data / Analytics"));
        for (String cmd : List.of("data", "historical", "replay", " replay-console",
                " analytics", "options-scan", "scan")) {
            operations.output().println("    " + Ansi.cyan(cmd));
        }

        operations.output().println("");
        operations.output().println(Ansi.yellow("  Strategy / Backtest"));
        for (String cmd : List.of("strategies", "backtest", "screener", "indicators",
                "compute", "square-off", "bracket", "gtt", "futures", "alerts")) {
            operations.output().println("    " + Ansi.cyan(cmd));
        }

        operations.output().println("");
        operations.output().println(Ansi.yellow("  Runtime / Pipeline"));
        for (String cmd : List.of("status", "runtime", "pipeline", "summary",
                "stream-read-model", "kill-switch", "reconcile", "risk-config")) {
            operations.output().println("    " + Ansi.cyan(cmd));
        }

        operations.output().println("");
        operations.output().println(Ansi.yellow("  Download / Universe"));
        for (String cmd : List.of("download", "universe", "equity", "parquet")) {
            operations.output().println("    " + Ansi.cyan(cmd));
        }

        operations.output().println("");
        operations.output().println(Ansi.yellow("  Maintenance"));
        for (String cmd : List.of("token", "test", "doctor", "readiness", "regression",
                "coverage", "certify", "docs", "modules", "plugins", "datasources",
                "flows", "architecture", "capabilities", "dashboard", "monitor",
                "commands", "query")) {
            operations.output().println("    " + Ansi.cyan(cmd));
        }

        operations.output().println("");
        operations.output().println(Ansi.dim("  Run 'help <topic>' for topic details, or 'tradej <command> --help' for usage."));
        operations.output().println("");
    }

    /**
     * Exception handler that prints errors in the REPL without exiting.
     */
    private static final class ReplExceptionHandler implements CommandLine.IExecutionExceptionHandler {
        @Override
        public int handleExecutionException(
                Exception ex,
                CommandLine commandLine,
                CommandLine.ParseResult parseResult
        ) {
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            // Check if it's an "unknown command" error and suggest alternatives
            if (msg.contains("Unknown option") || msg.contains("is not a")) {
                String input = parseResult != null && parseResult.commandSpec() != null
                        ? parseResult.commandSpec().name() : "";
                if (!input.isEmpty()) {
                    var commands = commandLine.getSubcommands().keySet();
                    commandLine.getErr().println(CommandSuggester.formatSuggestion(input, commands));
                    return 1;
                }
            }
            commandLine.getErr().println(commandLine.getColorScheme().errorText(msg));
            return 1;
        }
    }
}
