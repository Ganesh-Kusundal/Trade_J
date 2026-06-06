package com.tradej.brokergateway.simulation;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.spi.BrokerDescriptor;
import com.tradej.brokergateway.spi.BrokerProvider;
import com.tradej.brokergateway.spi.CapabilityMetadata;
import com.tradej.composition.config.BrokerProfile;

import java.util.List;
import java.util.Map;

/**
 * Simulation broker provider for paper trading.
 * Returns canned responses for all operations — no real broker connection.
 *
 * <p>Registered via SPI in {@code META-INF/services/com.tradej.brokergateway.spi.BrokerProvider}.
 */
public final class SimulationBrokerProvider implements BrokerProvider {

    @Override
    public BrokerSource source() {
        return BrokerSource.SIMULATION;
    }

    @Override
    public String displayName() {
        return "Paper Trading";
    }

    @Override
    public BrokerDescriptor descriptor() {
        return new BrokerDescriptor(
                BrokerSource.SIMULATION,
                "Paper Trading",
                Map.ofEntries(
                        Map.entry("MarketDataProvider", true),
                        Map.entry("OptionsProvider", true),
                        Map.entry("OrderCommand", true),
                        Map.entry("OrderQuery", true),
                        Map.entry("PortfolioProvider", true),
                        Map.entry("MarginProvider", true),
                        Map.entry("InstrumentResolver", true),
                        Map.entry("WebSocketMultiplexer", false),
                        Map.entry("FuturesProvider", true),
                        Map.entry("BracketOrderProvider", true),
                        Map.entry("GttOrderProvider", true),
                        Map.entry("SliceOrderCommand", true),
                        Map.entry("SessionRiskProvider", false),
                        Map.entry("ConditionalAlertProvider", false),
                        Map.entry("NewsProvider", false)
                ),
                Map.of("mode", "simulation", "latency", "0ms"),
                List.of("NSE_EQ", "IDX_I", "NSE_FNO"),
                "unlimited (simulated)",
                Map.ofEntries(
                        Map.entry("MarketDataProvider", new CapabilityMetadata("Simulated LTP, quotes, OHLC, depth", "market", "1.0")),
                        Map.entry("OptionsProvider", new CapabilityMetadata("Simulated option chain, expiries, greeks", "market", "1.0")),
                        Map.entry("OrderCommand", new CapabilityMetadata("Simulated place, modify, cancel orders", "orders", "1.0")),
                        Map.entry("OrderQuery", new CapabilityMetadata("Simulated order book, trade book, status", "orders", "1.0")),
                        Map.entry("PortfolioProvider", new CapabilityMetadata("Simulated holdings, positions", "portfolio", "1.0")),
                        Map.entry("MarginProvider", new CapabilityMetadata("Simulated margin calculator", "risk", "1.0")),
                        Map.entry("InstrumentResolver", new CapabilityMetadata("Simulated instrument master, symbol lookup", "services", "1.0")),
                        Map.entry("WebSocketMultiplexer", new CapabilityMetadata("Not supported in simulation", "streaming", "1.0")),
                        Map.entry("FuturesProvider", new CapabilityMetadata("Simulated futures quotes and chain", "market", "1.0")),
                        Map.entry("BracketOrderProvider", new CapabilityMetadata("Simulated bracket orders with target and SL", "orders", "1.0")),
                        Map.entry("GttOrderProvider", new CapabilityMetadata("Simulated GTT orders", "orders", "1.0")),
                        Map.entry("SliceOrderCommand", new CapabilityMetadata("Simulated order slicing", "orders", "1.0")),
                        Map.entry("SessionRiskProvider", new CapabilityMetadata("Not supported in simulation", "risk", "1.0")),
                        Map.entry("ConditionalAlertProvider", new CapabilityMetadata("Not supported in simulation", "services", "1.0")),
                        Map.entry("NewsProvider", new CapabilityMetadata("Not supported in simulation", "services", "1.0"))
                )
        );
    }

    @Override
    public IBrokerConnection connect(BrokerProfile profile) {
        return new PaperBrokerConnection();
    }
}
