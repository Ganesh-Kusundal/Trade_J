package com.tradej.broker.api.auth;

/**
 * Describes how a token was obtained.
 */
public enum TokenSource {
    /** Pre-configured static token, no refresh mechanism. */
    STATIC,
    /** Generated via TOTP (Dhan). */
    TOTP,
    /** Authorization Code Grant with PKCE (Upstox). */
    OAUTH,
    /** Token obtained from an interactive login flow. */
    INTERACTIVE
}
