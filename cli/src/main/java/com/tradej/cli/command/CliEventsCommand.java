package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import com.tradej.cli.output.RichTable;
import com.tradej.core.domain.event.DomainEventVisitor;
import com.tradej.core.domain.event.EventCatalogEntry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(name = "events", description = "Event catalog — all DomainEvent types with category, schema, and priority")
public final class CliEventsCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli root;

    @Option(names = "--filter", description = "Filter events by name or category")
    String filter;

    @Option(names = "--group", description = "Group events by category")
    boolean group;

    @Option(names = "--producers", description = "Show producer classes for each event")
    boolean producers;

    @Option(names = "--consumers", description = "Show consumer classes for each event")
    boolean consumers;

    @Override
    public Integer call() {
        List<EventCatalogEntry> entries = discoverEvents();

        if (filter != null && !filter.isBlank()) {
            String lowerFilter = filter.toLowerCase();
            entries = entries.stream()
                    .filter(e -> e.name().toLowerCase().contains(lowerFilter)
                            || e.category().toLowerCase().contains(lowerFilter))
                    .toList();
        }

        Map<String, EventFlowScanner.EventFlow> flowMap = null;
        if (producers || consumers) {
            List<String> eventNames = entries.stream().map(EventCatalogEntry::name).toList();
            List<EventFlowScanner.EventFlow> flows = EventFlowScanner.scan(eventNames);
            flowMap = new java.util.LinkedHashMap<>();
            for (EventFlowScanner.EventFlow flow : flows) {
                flowMap.put(flow.eventName(), flow);
            }
        }

        if (root.json()) {
            printJson(entries);
            return 0;
        }

        if (group) {
            printGrouped(entries);
        } else {
            printTable(entries, flowMap);
        }

        System.out.println(Ansi.dim("  Total: " + entries.size() + " event types"));
        return 0;
    }

    static List<EventCatalogEntry> discoverEvents() {
        List<EventCatalogEntry> entries = new ArrayList<>();
        for (Method method : DomainEventVisitor.class.getMethods()) {
            if (method.getName().equals("visit") && method.getParameterCount() == 1) {
                Class<?> eventClass = method.getParameterTypes()[0];
                entries.add(EventCatalogEntry.fromClass(eventClass));
            }
        }
        entries.sort(Comparator.comparing(EventCatalogEntry::category)
                .thenComparing(EventCatalogEntry::name));
        return entries;
    }

    private void printTable(List<EventCatalogEntry> entries, Map<String, EventFlowScanner.EventFlow> flowMap) {
        System.out.println(Ansi.bold("\n  Domain Event Catalog\n"));

        boolean showFlows = flowMap != null;
        String[] headers = showFlows
                ? new String[]{"Event", "Category", "Schema", "Producers", "Consumers"}
                : new String[]{"Event", "Category", "Schema", "Package"};

        RichTable table = RichTable.of(headers)
                .rowFormatter(row -> {
                    String category = row[1];
                    String colored = switch (category) {
                        case "market" -> Ansi.cyan(category);
                        case "order" -> Ansi.blue(category);
                        case "trade" -> Ansi.green(category);
                        case "signal" -> Ansi.magenta(category);
                        case "risk" -> Ansi.red(category);
                        case "analytics" -> Ansi.yellow(category);
                        default -> Ansi.dim(category);
                    };
                    return new String[]{row[0], colored, row[2], row[3], showFlows ? row[4] : null};
                });

        for (EventCatalogEntry e : entries) {
            if (showFlows) {
                EventFlowScanner.EventFlow flow = flowMap.get(e.name());
                String prodStr = flow != null ? String.valueOf(flow.producers().size()) : "—";
                String consStr = flow != null ? String.valueOf(flow.consumers().size()) : "—";
                table.addRow(e.name(), e.category(), String.valueOf(e.schemaVersion()), prodStr, consStr);
            } else {
                table.addRow(e.name(), e.category(), String.valueOf(e.schemaVersion()), e.packageName());
            }
        }
        table.print();

        if (showFlows && flowMap != null) {
            System.out.println();
            for (EventFlowScanner.EventFlow flow : flowMap.values()) {
                if (!flow.producers().isEmpty() || !flow.consumers().isEmpty()) {
                    System.out.printf("  %s%n", Ansi.bold(flow.eventName()));
                    if (!flow.producers().isEmpty()) {
                        System.out.printf("    Producers: %s%n", String.join(", ", flow.producers()));
                    }
                    if (!flow.consumers().isEmpty()) {
                        System.out.printf("    Consumers: %s%n", String.join(", ", flow.consumers()));
                    }
                }
            }
        }
    }

    private void printGrouped(List<EventCatalogEntry> entries) {
        System.out.println(Ansi.bold("\n  Domain Event Catalog (by Category)\n"));

        Map<String, List<EventCatalogEntry>> grouped = entries.stream()
                .collect(Collectors.groupingBy(EventCatalogEntry::category));

        for (Map.Entry<String, List<EventCatalogEntry>> group : grouped.entrySet()) {
            System.out.println(Ansi.bold("  " + group.getKey()) + " (" + group.getValue().size() + ")");
            for (EventCatalogEntry e : group.getValue()) {
                System.out.printf("    %s %s%n", Ansi.cyan(e.name()), Ansi.dim("(schema v" + e.schemaVersion() + ")"));
            }
            System.out.println();
        }
    }

    private void printJson(List<EventCatalogEntry> entries) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) json.append(",");
            var e = entries.get(i);
            json.append(String.format(
                    "{\"name\":\"%s\",\"category\":\"%s\",\"schemaVersion\":%d,\"packageName\":\"%s\"}",
                    e.name(), e.category(), e.schemaVersion(), e.packageName()));
        }
        json.append("]");
        System.out.println(json);
    }
}
