package com.tradej.cli.command;

import com.tradej.brokergateway.BrokerHandle;
import com.tradej.brokergateway.certification.BrokerCertification;
import com.tradej.brokergateway.certification.CertificationReport;
import com.tradej.brokergateway.explorer.BrokerExplorer;
import com.tradej.brokergateway.explorer.BrokerInspectionReport;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.cli.CliContext;
import com.tradej.cli.output.OutputFormatter;
import com.tradej.cli.output.TablePrinter;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.ExchangeSegment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CLI commands for the broker gateway: {@code tradej broker <name> <action>}.
 * Uses the {@link com.tradej.brokergateway.BrokerGateway} for all operations,
 * providing latency and source metadata in output.
 */
public final class CliGatewayCommands extends CliCommandSupport {

    public CliGatewayCommands(CliContext context, OutputFormatter out) {
        super(context, out);
    }

    public void quote(String brokerName, String symbol, String segmentName) {
        BrokerHandle broker = resolveBroker(brokerName);
        GatewayResult<Quote> result = broker.quote(symbol, parseSegment(segmentName));
        if (context().json()) {
            out().print(gatewayResultMap("quote", result));
            return;
        }
        Quote q = result.data();
        out().println("Quote: " + symbol + " [" + result.source() + " " + result.latencyMs() + "ms]");
        out().println("  LTP:    " + q.ltpPaisa() + " paisa");
        out().println("  Open:   " + q.openPaisa());
        out().println("  High:   " + q.highPaisa());
        out().println("  Low:    " + q.lowPaisa());
        out().println("  Close:  " + q.closePaisa());
        out().println("  Volume: " + q.volume());
    }

    public void depth(String brokerName, String symbol, String segmentName) {
        BrokerHandle broker = resolveBroker(brokerName);
        GatewayResult<MarketDepth> result = broker.depth(symbol, parseSegment(segmentName));
        if (context().json()) {
            out().print(gatewayResultMap("depth", result));
            return;
        }
        MarketDepth d = result.data();
        out().println("Depth: " + symbol + " [" + result.source() + " " + result.latencyMs() + "ms]");
        out().println("  Bids: " + d.bids().size() + "  Asks: " + d.asks().size());
    }

    public void ltp(String brokerName, String symbol, String segmentName) {
        BrokerHandle broker = resolveBroker(brokerName);
        GatewayResult<Long> result = broker.ltp(symbol, parseSegment(segmentName));
        if (context().json()) {
            out().print(gatewayResultMap("ltp", result));
            return;
        }
        out().println("LTP: " + symbol + " = " + result.data() + " paisa [" + result.source() + " " + result.latencyMs() + "ms]");
    }

    public void historical(String brokerName, String symbol, String segmentName, String interval, LocalDate from, LocalDate to) {
        BrokerHandle broker = resolveBroker(brokerName);
        GatewayResult<List<Candle>> result = broker.historical(symbol, parseSegment(segmentName), interval, from, to);
        if (context().json()) {
            out().print(gatewayResultMap("historical", result));
            return;
        }
        out().println("Historical: " + symbol + " " + interval + " [" + result.source() + " " + result.latencyMs() + "ms]");
        out().println("  Candles: " + result.data().size());
    }

    public void optionChain(String brokerName, String underlying, String segmentName, String expiryStr) {
        BrokerHandle broker = resolveBroker(brokerName);
        ExchangeSegment segment = parseSegment(segmentName);
        GatewayResult<OptionChainSnapshot> result;
        if (expiryStr != null && !expiryStr.isBlank()) {
            result = broker.optionChain(underlying, segment, LocalDate.parse(expiryStr));
        } else {
            result = broker.optionChain(underlying);
        }
        if (context().json()) {
            out().print(gatewayResultMap("option-chain", result));
            return;
        }
        OptionChainSnapshot chain = result.data();
        out().println("Option Chain: " + underlying + " [" + result.source() + " " + result.latencyMs() + "ms]");
        out().println("  Expiry:  " + chain.expiry());
        out().println("  Spot:    " + chain.spotPricePaisa() + " paisa");
        out().println("  Strikes: " + chain.strikes().size());
    }

    public void balance(String brokerName) {
        BrokerHandle broker = resolveBroker(brokerName);
        var result = broker.balance();
        if (context().json()) {
            out().print(gatewayResultMap("balance", result));
            return;
        }
        out().println("Balance [" + result.source() + " " + result.latencyMs() + "ms]");
        out().println("  Cash:         " + result.data().cashPaisa() + " paisa");
        out().println("  Utilized:     " + result.data().utilizedPaisa() + " paisa");
        out().println("  Withdrawable: " + result.data().withdrawablePaisa() + " paisa");
    }

