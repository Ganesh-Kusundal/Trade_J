package com.tradej.cli.command;

import com.tradej.brokergateway.spi.BrokerProvider;
import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import com.tradej.cli.output.RichTable;
import com.tradej.core.domain.event.EventCatalogEntry;
import com.tradej.indicators.spi.IndicatorRegistry;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;

@Command(name = "capabilities", description = "Aggregated platform capability summary across all subsystems")
public final class CliCapabilitiesCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli root;

    @Option(names = "--evidence", description = "Show certification evidence (last certified, latency)")
    boolean evidence;

    @Override
    public Integer call() {
        List<CapabilityRow> rows = collectCapabilities();

        if (root.json()) {
            printJson(rows);
            return 0;
        }

        System.out.println(Ansi.bold("\n  Trade-J Platform Capabilities\n"));

        RichTable table = RichTable.of("Category", "Subsystem", "Count", "Status")
                .rowFormatter(row -> {
                    String status = row[3];
                    return new String[]{
                            row[0], row[1], row[2],
                            "ACTIVE".equals(status) || "ENFORCED".equals(status)
                                    ? Ansi.green(status) : Ansi.dim(status)
                    };
                });

        for (CapabilityRow r : rows) {
            table.addRow(r.category(), r.subsystem(), String.valueOf(r.count()), r.status());
        }
        table.print();

        if (evidence) {
            printCertificationEvidence();
        }

        return 0;
    }

    private List<CapabilityRow> collectCapabilities() {
        List<CapabilityRow> rows = new ArrayList<>();

        int brokerCount = 0;
        for (BrokerProvider ignored : ServiceLoader.load(BrokerProvider.class)) brokerCount++;
        rows.add(new CapabilityRow("Broker", "BrokerProvider SPI", brokerCount, brokerCount > 0 ? "ACTIVE" : "NONE"));

        int indicatorCount = IndicatorRegistry.discover().size();
        rows.add(new CapabilityRow("Indicator", "IndicatorProvider SPI", indicatorCount, indicatorCount > 0 ? "ACTIVE" : "NONE"));

        CommandLine cmd = new CommandLine(new TradeCli());
        int commandCount = countSubcommands(cmd);
        rows.add(new CapabilityRow("CLI", "Picocli Commands", commandCount, "ACTIVE"));

        List<EventCatalogEntry> events = CliEventsCommand.discoverEvents();
        rows.add(new CapabilityRow("Events", "DomainEvent types", events.size(), "ACTIVE"));

        List<CliModulesCommand.ModuleEntry> modules = CliModulesCommand.parseModules();
        rows.add(new CapabilityRow("Modules", "Gradle modules", modules.size(), "ACTIVE"));

        List<CliPluginsCommand.PluginEntry> plugins = CliPluginsCommand.discoverPlugins();
        rows.add(new CapabilityRow("Plugins", "SPI registrations", plugins.size(), "ACTIVE"));

        rows.add(new CapabilityRow("Patterns", "Design patterns", 10, "ENFORCED"));
        rows.add(new CapabilityRow("Architecture", "ArchUnit rules", 36, "ENFORCED"));

        return rows;
    }

    private static int countSubcommands(CommandLine cmd) {
        int count = 0;
        for (CommandLine sub : cmd.getSubcommands().values()) {
            count++;
            count += countSubcommands(sub);
        }
        return count;
    }

    private void printJson(List<CapabilityRow> rows) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) json.append(",");
            var r = rows.get(i);
            json.append(String.format(
                    "{\"category\":\"%s\",\"subsystem\":\"%s\",\"count\":%d,\"status\":\"%s\"}",
                    r.category(), r.subsystem(), r.count(), r.status()));
        }
        json.append("]");
        System.out.println(json);
    }

    private void printCertificationEvidence() {
        com.tradej.brokergateway.certification.CertificationArtifactStore store =
                new com.tradej.brokergateway.certification.CertificationArtifactStore();

        System.out.println(Ansi.bold("\n  Certification Evidence\n"));

        for (com.tradej.brokergateway.result.BrokerSource source :
                com.tradej.brokergateway.result.BrokerSource.values()) {
            try {
                int count = store.countArtifacts(source);
                if (count > 0) {
                    java.util.List<com.tradej.brokergateway.certification.CertificationArtifact> artifacts =
                            store.loadAll(source);
                    long pass = artifacts.stream().filter(com.tradej.brokergateway.certification.CertificationArtifact::isPass).count();
                    long fail = artifacts.size() - pass;
                    java.time.Instant latest = artifacts.stream()
                            .map(com.tradej.brokergateway.certification.CertificationArtifact::certifiedAt)
                            .max(java.time.Instant::compareTo)
                            .orElse(null);
                    double avgLatency = artifacts.stream()
                            .mapToLong(com.tradej.brokergateway.certification.CertificationArtifact::latencyMs)
                            .average().orElse(0);

                    System.out.printf("  %s: %d artifacts (%d pass, %d fail)%n",
                            Ansi.bold(source.name()), count, pass, fail);
                    if (latest != null) {
                        System.out.printf("    Last certified: %s%n", latest);
                    }
                    System.out.printf("    Avg latency: %.0fms%n", avgLatency);
                }
            } catch (java.io.IOException e) {
                System.out.printf("  %s: no artifacts%n", source.name());
            }
        }
        System.out.println();
    }

    record CapabilityRow(String category, String subsystem, int count, String status) {}
}
