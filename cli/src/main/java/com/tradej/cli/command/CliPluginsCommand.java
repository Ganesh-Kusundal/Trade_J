package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import com.tradej.cli.output.RichTable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "plugins", description = "Discover all SPI plugin registrations on the classpath")
public final class CliPluginsCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli root;

    private static final String[] KNOWN_SPIS = {
            "com.tradej.broker.api.spi.BrokerProvider",
            "com.tradej.indicators.spi.IndicatorProvider",
            "com.tradej.indicators.spi.TransformationProvider"
    };

    @Override
    public Integer call() {
        List<PluginEntry> plugins = discoverPlugins();

        if (root.json()) {
            printJson(plugins);
            return 0;
        }

        System.out.println(Ansi.bold("\n  SPI Plugin Registrations\n"));

        RichTable table = RichTable.of("SPI Interface", "Provider Class", "Module");
        for (PluginEntry p : plugins) {
            table.addRow(p.spiInterface(), p.providerClass(), p.module());
        }
        table.print();

        System.out.println(Ansi.dim("  Total: " + plugins.size() + " provider registrations across "
                + plugins.stream().map(PluginEntry::spiInterface).distinct().count() + " SPIs"));
        return 0;
    }

    static List<PluginEntry> discoverPlugins() {
        List<PluginEntry> plugins = new ArrayList<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        for (String spi : KNOWN_SPIS) {
            String shortName = spi.substring(spi.lastIndexOf('.') + 1);
            try {
                Enumeration<URL> resources = cl.getResources("META-INF/services/" + spi);
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    List<String> providers = readServiceFile(url);
                    String module = inferModule(url);
                    for (String provider : providers) {
                        plugins.add(new PluginEntry(shortName, provider, module));
                    }
                }
            } catch (IOException e) {
                plugins.add(new PluginEntry(shortName, "(error reading)", e.getMessage()));
            }
        }
        return plugins;
    }

    private static List<String> readServiceFile(URL url) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private static String inferModule(URL url) {
        String path = url.toString();
        if (path.contains("broker-gateway")) return "broker-gateway";
        if (path.contains("broker-dhan") || path.contains("broker/dhan")) return "broker-dhan";
        if (path.contains("broker-upstox") || path.contains("broker/upstox")) return "broker-upstox";
        if (path.contains("broker-icici") || path.contains("broker/icici")) return "broker-icici";
        if (path.contains("trading-simulation") || path.contains("trading/simulation")) return "trading-simulation";
        if (path.contains("trading-indicators") || path.contains("trading/indicators")) return "trading-indicators";
        return "unknown";
    }

    private void printJson(List<PluginEntry> plugins) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < plugins.size(); i++) {
            if (i > 0) json.append(",");
            var p = plugins.get(i);
            json.append(String.format(
                    "{\"spi\":\"%s\",\"provider\":\"%s\",\"module\":\"%s\"}",
                    p.spiInterface(), p.providerClass(), p.module()));
        }
        json.append("]");
        System.out.println(json);
    }

    record PluginEntry(String spiInterface, String providerClass, String module) {}
}
