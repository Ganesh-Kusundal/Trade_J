package com.tradej.brokergateway.spi;

import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.OptionQuote;

import java.util.Optional;
import java.util.Set;

/**
 * Escape hatch for broker-specific capabilities that don't fit the standard port interfaces.
 *
 * <p>Example: Upstox provides news and data services (charges, P&L, holidays) that
 * are not part of the standard broker-api. Access them via:
 * <pre>
 *   handle.extras().news().ifPresent(news -> news.getNewsByInstrumentKeys(...));
 * </pre>
 */
public interface BrokerExtras {

    /**
     * The broker source this extras object belongs to.
     */
    BrokerSource source();

    /**
     * Access the broker's news provider, if available.
     */
    default Optional<NewsProvider> news() {
        return Optional.empty();
    }

    /**
     * Access the broker's options provider, if available.
     */
    default Optional<OptionsProvider> optionsProvider() {
        return Optional.empty();
    }

    /**
     * Fetch greeks for the given option instrument.
     * Default returns empty when the broker does not expose an {@link OptionsProvider}.
     * <p>For latency-aware access, use {@code BrokerHandle.greeks(InstrumentKey)} which
     * returns a {@code GatewayResult<OptionQuote>} with timing metadata.
     */
    default Optional<OptionQuote> optionGreeks(InstrumentKey instrumentKey) {
        return optionsProvider().map(p -> p.getGreeks(instrumentKey));
    }

    /**
     * Returns the set of method names available via {@link #invoke(String, Object...)}.
     */
    default Set<String> methods() {
        return Set.of();
    }

    /**
     * Invoke a broker-specific method by name.
     * Arguments and return types are broker-specific.
     *
     * @throws UnsupportedOperationException if the method is not exposed
     */
    default Optional<Object> invoke(String methodName, Object... args) {
        throw new UnsupportedOperationException(
                source() + " does not expose " + methodName);
    }
}
