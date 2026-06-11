package com.tradej.cli.command;

import com.tradej.broker.api.spi.BrokerDescriptor;
import com.tradej.broker.api.spi.BrokerProvider;
import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import com.tradej.cli.output.RichTable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;

@Command(name = "brokers", description = "Discover all registered broker plugins via SPI")
public final class CliBrokersCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli root;

    @Override
    public Integer call() {
        List<BrokerProvider> providers = new ArrayList<>();
        ServiceLoader.load(BrokerProvider.class).forEach(providers::add);

        if (root.json()) {
            printJson(providers);
            return 0;
        }

        System.out.println(Ansi.bold("\n  Broker Plugins (SPI Discovery)\n"));

        RichTable table = RichTable.of("Source", "Display Name", "Version", "Enabled", "Segments", "Capabilities")
                .rowFormatter(row -> {
                    String enabled = row[3];
                    return new String[]{
                            row[0], row[1], row[2],
                            "true".equals(enabled) ? Ansi.green("YES") : Ansi.red("NO"),
                            row[4], row[5]
                    };
                });

        for (BrokerProvider provider : providers) {
            BrokerDescriptor desc = provider.descriptor();
            table.addRow(
                    provider.source().name(),
                    provider.displayName(),
                    provider.version(),
                    String.valueOf(provider.isEnabled()),
                    String.join(", ", desc.supportedSegments()),
                    desc.supportedCount() + "/" + desc.totalCount()
            );
        }
        table.print();

        System.out.println(Ansi.dim("  Total: " + providers.size() + " broker providers"));
        return 0;
    }

    private void printJson(List<BrokerProvider> providers) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < providers.size(); i++) {
            if (i > 0) json.append(",");
            BrokerProvider p = providers.get(i);
            BrokerDescriptor d = p.descriptor();
            json.append(String.format(
                    "{\"source\":\"%s\",\"displayName\":\"%s\",\"version\":\"%s\",\"enabled\":%s," +
                    "\"segments\":[%s],\"supportedCapabilities\":%d,\"totalCapabilities\":%d}",
                    p.source().name(), p.displayName(), p.version(), p.isEnabled(),
                    quotedJoin(d.supportedSegments()), d.supportedCount(), d.totalCount()));
        }
        json.append("]");
        System.out.println(json);
    }

    private static String quotedJoin(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(items.get(i)).append("\"");
        }
        return sb.toString();
    }
}
