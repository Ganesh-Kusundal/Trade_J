package com.tradej.app.config;

import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "trade")
public record TradingProperties(
        DhanProperties broker,
        StorageProperties storage,
        RiskProperties risk,
        ReconciliationProperties reconciliation,
        InstrumentProperties instruments,
        List<SubscriptionProperties> subscriptions,
        Map<String, VenueProperties> venues
) {
    public record DhanProperties(
            @NotBlank String clientId,
            String accessToken,
            @DefaultValue("LIVE") DhanApiEnvironment environment,
            String restBaseUrl,
            boolean loggingEnabled,
            int rateLimitRetries,
            int maxReconnectAttempts,
            boolean autoReconnectEnabled,
            boolean autoResubscribeEnabled,
            @DefaultValue("STATIC") DhanAuthMode authMode,
            @DefaultValue("config/dhan-pin.txt") String pinFile,
            @DefaultValue("config/dhan-totp-secret.txt") String totpSecretFile,
            @DefaultValue("runtime/dhan-token-state.json") String tokenStateFile,
            @DefaultValue("10") long refreshBufferMinutes,
            @DefaultValue("5") long optionExpiryCacheTtlMinutes
    ) {
        @AssertTrue(message = "trade.broker.access-token is required unless trade.broker.auth-mode is TOTP_GENERATED")
        public boolean hasSupportedCredentialShape() {
            return authMode == DhanAuthMode.TOTP_GENERATED || (accessToken != null && !accessToken.isBlank());
        }
    }

    public record StorageProperties(
            @NotBlank String chroniclePath,
            @NotBlank String duckdbPath
    ) {
    }

    public record RiskProperties(
            long maxDailyLossPaisa,
            int maxConsecutiveLosses,
            long maxOrderValuePaisa,
            int maxOpenPositions
    ) {
    }

    public record InstrumentProperties(
            String csvPath,
            String cacheDirectory,
            boolean autoDownload
    ) {
    }

    public record SubscriptionProperties(
            @NotBlank String symbol,
            ExchangeSegment exchangeSegment,
            FeedMode feedMode
    ) {
    }

    public record ReconciliationProperties(
            long intervalSeconds,
            long initialDelaySeconds
    ) {
    }

    public record VenueProperties(
            List<FeedMode> supportedFeedModes,
            boolean supportsDepth20,
            boolean supportsDepth200,
            boolean requiresContractDiscovery,
            LocalTime sessionOpen,
            LocalTime sessionClose,
            boolean supportsLateSession
    ) {
    }
}
