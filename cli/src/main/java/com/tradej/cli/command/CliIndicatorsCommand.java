package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import com.tradej.cli.output.RichTable;
import com.tradej.indicators.spi.IndicatorProvider;
import com.tradej.indicators.spi.IndicatorRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "indicators", description = "Discover all registered indicator providers via SPI")
public final class CliIndicatorsCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli root;

    @Override
    public Integer call() {
        IndicatorRegistry registry = IndicatorRegistry.discover();
        Map<String, IndicatorProvider> all = registry.all();

        if (root.json()) {
            printJson(all);
            return 0;
        }

        System.out.println(Ansi.bold("\n  Indicator Providers (SPI Discovery)\n"));

        RichTable table = RichTable.of("Name", "Display Name", "Min Period", "Version");
        for (Map.Entry<String, IndicatorProvider> entry : all.entrySet()) {
            IndicatorProvider p = entry.getValue();
            table.addRow(p.name(), p.displayName(), String.valueOf(p.minPeriod()), p.version());
        }
        table.print();

        System.out.println(Ansi.dim("  Total: " + all.size() + " indicator providers"));
        return 0;
    }

    private void printJson(Map<String, IndicatorProvider> all) {
        StringBuilder json = new StringBuilder("[");
        int i = 0;
        for (IndicatorProvider p : all.values()) {
            if (i > 0) json.append(",");
            json.append(String.format(
                    "{\"name\":\"%s\",\"displayName\":\"%s\",\"minPeriod\":%d,\"version\":\"%s\"}",
                    p.name(), p.displayName(), p.minPeriod(), p.version()));
            i++;
        }
        json.append("]");
        System.out.println(json);
    }
}
