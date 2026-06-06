package com.tradej.core.domain.instrument;

import com.tradej.core.domain.value.ExchangeSegment;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Canonical NSE index symbols and their broker-trading aliases.
 *
 * <p>The NSE's official index names contain spaces (e.g. {@code NIFTY BANK}) but most broker
 * trading feeds and option contract symbols use the legacy compact form ({@code BANKNIFTY}).
 * This class is the single source of truth for both:
 * <ul>
 *   <li>The canonical names that callers use externally (via {@link Instruments})</li>
 *   <li>The legacy aliases that callers may still pass as input (e.g. {@code BANKNIFTY})</li>
 * </ul>
 *
 * <p>Outside (public API surface) — use the canonical names.
 * <br>Inside broker adapters — translate canonical → broker-specific (e.g. securityId lookup).
 */
public final class IndexSymbols {

    public static final String NIFTY = "NIFTY";
    public static final String NIFTY_BANK = "NIFTY BANK";
    public static final String NIFTY_FIN_SERVICE = "NIFTY FIN SERVICE";
    public static final String NIFTY_MID_SELECT = "NIFTY MID SELECT";
    public static final String SENSEX = "SENSEX";
    public static final String BANKEX = "BANKEX";

    /**
     * Canonical NSE index symbols. Order matters: longer prefixes must precede shorter ones
     * so {@code startsWith} checks match the most specific name first.
     */
    public static final List<String> CANONICAL_INDICES = List.of(
            NIFTY_MID_SELECT,
            NIFTY_FIN_SERVICE,
            NIFTY_BANK,
            NIFTY,
            SENSEX,
            BANKEX
    );

    /**
     * Broker-trading aliases for canonical index names. The mapping is broker-feed-specific
     * (NSE/broker tickers use compact forms) and is the inverse of the canonical name.
     */
    public static final Map<String, String> ALIAS_TO_CANONICAL = Map.ofEntries(
            Map.entry("BANKNIFTY", NIFTY_BANK),
            Map.entry("FINNIFTY", NIFTY_FIN_SERVICE),
            Map.entry("MIDCPNIFTY", NIFTY_MID_SELECT)
    );

    private IndexSymbols() {
    }

    /**
     * Returns the canonical NSE name for the given input symbol, or the input unchanged
     * (uppercased and trimmed) when no alias matches. Cash equities and F&amp;O contract
     * symbols pass through untouched.
     */
    public static String canonicalize(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "";
        }
        String upper = symbol.trim().toUpperCase(Locale.ENGLISH);
        return ALIAS_TO_CANONICAL.getOrDefault(upper, upper);
    }

    /**
     * Returns true when the symbol refers to an index underlying. Checks both canonical
     * names and contract symbols whose underlying is an index (e.g. {@code NIFTY 30 JUN FUT}
     * or {@code NIFTY 30 JUN 30000 CALL}).
     */
    public static boolean isIndexUnderlying(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        String upper = symbol.trim().toUpperCase(Locale.ENGLISH);
        String canonical = ALIAS_TO_CANONICAL.getOrDefault(upper, upper);
        for (String prefix : CANONICAL_INDICES) {
            if (canonical.equals(prefix) || canonical.startsWith(prefix + " ")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the canonical {@link ExchangeSegment} for the default (unspecified) case.
     * <p>Resolution order:
     * <ol>
     *   <li>Parsed option/future contract (any underlying) → {@link ExchangeSegment#NSE_FNO}</li>
     *   <li>Spot index underlying (e.g. {@code "NIFTY"}, {@code "NIFTY BANK"}) → {@link ExchangeSegment#IDX_I}</li>
     *   <li>Cash equity, malformed symbol, or null → {@link ExchangeSegment#NSE_EQ}</li>
     * </ol>
     * <p>Contracts are checked first because an index option symbol like
     * {@code "NIFTY 30 JUN 30000 CALL"} matches both the contract pattern and the
     * "index underlying" check — but the contract's actual market segment is
     * {@code NSE_FNO}, not {@code IDX_I}.
     */
    public static ExchangeSegment defaultSegment(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return ExchangeSegment.NSE_EQ;
        }
        if (ContractSymbolNormalizer.parse(symbol) != null) {
            return ExchangeSegment.NSE_FNO;
        }
        if (isIndexUnderlying(symbol)) {
            return ExchangeSegment.IDX_I;
        }
        return ExchangeSegment.NSE_EQ;
    }

    /**
     * Returns a defensive copy of the canonical index set, in declaration order.
     * Useful for tests and for callers that need to iterate indices deterministically.
     */
    public static Set<String> canonicalIndexSet() {
        return new LinkedHashSet<>(CANONICAL_INDICES);
    }
}
