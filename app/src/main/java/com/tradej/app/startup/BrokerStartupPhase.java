package com.tradej.app.startup;

/**
 * Ordered broker startup contract.
 */
public enum BrokerStartupPhase {
    RESOLVE_PROFILE,
    LOAD_CATALOG,
    VALIDATE_SUBSCRIPTIONS,
    VALIDATE_TOKENS,
    VERIFY_PREFLIGHT,
    WIRE_EVENT_HANDLERS,
    START_EVENT_BUS,
    RECOVER_STATE,
    CONNECT_TRANSPORT,
    COMPLETE
}
