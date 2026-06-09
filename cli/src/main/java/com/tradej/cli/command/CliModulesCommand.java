package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import com.tradej.cli.output.RichTable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Command(name = "modules", description = "List all Gradle modules with directory and category")
public final class CliModulesCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli root;

    static final Pattern INCLUDE_PATTERN = Pattern.compile("include\\s+'([^']+)'");
    static final Pattern DIR_PATTERN = Pattern.compile("project\\(':[^']+'\\)\\.projectDir\\s*=\\s*file\\('([^']+)'\\)");

    @Override
    public Integer call() {
        List<ModuleEntry> modules = parseModules();

        if (root.json()) {
            printJson(modules);
            return 0;
        }

        System.out.println(Ansi.bold("\n  Gradle Modules\n"));

        RichTable table = RichTable.of("Module", "Directory", "Category")
                .rowFormatter(row -> {
                    String cat = row[2];
                    String colored = switch (cat) {
                        case "broker" -> Ansi.blue(cat);
                        case "trading" -> Ansi.green(cat);
                        case "data" -> Ansi.cyan(cat);
                        case "runtime" -> Ansi.magenta(cat);
                        case "pipeline" -> Ansi.yellow(cat);
                        default -> Ansi.dim(cat);
                    };
                    return new String[]{row[0], row[1], colored};
                });

        for (ModuleEntry m : modules) {
            table.addRow(m.name(), m.directory(), m.category());
        }
        table.print();

        System.out.println(Ansi.dim("  Total: " + modules.size() + " modules"));
        return 0;
    }

    static List<ModuleEntry> parseModules() {
        String workspaceRoot = System.getProperty("trade.workspace.root", ".");
        Path settingsFile = Path.of(workspaceRoot, "settings.gradle");
        if (!Files.exists(settingsFile)) {
            settingsFile = Path.of("settings.gradle");
        }
        if (!Files.exists(settingsFile)) {
            return List.of();
        }
        try {
            String content = Files.readString(settingsFile);
            return parseSettingsContent(content);
        } catch (IOException e) {
            return List.of();
        }
    }

    static List<ModuleEntry> parseSettingsContent(String content) {
        List<String> names = new ArrayList<>();
        List<String> dirs = new ArrayList<>();

        Matcher includeMatcher = INCLUDE_PATTERN.matcher(content);
        while (includeMatcher.find()) {
            names.add(includeMatcher.group(1));
        }

        Matcher dirMatcher = DIR_PATTERN.matcher(content);
        while (dirMatcher.find()) {
            dirs.add(dirMatcher.group(1));
        }

        List<ModuleEntry> modules = new ArrayList<>();
        for (int i = 0; i < names.size() && i < dirs.size(); i++) {
            String name = names.get(i);
            String dir = dirs.get(i);
            modules.add(new ModuleEntry(name, dir, categorize(dir)));
        }
        return modules;
    }

    static String categorize(String directory) {
        if (directory.startsWith("broker/")) return "broker";
        if (directory.startsWith("trading/")) return "trading";
        if (directory.startsWith("data/")) return "data";
        if (directory.startsWith("runtime/")) return "runtime";
        if (directory.startsWith("pipeline/")) return "pipeline";
        if (directory.startsWith("replay/")) return "replay";
        if (directory.startsWith("nodes/")) return "pipeline";
        if (directory.startsWith("research/")) return "research";
        if (directory.equals("core")) return "core";
        if (directory.equals("app")) return "app";
        if (directory.equals("cli")) return "cli";
        if (directory.equals("gateway")) return "gateway";
        if (directory.equals("composition")) return "composition";
        if (directory.equals("broker-gateway")) return "broker";
        if (directory.equals("architecture-test")) return "test";
        return "other";
    }

    private void printJson(List<ModuleEntry> modules) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < modules.size(); i++) {
            if (i > 0) json.append(",");
            var m = modules.get(i);
            json.append(String.format(
                    "{\"name\":\"%s\",\"directory\":\"%s\",\"category\":\"%s\"}",
                    m.name(), m.directory(), m.category()));
        }
        json.append("]");
        System.out.println(json);
    }

    record ModuleEntry(String name, String directory, String category) {}
}
