package com.tradej.core.domain.value;

/**
 * Canonical instrument type classification.
 * Parses broker-specific type strings into a standard enum.
 */
public enum InstrumentType {
    EQUITY,
    FUTURE,
    OPTION_CALL,
    OPTION_PUT,
    INDEX,
    CURRENCY_FUTURE,
    CURRENCY_OPTION,
    COMMODITY_FUTURE,
    COMMODITY_OPTION,
    WARRANT,
    BOND,
    MUTUAL_FUND,
    UNKNOWN;

    public static InstrumentType parse(String raw) {
        if (raw == null || raw.isBlank()) return UNKNOWN;
        String upper = raw.trim().toUpperCase();
        return switch (upper) {
            case "EQ", "EQUITY", "E", "STOCK" -> EQUITY;
            case "FUT", "FUTURE", "FUTURES" -> FUTURE;
            case "CE", "CALL", "OPTCE", "OPTION_CALL" -> OPTION_CALL;
            case "PE", "PUT", "OPTPE", "OPTION_PUT" -> OPTION_PUT;
            case "INDEX", "INDICES" -> INDEX;
            case "CURRENCY_FUT", "CURR_FUT", "CFUT" -> CURRENCY_FUTURE;
            case "CURRENCY_OPT", "CURR_OPT", "COPT" -> CURRENCY_OPTION;
            case "COMMODITY_FUT", "COMM_FUT" -> COMMODITY_FUTURE;
            case "COMMODITY_OPT", "COMM_OPT" -> COMMODITY_OPTION;
            default -> {
                if (upper.startsWith("FUT")) yield FUTURE;
                if (upper.startsWith("OPT") && upper.contains("CE")) yield OPTION_CALL;
                if (upper.startsWith("OPT") && upper.contains("PE")) yield OPTION_PUT;
                if (upper.startsWith("OPT")) yield OPTION_CALL;
                yield UNKNOWN;
            }
        };
    }

    public boolean isOption() {
        return this == OPTION_CALL || this == OPTION_PUT
                || this == CURRENCY_OPTION || this == COMMODITY_OPTION;
    }

    public boolean isFuture() {
        return this == FUTURE || this == CURRENCY_FUTURE || this == COMMODITY_FUTURE;
    }

    public boolean isEquity() {
        return this == EQUITY;
    }
}
