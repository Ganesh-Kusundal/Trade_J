package com.tradej.brokergateway.spi.impl;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.SessionRiskProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.spi.BrokerExtras;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.OptionQuote;

import java.util.Optional;
import java.util.Set;

/**
 * Dhan-specific extras.
 *
 * <p>Dhan is the most feature-complete broker. Exposes:
 * session risk, bracket orders, GTT orders, slice orders,
 * conditional alerts, futures, and options (greeks, chain, expiries).
 * Does not provide a {@link NewsProvider}.
 */
public final class DhanExtras implements BrokerExtras {

    private final IBrokerConnection connection;

    public DhanExtras(IBrokerConnection connection) {
        this.connection = connection;
    }

    @Override
    public BrokerSource source() {
        return BrokerSource.DHAN;
    }

    @Override
    public Optional<NewsProvider> news() {
        return Optional.empty();
    }

    @Override
    public Optional<OptionsProvider> optionsProvider() {
        return connection.getCapability(OptionsProvider.class);
    }

    @Override
    public Optional<OptionQuote> optionGreeks(InstrumentKey instrumentKey) {
        return optionsProvider().map(p -> p.getGreeks(instrumentKey));
    }

    @Override
    public Set<String> methods() {
        return Set.of("sessionRisk", "bracketOrders", "gttOrders",
                "sliceOrders", "alerts", "futures", "optionGreeks", "getOptionGreeks");
    }

    @Override
    public Optional<Object> invoke(String methodName, Object... args) {
        return switch (methodName) {
            case "sessionRisk" -> connection.getCapability(SessionRiskProvider.class)
                    .map(p -> (Object) p);
            case "bracketOrders" -> connection.getCapability(BracketOrderProvider.class)
                    .map(p -> (Object) p);
            case "gttOrders" -> connection.getCapability(GttOrderProvider.class)
                    .map(p -> (Object) p);
            case "sliceOrders" -> connection.getCapability(SliceOrderCommand.class)
                    .map(p -> (Object) p);
            case "alerts" -> connection.getCapability(ConditionalAlertProvider.class)
                    .map(p -> (Object) p);
            case "futures" -> connection.getCapability(FuturesProvider.class)
                    .map(p -> (Object) p);
            case "optionGreeks", "getOptionGreeks" -> invokeOptionGreeks(args);
            default -> throw new UnsupportedOperationException("Dhan does not expose " + methodName);
        };
    }

    private Optional<Object> invokeOptionGreeks(Object[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("'optionGreeks' requires an InstrumentKey argument");
        }
        Object arg = args[0];
        if (arg instanceof InstrumentKey ik) {
            return optionGreeks(ik).map(q -> (Object) q);
        }
        throw new IllegalArgumentException(
                "'optionGreeks' first arg must be InstrumentKey, got " +
                        (arg == null ? "null" : arg.getClass().getSimpleName()));
    }
}
