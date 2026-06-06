package com.tradej.broker.core.startup;

import com.tradej.core.domain.runtime.RuntimeMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class BrokerStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(BrokerStartupValidator.class);

    public void validate(RuntimeMode runtime, BrokerStartupConfig config) {
        log.info("Validating startup configuration for runtime mode: {}", runtime);

        if (runtime == RuntimeMode.LIVE) {
            validateLiveMode(config);
        }

        if (config.instrumentCsvPath() != null) {
            Path path = Path.of(config.instrumentCsvPath());
            if (!Files.exists(path)) {
                throw new IllegalStateException(
                        "Instrument catalog not found at: " + config.instrumentCsvPath());
            }
        }

        log.info("Startup configuration validation passed for mode: {}", runtime);
    }

    private void validateLiveMode(BrokerStartupConfig config) {
        if (config.brokerClientId() == null || config.brokerClientId().isBlank()) {
            throw new IllegalStateException(
                    "Runtime mode LIVE requires 'trade.broker.client-id'");
        }
        if (config.brokerAccessToken() == null || config.brokerAccessToken().isBlank()) {
            log.warn("'trade.broker.access-token' is not configured — broker will fail on first connection");
        }
    }

    public record BrokerStartupConfig(
            String brokerClientId,
            String brokerAccessToken,
            String instrumentCsvPath
    ) {
    }
}
