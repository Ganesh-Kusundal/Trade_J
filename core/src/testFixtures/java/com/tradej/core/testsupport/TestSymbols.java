package com.tradej.core.testsupport;

/**
 * Shared test data constants to eliminate hardcoded symbol names across test files.
 *
 * <p>All modules should reference these constants instead of duplicating
 * {@code "RELIANCE"}, {@code "SBIN"}, {@code "TCS"}, {@code "NIFTY"}, etc.
 *
 * <p>Lives in {@code core/src/testFixtures/} so it is reachable from every
 * module that consumes {@code testFixtures(project(':core'))}.
 */
public final class TestSymbols {

    private TestSymbols() {
    }

    // ── Common equity symbols ─────────────────────────────────────────
    public static final String RELIANCE = "RELIANCE";
    public static final String SBIN = "SBIN";
    public static final String TCS = "TCS";
    public static final String INFY = "INFY";
    public static final String HDFC = "HDFC";

    // ── Index underlying symbols ──────────────────────────────────────
    public static final String NIFTY = "NIFTY";
    public static final String BANKNIFTY = "BANKNIFTY";
    public static final String SENSEX = "SENSEX";
    public static final String FINNIFTY = "FINNIFTY";
    public static final String MIDCPNIFTY = "MIDCPNIFTY";

    // ── Commodity underlying symbols ──────────────────────────────────
    public static final String CRUDEOIL = "CRUDEOIL";
    public static final String GOLD = "GOLD";
    public static final String SILVER = "SILVER";
    public static final String NATURALGAS = "NATURALGAS";

    // ── Default test symbol for generic tests ─────────────────────────
    public static final String DEFAULT = SBIN;
}
