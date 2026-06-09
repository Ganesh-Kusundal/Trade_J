package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import com.tradej.cli.output.RichTable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "commands", description = "List all registered CLI commands with descriptions")
public final class CliCommandsCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli root;

    @Option(names = "--filter", description = "Filter commands by name or description")
    String filter;

    @Option(names = "--tree", description = "Show commands as indented tree")
    boolean tree;

    @Override
    public Integer call() {
        CommandLine cmd = new CommandLine(new TradeCli());
        List<CommandEntry> entries = new ArrayList<>();
        collectCommands(cmd, "", entries);

        if (filter != null && !filter.isBlank()) {
            String lowerFilter = filter.toLowerCase();
            entries = entries.stream()
                    .filter(e -> e.path().toLowerCase().contains(lowerFilter)
                            || e.description().toLowerCase().contains(lowerFilter))
                    .toList();
        }

        if (root.json()) {
            printJson(entries);
            return 0;
        }

        if (tree) {
            printTree(entries);
        } else {
            printTable(entries);
        }

        System.out.println(Ansi.dim("  Total: " + entries.size() + " commands"));
        return 0;
    }

    private void collectCommands(CommandLine cmd, String prefix, List<CommandEntry> entries) {
        for (Map.Entry<String, CommandLine> entry : cmd.getSubcommands().entrySet()) {
            String name = entry.getKey();
            CommandLine sub = entry.getValue();
            String path = prefix.isEmpty() ? name : prefix + " " + name;
            String[] descParts = sub.getCommandSpec().usageMessage().description();
            String desc = descParts != null && descParts.length > 0 ? String.join(" ", descParts) : "";

            if (!desc.startsWith("picocli")) {
                entries.add(new CommandEntry(path, desc));
            }
            collectCommands(sub, path, entries);
        }
    }

    private void printTable(List<CommandEntry> entries) {
        System.out.println(Ansi.bold("\n  Trade-J Command Reference\n"));
        RichTable table = RichTable.of("Command", "Description");
        for (CommandEntry e : entries) {
            table.addRow("tradej " + e.path(), e.description());
        }
        table.print();
    }

    private void printTree(List<CommandEntry> entries) {
        System.out.println(Ansi.bold("\n  tradej"));
        for (CommandEntry e : entries) {
            int depth = e.path().split(" ").length;
            String indent = "  ".repeat(depth + 1);
            String name = e.path().substring(e.path().lastIndexOf(' ') + 1);
            System.out.printf("%s%s %s%n", indent,
                    Ansi.cyan(name),
                    Ansi.dim(e.description()));
        }
    }

    private void printJson(List<CommandEntry> entries) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) json.append(",");
            var e = entries.get(i);
            json.append(String.format(
                    "{\"path\":\"tradej %s\",\"description\":\"%s\"}",
                    e.path(), e.description().replace("\"", "\\\"")));
        }
        json.append("]");
        System.out.println(json);
    }

    record CommandEntry(String path, String description) {}
}