    public void positions(String brokerName) {
        BrokerHandle broker = resolveBroker(brokerName);
        var result = broker.positions();
        if (context().json()) {
            out().print(gatewayResultMap("positions", result));
            return;
        }
        out().println("Positions [" + result.source() + " " + result.latencyMs() + "ms]: " + result.data().size());
    }

    public void orders(String brokerName) {
        BrokerHandle broker = resolveBroker(brokerName);
        var result = broker.orders();
        if (context().json()) {
            out().print(gatewayResultMap("orders", result));
            return;
        }
        out().println("Orders [" + result.source() + " " + result.latencyMs() + "ms]: " + result.data().size());
    }

    public void inspect(String brokerName) {
        BrokerHandle broker = resolveBroker(brokerName);
        BrokerInspectionReport report = BrokerExplorer.inspect(broker);
        if (context().json()) {
            out().print(Map.of(
                    "broker", report.source().name(),
                    "capabilities", report.capabilities(),
                    "metadata", report.metadata(),
                    "catalogLoaded", report.catalogLoaded(),
                    "instrumentCount", report.instrumentCount()
            ));
            return;
        }
        out().print(BrokerExplorer.formatReport(report));
    }

    public void capabilities(String brokerName, boolean jsonOutput) {
        BrokerHandle broker = resolveBroker(brokerName);
        BrokerInspectionReport report = BrokerExplorer.inspect(broker);

        if (jsonOutput || context().json()) {
            out().print(Map.of(
                    "broker", report.source().name(),
                    "capabilities", report.capabilities(),
                    "supported", report.supportedCount(),
                    "total", report.totalCount(),
                    "metadata", report.metadata(),
                    "catalogLoaded", report.catalogLoaded(),
                    "instrumentCount", report.instrumentCount()
            ));
            return;
        }

        out().println("Broker Capabilities: " + report.source());
        out().println("Supported: " + report.supportedCount() + "/" + report.totalCount());
        out().println("");

        List<String[]> rows = new java.util.ArrayList<>();
        for (Map.Entry<String, Boolean> entry : report.capabilities().entrySet()) {
            rows.add(new String[]{
                    entry.getKey(),
                    entry.getValue() ? "YES" : "NO",
                    categorize(entry.getKey())
            });
        }
        TablePrinter.print(new String[]{"Capability", "Supported", "Category"}, rows);
    }

    private static String categorize(String capability) {
        return switch (capability) {
            case "MarketDataProvider", "OptionsProvider", "FuturesProvider", "InstrumentResolver" -> "market";
            case "OrderCommand", "OrderQuery", "BracketOrderProvider", "GttOrderProvider", "SliceOrderCommand" -> "orders";
            case "PortfolioProvider", "MarginProvider" -> "portfolio";
            case "WebSocketMultiplexer" -> "streaming";
            case "NewsProvider", "ConditionalAlertProvider" -> "services";
            case "SessionRiskProvider" -> "risk";
            default -> capability.endsWith("Capable") ? "marker" : "other";
        };
    }

    public void validate(String brokerName, String symbol, String segmentName) {
        BrokerHandle broker = resolveBroker(brokerName);
        ExchangeSegment segment = parseSegment(segmentName);
        CertificationReport report = BrokerCertification.runFull(broker, symbol, segment);
        if (context().json()) {
            out().print(Map.of(
                    "broker", report.broker().name(),
                    "overall", report.overall().name(),
                    "passed", report.passed(),
                    "failed", report.failed(),
                    "total", report.total(),
                    "totalLatencyMs", report.totalLatencyMs(),
                    "checks", report.checks().stream().map(c -> Map.of(
                            "name", c.name(),
                            "status", c.status().name(),
                            "latencyMs", c.latencyMs(),
                            "evidence", c.evidence() != null ? c.evidence() : "",
                            "error", c.errorMessage() != null ? c.errorMessage() : ""
                    )).toList()
            ));
            return;
        }
        out().print(report.formatReport());
    }

    // ── Internal ────────────────────────────────────────────────────

    private BrokerHandle resolveBroker(String name) {
        if (name == null || name.isBlank()) {
            return context().brokerHandle();
        }
        return context().brokerHandle(name);
    }

    private <T> Map<String, Object> gatewayResultMap(String operation, GatewayResult<T> result) {
        return Map.of(
                "operation", operation,
                "broker", result.source().name(),
                "latencyMs", result.latencyMs(),
                "receivedAt", result.receivedAt().toString(),
                "data", result.data()
        );
    }
}
