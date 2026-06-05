package com.tradej.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.dhan.config.DhanConfigPaths;
import com.tradej.cli.CliContext;
import com.tradej.cli.config.CliConfig;
import com.tradej.cli.output.OutputFormatter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CliAttachCommands extends CliCommandSupport {

    public CliAttachCommands(CliContext context, OutputFormatter out) {
        super(context, out);
    }

    public void status() {
        requireAttach();
        JsonNode health = context().attach().health();
        JsonNode summary = context().attach().summary();
        JsonNode runtime = context().attach().runtime();
        if (context().json()) {
            out().print(Map.of("health", health, "summary", summary, "runtime", runtime));
            return;
        }
        out().println("=== Health ===");
        out().println(health.toPrettyString());
        out().println("=== Summary ===");
        out().println(summary.toPrettyString());
        out().println("=== Runtime ===");
        out().println(runtime.toPrettyString());
    }

    public void runtime() {
        requireAttach();
        out().print(context().attach().runtime());
    }

    public void pipeline() {
        requireAttach();
        out().print(context().attach().pipeline());
    }

    public void strategies() {
        requireAttach();
        out().print(context().attach().strategies());
    }

    public void summary() {
        requireAttach();
        out().print(context().attach().summary());
    }

    public void orders() {
        requireAttach();
        printReadModel(true, false);
    }

    public void positions() {
        requireAttach();
        printReadModel(false, true);
    }

    public void readModel() {
        requireAttach();
        printReadModel(true, true);
    }

    private void printReadModel(boolean orders, boolean positions) {
        JsonNode model = context().attach().readModel();
        if (context().json()) {
            out().print(model);
            return;
        }
        if (orders && model.has("orders")) {
            out().println("Orders:");
            printJsonArrayTable(model.get("orders"),
                    "orderId", "symbol", "status", "quantity", "pricePaisa");
        }
        if (positions && model.has("positions")) {
            out().println("Positions:");
            printJsonArrayTable(model.get("positions"),
                    "symbol", "quantity", "averagePricePaisa", "lastPricePaisa");
        }
    }

    public void streamReadModel(int seconds) {
        requireAttach();
        context().attach().streamReadModel(seconds, line -> out().println(line));
    }

    public void killSwitch(boolean enabled) {
        if (context().profile() == CliConfig.Profile.LIVE && enabled && !context().yes()) {
            confirmOrAbort("Enable kill-switch on LIVE broker session?");
        }
        if (context().attachReachable()) {
            out().print(context().attach().killSwitch(enabled));
            return;
        }
        boolean ack = orderCommand().setKillSwitch(enabled);
        out().print(Map.of("enabled", enabled, "acknowledged", ack, "backend", "standalone"));
    }

    public void reconcile(String jsonPayload) throws IOException {
        requireAttach();
        Map<String, Long> expected = MAPPER.readValue(jsonPayload, MAPPER.getTypeFactory()
                .constructMapType(LinkedHashMap.class, String.class, Long.class));
        if (!context().yes()) {
            confirmOrAbort("Trigger reconcile with payload: " + expected);
        }
        out().print(context().attach().reconcile(expected));
    }

    public void showRiskConfig() throws IOException {
        Path yml = DhanConfigPaths.resolve("trade-app/src/main/resources/application.yml");
        if (!Files.exists(yml)) {
            out().error("application.yml not found at " + yml);
            return;
        }
        boolean inRisk = false;
        for (String line : Files.readAllLines(yml)) {
            if (line.startsWith("  risk:")) {
                inRisk = true;
            } else if (inRisk && line.startsWith("  ") && !line.startsWith("    ")) {
                break;
            }
            if (inRisk) {
                out().println(line);
            }
        }
        String mode = readYamlValue(yml, "mode:");
        if (mode != null) {
            out().println("Configured runtime mode (application.yml): " + mode.trim());
        }
    }

    public void historicalCandles(String symbol, String interval, long from, long to, int limit) {
        requireAttach();
        out().print(context().attach().historicalCandles(symbol, interval, from, to, limit));
    }

    public void historicalTicks(String symbol, long from, long to, int limit) {
        requireAttach();
        out().print(context().attach().historicalTicks(symbol, from, to, limit));
    }

    public void historicalOrders(String symbol, long from, long to, int limit) {
        requireAttach();
        out().print(context().attach().historicalOrders(symbol, from, to, limit));
    }

    public void historicalFills(String symbol, long from, long to, int limit) {
        requireAttach();
        out().print(context().attach().historicalFills(symbol, from, to, limit));
    }

    public void historicalStats(String symbol, long from, long to) {
        requireAttach();
        out().print(context().attach().historicalStats(symbol, from, to));
    }

    public void replayTicks(String symbol, long from, long to) {
        requireAttach();
        guardReplay();
        if (!context().yes()) {
            confirmOrAbort("Replay ticks for " + symbol + " from " + from + " to " + to);
        }
        out().print(context().attach().replayTicks(symbol, from, to));
    }

    public void replayCandles(String symbol, String interval, long from, long to) {
        requireAttach();
        guardReplay();
        if (!context().yes()) {
            confirmOrAbort("Replay candles for " + symbol);
        }
        out().print(context().attach().replayCandles(symbol, interval, from, to));
    }

    public void replayFills(String symbol, long from, long to) {
        requireAttach();
        guardReplay();
        if (!context().yes()) {
            confirmOrAbort("Replay fill events");
        }
        out().print(context().attach().replayFills(symbol, from, to));
    }

    public void replayOrders(String symbol, long from, long to) {
        requireAttach();
        guardReplay();
        if (!context().yes()) {
            confirmOrAbort("Replay orders");
        }
        out().print(context().attach().replayOrders(symbol, from, to));
    }

    public void replayChronicle(String eventType) {
        requireAttach();
        guardReplay();
        if (!context().yes()) {
            confirmOrAbort("Replay chronicle events of type " + eventType);
        }
        out().print(context().attach().replayChronicle(eventType));
    }
}
