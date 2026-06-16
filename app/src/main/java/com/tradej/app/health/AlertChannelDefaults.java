package com.tradej.app.health;

import java.time.Duration;

/**
 * Shared default timeout constants for alert channels.
 *
 * <p>All alert channel implementations ({@link SlackAlertChannel},
 * {@link PagerDutyAlertChannel}, {@link WebhookAlertChannel}) should
 * reference the same timeout to ensure consistent behavior and allow
 * centralised tuning.
 */
public final class AlertChannelDefaults {

    private AlertChannelDefaults() {
    }

    /** Standard timeout for HTTP calls made by alert channels. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
}
