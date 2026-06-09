package com.tradej.cli.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans source files to map DomainEvent types to their producers and consumers.
 */
public final class EventFlowScanner {

    private EventFlowScanner() {}

    public record EventFlow(
            String eventName,
            List<String> producers,
            List<String> consumers,
            boolean persisted,
            boolean replayable
    ) {}

    public static List<EventFlow> scan(List<String> eventNames) {
        String workspaceRoot = System.getProperty("trade.workspace.root", ".");
        Path srcRoot = Path.of(workspaceRoot);

        Map<String, List<String>> producerMap = new LinkedHashMap<>();
        Map<String, List<String>> consumerMap = new LinkedHashMap<>();

        for (String name : eventNames) {
            producerMap.put(name, new ArrayList<>());
            consumerMap.put(name, new ArrayList<>());
        }

        scanDirectory(srcRoot, eventNames, producerMap, consumerMap);

        List<String> persistedEvents = findPersistedEvents(srcRoot);
        List<String> replayableEvents = findReplayableEvents(srcRoot);

        List<EventFlow> flows = new ArrayList<>();
        for (String name : eventNames) {
            flows.add(new EventFlow(
                    name,
                    List.copyOf(producerMap.getOrDefault(name, List.of())),
                    List.copyOf(consumerMap.getOrDefault(name, List.of())),
                    persistedEvents.contains(name),
                    replayableEvents.contains(name)
            ));
        }
        return flows;
    }

    private static void scanDirectory(Path root, List<String> eventNames,
                                       Map<String, List<String>> producers,
                                       Map<String, List<String>> consumers) {
        String[] srcDirs = {
                "core/src/main/java",
                "runtime/disruptor/src/main/java",
                "runtime/hotpath/src/main/java",
                "trading/execution/src/main/java",
                "trading/strategy/src/main/java",
                "trading/scanner/src/main/java",
                "trading/simulation/src/main/java",
                "trading/options-analytics/src/main/java",
                "data/persistence/src/main/java",
                "broker-gateway/src/main/java",
                "broker/dhan/src/main/java",
                "broker/upstox/src/main/java",
                "broker/icici/src/main/java",
                "replay/engine/src/main/java",
                "pipeline/core/src/main/java",
                "pipeline/runtime/src/main/java",
                "gateway/src/main/java",
                "app/src/main/java",
        };

        for (String dir : srcDirs) {
            Path srcDir = root.resolve(dir);
            if (!Files.exists(srcDir)) continue;
            try (Stream<Path> files = Files.walk(srcDir)) {
                files.filter(f -> f.toString().endsWith(".java"))
                        .forEach(f -> scanFile(f, eventNames, producers, consumers));
            } catch (IOException e) {
                // skip
            }
        }
    }

    private static void scanFile(Path file, List<String> eventNames,
                                  Map<String, List<String>> producers,
                                  Map<String, List<String>> consumers) {
        try {
            String content = Files.readString(file);
            String className = file.getFileName().toString().replace(".java", "");

            for (String eventName : eventNames) {
                if (content.contains("new " + eventName + "(") || content.contains("new " + eventName + " (")) {
                    List<String> list = producers.get(eventName);
                    if (list != null && !list.contains(className)) {
                        list.add(className);
                    }
                }
                if (content.contains("visit(" + eventName + " ") || content.contains("visit(" + eventName + ")")) {
                    List<String> list = consumers.get(eventName);
                    if (list != null && !list.contains(className)) {
                        list.add(className);
                    }
                }
            }
        } catch (IOException e) {
            // skip
        }
    }

    private static List<String> findPersistedEvents(Path root) {
        List<String> persisted = new ArrayList<>();
        Path eventStore = root.resolve("data/persistence/src/main/java");
        if (!Files.exists(eventStore)) return persisted;
        try (Stream<Path> files = Files.walk(eventStore)) {
            files.filter(f -> f.toString().endsWith("EventStore.java") || f.toString().endsWith("DuckDbEventStore.java"))
                    .forEach(f -> {
                        try {
                            String content = Files.readString(f);
                            for (String event : ALL_EVENTS) {
                                if (content.contains(event)) {
                                    persisted.add(event);
                                }
                            }
                        } catch (IOException e) {
                            // skip
                        }
                    });
        } catch (IOException e) {
            // skip
        }
        return persisted;
    }

    private static List<String> findReplayableEvents(Path root) {
        List<String> replayable = new ArrayList<>();
        Path replayService = root.resolve("data/persistence/src/main/java");
        if (!Files.exists(replayService)) return replayable;
        try (Stream<Path> files = Files.walk(replayService)) {
            files.filter(f -> f.toString().contains("Replay") && f.toString().endsWith(".java"))
                    .forEach(f -> {
                        try {
                            String content = Files.readString(f);
                            for (String event : ALL_EVENTS) {
                                if (content.contains(event)) {
                                    replayable.add(event);
                                }
                            }
                        } catch (IOException e) {
                            // skip
                        }
                    });
        } catch (IOException e) {
            // skip
        }
        return replayable;
    }

    private static final List<String> ALL_EVENTS = List.of(
            "MarketTickEvent", "DepthUpdateEvent", "CandleClosed", "CandleDeveloping",
            "OrderAccepted", "OrderFilled", "OrderFullyFilled", "OrderPartiallyFilled",
            "OrderCancelled", "OrderRejected", "OrderModified",
            "TradeOpened", "TradeClosed", "TradeUpdated", "TradeExecutionEvent",
            "SignalGenerated", "SignalPendingExecution", "SignalSuppressed",
            "KillSwitchEngaged", "UnifiedKillSwitchEngaged", "UnifiedKillSwitchDisengaged",
            "ReconciliationHaltRequired",
            "PnlUpdatedEvent", "UnrealizedPnLUpdated", "PositionUpdateEvent", "PositionMismatch",
            "OptionChainUpdated", "GreeksComputed", "GammaExposureComputed", "MaxPainComputed",
            "ScanHitProduced", "ScanResultsPublished",
            "BrokerAdapterError", "StrategyError",
            "StreamHealthChanged", "ReplayTimeChangedEvent", "EventBusBackpressure",
            "OrderUpdateEvent"
    );
}
