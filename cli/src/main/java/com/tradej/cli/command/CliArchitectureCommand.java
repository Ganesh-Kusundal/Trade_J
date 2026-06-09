package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import com.tradej.cli.output.RichTable;
import com.tradej.core.domain.event.DomainEventVisitor;
import com.tradej.core.domain.event.EventCatalogEntry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(name = "architecture", description = "Platform architecture overview — modules, patterns, events, SPI graph",
        subcommands = {
                CliArchitectureCommand.ModulesSubCmd.class,
                CliArchitectureCommand.PatternsSubCmd.class,
                CliArchitectureCommand.EventsSubCmd.class,
                CliArchitectureCommand.RulesSubCmd.class
        })
public final class CliArchitectureCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli root;

    @Option(names = "--graph", description = "Show ASCII dependency graph")
    boolean graph;

    @Override
    public Integer call() {
        if (root.json()) {
            printJsonSummary();
            return 0;
        }

        if (graph) {
            printDependencyGraph();
            return 0;
        }

        System.out.println(Ansi.bold("\n  Trade-J Platform Architecture\n"));

        printModuleSummary();
        System.out.println();
        printPatternSummary();
        System.out.println();
        printEventSummary();
        System.out.println();
        printSpiSummary();
        System.out.println();
        printConstraintSummary();

        return 0;
    }

    private void printModuleSummary() {
        List<CliModulesCommand.ModuleEntry> modules = CliModulesCommand.parseModules();
        Map<String, Long> byCategory = modules.stream()
                .collect(Collectors.groupingBy(CliModulesCommand.ModuleEntry::category, Collectors.counting()));

        System.out.println(Ansi.bold("  Modules: ") + modules.size());
        RichTable table = RichTable.of("Category", "Count");
        byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> table.addRow(e.getKey(), String.valueOf(e.getValue())));
        table.print();
    }

    private void printPatternSummary() {
        System.out.println(Ansi.bold("  Design Patterns"));
        RichTable table = RichTable.of("Pattern", "Usage", "Enforced By");
        table.addRow("Command", "TradingCommand / CommandHandler / CommandResult", "DesignPatternArchitectureTest");
        table.addRow("Chain of Responsibility", "RiskCheckChain (kill_switch → daily_loss → position_limit)", "DesignPatternArchitectureTest");
        table.addRow("Strategy (SPI)", "IndicatorProvider, BrokerProvider", "SpringFreeArchitectureTest");
        table.addRow("Adapter", "IBrokerConnection per broker (Dhan, Upstox, ICICI)", "ModuleBoundaryArchitectureTest");
        table.addRow("Registry", "9 registries across 6 modules", "CodeQualityArchitectureTest");
        table.addRow("Decorator", "ObservableMarketDataProvider, ObservableOrderCommand", "—");
        table.addRow("State", "OrderStateMachine (table-driven)", "—");
        table.addRow("Event-Driven", "DisruptorEventBus (LMAX, 8192 ring buffer)", "—");
        table.addRow("Specification", "ScanCriterion (composable AND/OR)", "—");
        table.addRow("CQRS", "EventStore → ReadModelStore", "—");
        table.print();
    }

    private void printEventSummary() {
        List<EventCatalogEntry> events = CliEventsCommand.discoverEvents();
        Map<String, Long> byCategory = events.stream()
                .collect(Collectors.groupingBy(EventCatalogEntry::category, Collectors.counting()));

        System.out.println(Ansi.bold("  Events: ") + events.size() + " domain event types");
        RichTable table = RichTable.of("Category", "Count");
        byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> table.addRow(e.getKey(), String.valueOf(e.getValue())));
        table.print();
    }

    private void printSpiSummary() {
        System.out.println(Ansi.bold("  SPI Registrations"));
        RichTable table = RichTable.of("SPI", "Providers", "Discovery");
        table.addRow("BrokerProvider", "Dhan, Upstox, ICICI, Simulation", "ServiceLoader");
        table.addRow("IndicatorProvider", "RSI, EMA, SMA, ATR, VWAP, OBV", "ServiceLoader");
        table.addRow("TransformationProvider", "HeikinAshi", "ServiceLoader");
        table.print();
    }

    private void printConstraintSummary() {
        System.out.println(Ansi.bold("  Architecture Constraints (ArchUnit)"));
        RichTable table = RichTable.of("Test Class", "Rules", "Purpose");
        table.addRow("SpringFreeArchitectureTest", "~5", "Domain modules must not import Spring");
        table.addRow("ModuleBoundaryArchitectureTest", "~6", "Core must not depend on outer modules");
        table.addRow("DesignPatternArchitectureTest", "~21", "Pattern enforcement (command, chain, registry)");
        table.addRow("CodeQualityArchitectureTest", "~2", "God class limit, broker leak prevention");
        table.addRow("ProfileIsolationArchitectureTest", "~2", "Clock profile isolation");
        table.print();
    }

    private void printJsonSummary() {
        List<CliModulesCommand.ModuleEntry> modules = CliModulesCommand.parseModules();
        List<EventCatalogEntry> events = CliEventsCommand.discoverEvents();

        System.out.printf(
                "{\"modules\":%d,\"events\":%d,\"patterns\":10,\"spiProviders\":3,\"architectureTests\":5," +
                "\"architectureStyle\":\"hexagonal\",\"eventBus\":\"LMAX Disruptor\",\"persistence\":\"DuckDB\"}%n",
                modules.size(), events.size());
    }

    private void printDependencyGraph() {
        java.util.Map<String, java.util.List<String>> graph = ModuleDependencyGraph.buildGraph();

        System.out.println(Ansi.bold("\n  Module Dependency Graph\n"));
        System.out.println(ModuleDependencyGraph.renderTree(graph, "app"));
        System.out.println(ModuleDependencyGraph.renderSummary(graph));
    }

    // ── Sub-commands ────────────────────────────────────────────────

    @Command(name = "modules", description = "Module dependency table")
    static final class ModulesSubCmd implements Callable<Integer> {
        @ParentCommand CliArchitectureCommand parent;

        @Override
        public Integer call() {
            List<CliModulesCommand.ModuleEntry> modules = CliModulesCommand.parseModules();
            System.out.println(Ansi.bold("\n  Module Dependencies\n"));
            RichTable table = RichTable.of("Module", "Directory", "Category");
            for (var m : modules) {
                table.addRow(m.name(), m.directory(), m.category());
            }
            table.print();
            return 0;
        }
    }

    @Command(name = "patterns", description = "Design patterns in use")
    static final class PatternsSubCmd implements Callable<Integer> {
        @ParentCommand CliArchitectureCommand parent;

        @Override
        public Integer call() {
            System.out.println();
            parent.printPatternSummary();
            return 0;
        }
    }

    @Command(name = "events", description = "Event flow summary")
    static final class EventsSubCmd implements Callable<Integer> {
        @ParentCommand CliArchitectureCommand parent;

        @Override
        public Integer call() {
            System.out.println();
            parent.printEventSummary();
            return 0;
        }
    }

    @Command(name = "rules", description = "ArchUnit architecture rules")
    static final class RulesSubCmd implements Callable<Integer> {
        @ParentCommand CliArchitectureCommand parent;

        @Override
        public Integer call() {
            System.out.println();
            parent.printConstraintSummary();
            return 0;
        }
    }
}
