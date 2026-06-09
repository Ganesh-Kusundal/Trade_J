package com.tradej.broker.api.exception;

import java.util.Set;

public final class UnsupportedIntervalException extends IllegalArgumentException {
    private final String broker;
    private final String requestedInterval;
    private final Set<String> supportedIntervals;

    public UnsupportedIntervalException(String broker, String requestedInterval, Set<String> supportedIntervals) {
        super(buildMessage(broker, requestedInterval, supportedIntervals));
        this.broker = broker;
        this.requestedInterval = requestedInterval;
        this.supportedIntervals = supportedIntervals;
    }

    public String broker() { return broker; }
    public String requestedInterval() { return requestedInterval; }
    public Set<String> supportedIntervals() { return supportedIntervals; }

    private static String buildMessage(String broker, String interval, Set<String> supported) {
        return broker + " does not support interval '" + interval + "'. Supported: " + supported;
    }
}
