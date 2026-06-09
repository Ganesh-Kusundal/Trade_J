package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "flows", description = "Runtime flow documentation — sequence diagrams for key data paths",
        subcommands = {
                CliFlowsCommand.QuoteFlowCmd.class,
                CliFlowsCommand.OrderFlowCmd.class,
                CliFlowsCommand.ReplayFlowCmd.class,
                CliFlowsCommand.SimulationFlowCmd.class,
                CliFlowsCommand.RiskFlowCmd.class
        })
public final class CliFlowsCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli root;

    static final Map<String, FlowDefinition> FLOWS = Map.of(
            "quote", new FlowDefinition("Quote Flow",
                    "Broker WebSocket (Dhan/Upstox/ICICI)",
                    new String[]{
                            "1. Broker WebSocket → BrokerAdapter.normalize()",
                            "2. BrokerAdapter → DisruptorEventBus (ring buffer, 8192 slots)",
                            "3. DisruptorEventBus → GraphPipelineDisruptorHandler",
                            "4. GraphRuntime.processSequential()",
                            "5. CandleNode → CandleAggregationService",
                            "6. FeatureNode → DuckDbFeatureStore",
                            "7. AsyncDispatchHandler → subscribers",
                            "8. DuckDbEventStore (persist) + ChronicleAuditLogWriter (audit)",
                            "9. ReadModelStore (CQRS read model update)",
                            "10. ObservableMarketDataProvider (Micrometer metrics)"
                    }),
            "order", new FlowDefinition("Order Flow",
                    "CLI / REST API",
                    new String[]{
                            "1. CLI/REST → CommandHandler.execute(PlaceOrder)",
                            "2. CommandHandler → OMS.placeOrder(OrderRequest)",
                            "3. PositionRiskHandler.handleSignalPending()",
                            "4. RiskCheckChain.findRejection(context)",
                            "   ├── KillSwitchRiskCheck",
                            "   ├── DailyLossRiskCheck",
                            "   └── PositionLimitRiskCheck",
                            "5. approved → SignalPendingExecution",
                            "6. CommandHandler.execute(PlaceOrder)",
                            "7. BrokerAdapter.placeOrder() → Exchange",
                            "8. OrderStateMachine: NEW → SUBMITTED",
                            "9. OrderAccepted/OrderFilled → OrderStateMachine transition"
                    }),
            "replay", new FlowDefinition("Replay Flow",
                    "DuckDB / Chronicle Queue",
                    new String[]{
                            "1. DuckDB → HistoricalQueryService (SQL queries)",
                            "   ├── queryTicks() → List<MarketTickEvent>",
                            "   ├── queryCandles() → List<Candle>",
                            "   ├── queryOrders() → List<HistoricalOrder>",
                            "   └── queryFills() → List<HistoricalFill>",
                            "2. HistoricalEventReplayService (event reconstruction)",
                            "3. EventBus.publish() → full pipeline (same as live)",
                            "4. VirtualClock (REPLAY mode: advances on event timestamps)",
                            "5. ReplayResult (events replayed, duration, throughput)"
                    }),
            "simulation", new FlowDefinition("Simulation Flow",
                    "OrderRequest",
                    new String[]{
                            "1. SimulatedOrderService.placeOrder()",
                            "2. ContractSymbolNormalizer.normalize()",
                            "3. MatchingEngine.match()",
                            "   ├── resolveFillPrice() ← lastPrice + slippage model",
                            "   │   ├── spreadBps (half-spread for aggressive orders)",
                            "   │   ├── volatilitySlippageBps (scaled by variance)",
                            "   │   └── partialFillRatio (for large orders)",
                            "4. MatchResult (order + fills + rejected)",
                            "5. PnLLedger.applyFill()",
                            "   ├── Position.apply() ← net qty, avg price, realized PnL",
                            "   ├── markToMarket() ← unrealized PnL",
                            "   └── snapshot() → PnlUpdatedEvent",
                            "6. SimulationMetrics (orders, fills, slippage, duration)"
                    }),
            "risk", new FlowDefinition("Risk Flow",
                    "SignalPendingExecution",
                    new String[]{
                            "1. PositionRiskHandler.handleSignalPending()",
                            "2. Build RiskContext (positions, PnL, trade count)",
                            "3. RiskCheckChain.findRejection(context)",
                            "   ├── KillSwitchRiskCheck",
                            "   │   └── checks: kill_switch / reconciliation_halt flags",
                            "   ├── DailyLossRiskCheck",
                            "   │   └── checks: realized + unrealized vs max daily loss",
                            "   └── PositionLimitRiskCheck",
                            "       └── checks: open trades vs max position limit",
                            "4. First rejection wins",
                            "5. rejected → SignalSuppressed event",
                            "6. approved → continue to execution"
                    })
    );

    @Override
    public Integer call() {
        if (root.json()) {
            printJson();
            return 0;
        }

        System.out.println(Ansi.bold("\n  Trade-J Runtime Flows\n"));
        System.out.println("  Available flows:");
        for (var entry : FLOWS.entrySet()) {
            System.out.printf("    tradej flows %s  — %s (entry: %s)%n",
                    Ansi.cyan(entry.getKey()),
                    entry.getValue().title(),
                    Ansi.dim(entry.getValue().entryPoint()));
        }
        System.out.println();
        return 0;
    }

    static void printFlow(String key) {
        FlowDefinition flow = FLOWS.get(key);
        if (flow == null) {
            System.err.println("Unknown flow: " + key + ". Available: " + String.join(", ", FLOWS.keySet()));
            return;
        }
        System.out.println(Ansi.bold("\n  " + flow.title() + "\n"));
        System.out.println(Ansi.dim("  Entry point: " + flow.entryPoint()));
        System.out.println();
        for (String step : flow.steps()) {
            System.out.println("  " + step);
        }
        System.out.println();
    }

    private void printJson() {
        StringBuilder json = new StringBuilder("{");
        int i = 0;
        for (var entry : FLOWS.entrySet()) {
            if (i > 0) json.append(",");
            json.append(String.format("\"%s\":{\"title\":\"%s\",\"entryPoint\":\"%s\",\"steps\":%d}",
                    entry.getKey(), entry.getValue().title(), entry.getValue().entryPoint(),
                    entry.getValue().steps().length));
            i++;
        }
        json.append("}");
        System.out.println(json);
    }

    record FlowDefinition(String title, String entryPoint, String[] steps) {}

    // ── Sub-commands ────────────────────────────────────────────────

    @Command(name = "quote", description = "Quote/market data flow")
    static final class QuoteFlowCmd implements Callable<Integer> {
        @ParentCommand CliFlowsCommand parent;
        @Override public Integer call() { printFlow("quote"); return 0; }
    }

    @Command(name = "order", description = "Order execution flow")
    static final class OrderFlowCmd implements Callable<Integer> {
        @ParentCommand CliFlowsCommand parent;
        @Override public Integer call() { printFlow("order"); return 0; }
    }

    @Command(name = "replay", description = "Historical replay flow")
    static final class ReplayFlowCmd implements Callable<Integer> {
        @ParentCommand CliFlowsCommand parent;
        @Override public Integer call() { printFlow("replay"); return 0; }
    }

    @Command(name = "simulation", description = "Simulation matching engine flow")
    static final class SimulationFlowCmd implements Callable<Integer> {
        @ParentCommand CliFlowsCommand parent;
        @Override public Integer call() { printFlow("simulation"); return 0; }
    }

    @Command(name = "risk", description = "Risk check chain flow")
    static final class RiskFlowCmd implements Callable<Integer> {
        @ParentCommand CliFlowsCommand parent;
        @Override public Integer call() { printFlow("risk"); return 0; }
    }
}
