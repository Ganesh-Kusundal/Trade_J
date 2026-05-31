package com.tradej.scanner.fetch;

import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.scanner.option.OptionExpiryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OptionChainFetcher {
    private static final Logger log = LoggerFactory.getLogger(OptionChainFetcher.class);

    private final OptionsProvider optionsProvider;

    public OptionChainFetcher(OptionsProvider optionsProvider) {
        this.optionsProvider = optionsProvider;
    }

    public Map<String, OptionChainSnapshot> fetchForUnderlyings(
            List<String> underlyings,
            ExchangeSegment exchangeSegment
    ) {
        return fetchForUnderlyings(underlyings, exchangeSegment, OptionExpiryPolicy.NEAREST, null);
    }

    public Map<String, OptionChainSnapshot> fetchForUnderlyings(
            List<String> underlyings,
            ExchangeSegment exchangeSegment,
            OptionExpiryPolicy expiryPolicy,
            LocalDate explicitExpiry
    ) {
        Map<String, OptionChainSnapshot> chains = new HashMap<>();
        for (String underlying : underlyings) {
            try {
                OptionChainSnapshot chain = fetchChain(underlying, exchangeSegment, expiryPolicy, explicitExpiry);
                chains.put(underlying, chain);
            } catch (RuntimeException ex) {
                log.warn("Option chain fetch failed for {} on {}: {}", underlying, exchangeSegment, ex.getMessage());
            }
        }
        return Map.copyOf(chains);
    }

    public OptionChainSnapshot fetchChain(
            String underlying,
            ExchangeSegment exchangeSegment,
            OptionExpiryPolicy expiryPolicy,
            LocalDate explicitExpiry
    ) {
        List<LocalDate> expiries = optionsProvider.getExpiries(underlying, exchangeSegment);
        if (expiries.isEmpty()) {
            throw new IllegalStateException("No option expiries for " + underlying + " on " + exchangeSegment);
        }
        LocalDate expiry = expiryPolicy.resolve(expiries, explicitExpiry);
        return optionsProvider.getOptionChain(underlying, exchangeSegment, expiry);
    }
}
