package com.tradej.broker.upstox.config;

/**
 * Subscription and connection limits for Upstox Market Data Feed V3.
 * <p>
 * Sourced from the official Upstox V3 developer documentation. Enforced
 * client-side so the broker is not asked to subscribe to more keys than it
 * allows (the broker silently drops overflow subscriptions in V3).
 * <p>
 * Two tiers exist: a normal tier (2 connections) and a Plus tier (5
 * connections, plus access to {@link Mode#FULL_D30}). The {@code +Plus}
 * constants are exposed so a Plus subscriber can opt in explicitly.
 */
public final class UpstoxV3SubscriptionLimits {

    private UpstoxV3SubscriptionLimits() {
    }

    /** V3 subscription mode. Wire value is what the Upstox subscribe API expects. */
    public enum Mode {
        LTPC("ltpc", 5000, 2000, false),
        OPTION_GREEKS("option_greeks", 3000, 2000, false),
        FULL("full", 2000, 1500, false),
        FULL_D30("full_d30", 50, 1500, true);

        private final String wireValue;
        private final int individual;
        private final int combined;
        private final boolean plusOnly;

        Mode(String wireValue, int individual, int combined, boolean plusOnly) {
            this.wireValue = wireValue;
            this.individual = individual;
            this.combined = combined;
            this.plusOnly = plusOnly;
        }

        public String wireValue() {
            return wireValue;
        }

        public int individual() {
            return individual;
        }

        public int combined() {
            return combined;
        }

        public boolean isPlusOnly() {
            return plusOnly;
        }
    }

    // ─── Normal tier ───────────────────────────────────────────────────────
    public static final int CONNECTIONS_NORMAL = 2;

    // ─── Plus tier ─────────────────────────────────────────────────────────
    public static final int CONNECTIONS_PLUS = 5;

    /**
     * Validates a single-mode subscription request.
     *
     * @param mode             the requested V3 mode
     * @param instrumentCount  how many instrument keys are about to be subscribed
     * @param plus             whether the user is on a Upstox Plus plan
     * @return the effective limit that was applied (echoes back the input)
     * @throws UpstoxSubscriptionLimitException if the request exceeds any limit
     */
    public static void validate(Mode mode, int instrumentCount, boolean plus) {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (instrumentCount <= 0) {
            throw new IllegalArgumentException("instrumentCount must be positive, got: " + instrumentCount);
        }
        if (mode.isPlusOnly() && !plus) {
            throw new UpstoxSubscriptionLimitException(
                    "Mode " + mode.wireValue + " is Upstox-Plus-only (active subscriptions: " + instrumentCount + ")");
        }
        if (instrumentCount > mode.individual) {
            throw new UpstoxSubscriptionLimitException(
                    "Upstox V3 individual limit exceeded for mode=" + mode.wireValue
                            + " (requested " + instrumentCount + ", limit " + mode.individual + ")");
        }
    }

    /**
     * Thrown when a V3 subscription request would exceed a documented Upstox limit.
     */
    public static final class UpstoxSubscriptionLimitException extends RuntimeException {
        public UpstoxSubscriptionLimitException(String message) {
            super(message);
        }
    }
}
