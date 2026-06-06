package com.tradej.brokergateway.spi.impl;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.spi.BrokerExtras;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.OptionQuote;

import java.util.Optional;
import java.util.Set;

/**
 * ICICI-specific extras.
 *
 * <p>ICICI provides futures and options but lacks bracket orders, GTT, slice orders,
 * session risk, conditional alerts, and news.
 */
public final class IciciExtras implements BrokerExtras {

    private final IBrokerConnection connection;

    public IciciExtras(IBrokerConnection connection) {
        this.connection = connection;
    }

    @Override
    public BrokerSource source() {
        return BrokerSource.ICICI;
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
        return Set.of("futures", "optionGreeks", "getOptionGreeks");
    }

    @Override
    public Optional<Object> invoke(String methodName, Object... args) {
        return switch (methodName) {
            case "futures" -> connection.getCapability(FuturesProvider.class)
                    .map(p -> (Object) p);
            case "optionGreeks", "getOptionGreeks" -> invokeOptionGreeks(args);
            default -> throw new UnsupportedOperationException("ICICI does not expose " + methodName);
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
