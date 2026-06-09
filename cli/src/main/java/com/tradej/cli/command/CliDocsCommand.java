package com.tradej.cli.command;

import com.tradej.brokergateway.spi.BrokerProvider;
import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import com.tradej.core.domain.event.EventCatalogEntry;
import com.tradej.indicators.spi.IndicatorProvider;
import com.tradej.indicators.spi.IndicatorRegistry;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;

@Command(name = "docs", description = "Generate platform documentation",
        subcommands = {CliDocsCommand.GenerateCmd.class})
public final class CliDocsCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli root;

    @Override
    public Integer call() {
        System.out.println(Ansi.bold("\n  Trade-J Documentation Generator\n"));
        System.out.println("  Usage: tradej docs generate [--format markdown|html|json] [--output docs/]");
        return 0;
    }

    @Command(name = "generate", description = "Generate complete platform documentation")
    static final class GenerateCmd implements Callable<Integer> {
        @ParentCommand CliDocsCommand parent;

        @Option(names = "--format", defaultValue = "markdown", description = "Output format: markdown, html, json")
        String format;

        @Option(names = "--output", defaultValue = "docs", description = "Output directory")
        String outputDir;

        @Override
        public Integer call() throws IOException {
            Path outDir = Path.of(outputDir);
            Files.createDirectories(outDir);

            switch (format.toLowerCase()) {
                case "markdown" -> generateMarkdown(outDir);
                case "json" -> generateJson(outDir);
                case "html" -> generateHtml(outDir);
                default -> System.err.println("Unknown format: " + format);
            }

            System.out.println(Ansi.green("  Documentation generated to: " + outDir.toAbsolutePath()));
            return 0;
        }

        private void generateMarkdown(Path outDir) throws IOException {
            generateCliReference(outDir);
            generateEventCatalog(outDir);
            generateBrokerCapabilities(outDir);
            generateModuleIndex(outDir);
            generateArchitectureOverview(outDir);
            generateFlowDocs(outDir);
        }

        private void generateCliReference(Path outDir) throws IOException {
            StringBuilder md = new StringBuilder();
            md.append("# Trade-J CLI Reference\n\n");
            md.append("Generated: ").append(LocalDate.now()).append("\n\n");

            CommandLine cmd = new CommandLine(new TradeCli());
            md.append("## Commands\n\n");

            List<String[]> entries = new ArrayList<>();
            collectCommands(cmd, "", entries);

            md.append("| Command | Description |\n");
            md.append("|---------|-------------|\n");
            for (String[] entry : entries) {
                md.append("| `tradej ").append(entry[0]).append("` | ").append(entry[1]).append(" |\n");
            }

            Files.writeString(outDir.resolve("cli-reference.md"), md);
            System.out.println("  Generated: cli-reference.md (" + entries.size() + " commands)");
        }

        private void collectCommands(CommandLine cmd, String prefix, List<String[]> entries) {
            for (Map.Entry<String, CommandLine> entry : cmd.getSubcommands().entrySet()) {
                String name = entry.getKey();
                CommandLine sub = entry.getValue();
                String path = prefix.isEmpty() ? name : prefix + " " + name;
                String[] descParts = sub.getCommandSpec().usageMessage().description();
                String desc = descParts != null && descParts.length > 0 ? String.join(" ", descParts) : "";
                if (!desc.startsWith("picocli")) {
                    entries.add(new String[]{path, desc});
                }
                collectCommands(sub, path, entries);
            }
        }

        private void generateEventCatalog(Path outDir) throws IOException {
            List<EventCatalogEntry> events = CliEventsCommand.discoverEvents();
            StringBuilder md = new StringBuilder();
            md.append("# Trade-J Event Catalog\n\n");
            md.append("Generated: ").append(LocalDate.now()).append("\n\n");
            md.append("| Event | Category | Schema | Package |\n");
            md.append("|-------|----------|--------|----------|\n");
            for (EventCatalogEntry e : events) {
                md.append("| ").append(e.name())
                        .append(" | ").append(e.category())
                        .append(" | ").append(e.schemaVersion())
                        .append(" | `").append(e.packageName()).append("` |\n");
            }
            md.append("\nTotal: ").append(events.size()).append(" event types\n");

            Files.writeString(outDir.resolve("event-catalog.md"), md);
            System.out.println("  Generated: event-catalog.md (" + events.size() + " events)");
        }

        private void generateBrokerCapabilities(Path outDir) throws IOException {
            List<BrokerProvider> providers = new ArrayList<>();
            ServiceLoader.load(BrokerProvider.class).forEach(providers::add);

            StringBuilder md = new StringBuilder();
            md.append("# Trade-J Broker Capabilities\n\n");
            md.append("Generated: ").append(LocalDate.now()).append("\n\n");

            for (BrokerProvider provider : providers) {
                var desc = provider.descriptor();
                md.append("## ").append(provider.displayName()).append("\n\n");
                md.append("- **Source**: ").append(provider.source()).append("\n");
                md.append("- **Version**: ").append(provider.version()).append("\n");
                md.append("- **Segments**: ").append(String.join(", ", desc.supportedSegments())).append("\n\n");
                md.append("| Capability | Supported | Category |\n");
                md.append("|------------|-----------|----------|\n");
                for (var entry : desc.capabilities().entrySet()) {
                    String cat = desc.metadataFor(entry.getKey()).category();
                    md.append("| ").append(entry.getKey())
                            .append(" | ").append(entry.getValue() ? "YES" : "NO")
                            .append(" | ").append(cat).append(" |\n");
                }
                md.append("\n");
            }

            Files.writeString(outDir.resolve("broker-capabilities.md"), md);
            System.out.println("  Generated: broker-capabilities.md (" + providers.size() + " brokers)");
        }

        private void generateModuleIndex(Path outDir) throws IOException {
            List<CliModulesCommand.ModuleEntry> modules = CliModulesCommand.parseModules();
            StringBuilder md = new StringBuilder();
            md.append("# Trade-J Module Index\n\n");
            md.append("Generated: ").append(LocalDate.now()).append("\n\n");
            md.append("| Module | Directory | Category |\n");
            md.append("|--------|-----------|----------|\n");
            for (var m : modules) {
                md.append("| `").append(m.name()).append("` | `").append(m.directory())
                        .append("` | ").append(m.category()).append(" |\n");
            }
            md.append("\nTotal: ").append(modules.size()).append(" modules\n");

            Files.writeString(outDir.resolve("module-index.md"), md);
            System.out.println("  Generated: module-index.md (" + modules.size() + " modules)");
        }

        private void generateArchitectureOverview(Path outDir) throws IOException {
            StringBuilder md = new StringBuilder();
            md.append("# Trade-J Architecture Overview\n\n");
            md.append("Generated: ").append(LocalDate.now()).append("\n\n");

            md.append("## Design Patterns\n\n");
            md.append("| Pattern | Usage | Enforced By |\n");
            md.append("|---------|-------|-------------|\n");
            md.append("| Command | TradingCommand / CommandHandler / CommandResult | DesignPatternArchitectureTest |\n");
            md.append("| Chain of Responsibility | RiskCheckChain (kill_switch, daily_loss, position_limit) | DesignPatternArchitectureTest |\n");
            md.append("| Strategy (SPI) | IndicatorProvider, BrokerProvider | SpringFreeArchitectureTest |\n");
            md.append("| Adapter | IBrokerConnection per broker | ModuleBoundaryArchitectureTest |\n");
            md.append("| Registry | 9 registries across 6 modules | CodeQualityArchitectureTest |\n");
            md.append("| Decorator | ObservableMarketDataProvider, ObservableOrderCommand | — |\n");
            md.append("| State | OrderStateMachine (table-driven) | — |\n");
            md.append("| Event-Driven | DisruptorEventBus (LMAX, 8192 ring buffer) | — |\n");
            md.append("| Specification | ScanCriterion (composable AND/OR) | — |\n");
            md.append("| CQRS | EventStore → ReadModelStore | — |\n");

            md.append("\n## Architecture Constraints\n\n");
            md.append("| Test Class | Rules | Purpose |\n");
            md.append("|------------|-------|---------|\n");
            md.append("| SpringFreeArchitectureTest | ~5 | Domain modules must not import Spring |\n");
            md.append("| ModuleBoundaryArchitectureTest | ~6 | Core must not depend on outer modules |\n");
            md.append("| DesignPatternArchitectureTest | ~21 | Pattern enforcement |\n");
            md.append("| CodeQualityArchitectureTest | ~2 | God class limit, broker leak prevention |\n");
            md.append("| ProfileIsolationArchitectureTest | ~2 | Clock profile isolation |\n");

            Files.writeString(outDir.resolve("architecture-overview.md"), md);
            System.out.println("  Generated: architecture-overview.md");
        }

        private void generateFlowDocs(Path outDir) throws IOException {
            StringBuilder md = new StringBuilder();
            md.append("# Trade-J Runtime Flows\n\n");
            md.append("Generated: ").append(LocalDate.now()).append("\n\n");

            for (var entry : CliFlowsCommand.FLOWS.entrySet()) {
                var flow = entry.getValue();
                md.append("## ").append(flow.title()).append("\n\n");
                md.append("**Entry point**: ").append(flow.entryPoint()).append("\n\n");
                md.append("```\n");
                for (String step : flow.steps()) {
                    md.append(step).append("\n");
                }
                md.append("```\n\n");
            }

            Files.writeString(outDir.resolve("runtime-flows.md"), md);
            System.out.println("  Generated: runtime-flows.md (" + CliFlowsCommand.FLOWS.size() + " flows)");
        }

        private void generateJson(Path outDir) throws IOException {
            List<EventCatalogEntry> events = CliEventsCommand.discoverEvents();
            List<CliModulesCommand.ModuleEntry> modules = CliModulesCommand.parseModules();

            StringBuilder json = new StringBuilder("{");
            json.append("\"generated\":\"").append(LocalDate.now()).append("\",");
            json.append("\"events\":").append(events.size()).append(",");
            json.append("\"modules\":").append(modules.size()).append(",");
            json.append("\"patterns\":10,");
            json.append("\"flows\":").append(CliFlowsCommand.FLOWS.size());
            json.append("}");

            Files.writeString(outDir.resolve("platform-summary.json"), json);
            System.out.println("  Generated: platform-summary.json");
        }

        private void generateHtml(Path outDir) throws IOException {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n<html><head><title>Trade-J Platform Documentation</title>\n");
            html.append("<style>body{font-family:monospace;max-width:960px;margin:0 auto;padding:20px;}\n");
            html.append("table{border-collapse:collapse;width:100%;}\n");
            html.append("th,td{border:1px solid #ddd;padding:8px;text-align:left;}\n");
            html.append("th{background:#f4f4f4;}</style></head><body>\n");
            html.append("<h1>Trade-J Platform Documentation</h1>\n");
            html.append("<p>Generated: ").append(LocalDate.now()).append("</p>\n");

            List<EventCatalogEntry> events = CliEventsCommand.discoverEvents();
            html.append("<h2>Event Catalog (").append(events.size()).append(" types)</h2>\n");
            html.append("<table><tr><th>Event</th><th>Category</th><th>Schema</th></tr>\n");
            for (EventCatalogEntry e : events) {
                html.append("<tr><td>").append(e.name()).append("</td><td>")
                        .append(e.category()).append("</td><td>")
                        .append(e.schemaVersion()).append("</td></tr>\n");
            }
            html.append("</table>\n");

            html.append("<h2>Runtime Flows</h2>\n");
            for (var entry : CliFlowsCommand.FLOWS.entrySet()) {
                var flow = entry.getValue();
                html.append("<h3>").append(flow.title()).append("</h3>\n");
                html.append("<p>Entry point: ").append(flow.entryPoint()).append("</p>\n");
                html.append("<ol>\n");
                for (String step : flow.steps()) {
                    html.append("<li>").append(step.replaceAll("^\\d+\\.\\s*", "")).append("</li>\n");
                }
                html.append("</ol>\n");
            }

            html.append("</body></html>");
            Files.writeString(outDir.resolve("platform-docs.html"), html);
            System.out.println("  Generated: platform-docs.html");
        }
    }
}
