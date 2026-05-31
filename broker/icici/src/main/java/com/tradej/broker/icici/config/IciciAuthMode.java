package com.tradej.broker.icici.config;

public enum IciciAuthMode {
    /** Pre-resolved base64 session token in properties (short-lived dev). */
    STATIC,
    /**
     * Pass TOTP code directly to CustomerDetails (works only on some accounts;
     * most accounts require {@link #BROWSER_AUTOMATED} or {@link #API_SESSION}).
     */
    TOTP_GENERATED,
    /** Browser {@code API_Session} captured manually and stored in {@code icici-api-session.txt}. */
    API_SESSION,
    /**
     * Headless Chrome login at ICICI Breeze portal: username + password + TOTP,
     * capture {@code apisession} from redirect URL, then exchange via CustomerDetails.
     */
    BROWSER_AUTOMATED
}
