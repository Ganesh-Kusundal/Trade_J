package com.tradej.app.config;

import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.icici.config.IciciAuthMode;
import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.historical.ingest.importing.HiveCacheImportConfig;
import com.tradej.historical.ingest.universe.Nifty500UniverseFetcher;
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
        UpstoxProperties upstox,
        IciciProperties icici,
        StorageProperties storage,
        RiskProperties risk,
        ReconciliationProperties reconciliation,
        InstrumentProperties instruments,
        PortfolioProperties portfolio,
        RuntimeProperties runtime,
        HotPathProperties hotPath,
        UniverseProperties universe,
        CandleProperties candles,
        DownloadProperties download,
        HistoricalEquityProperties historicalEquity,
        AnalyticsProperties analytics,
        List<SubscriptionProperties> subscriptions,
        Map<String, VenueProperties> venues,
        SyncProperties sync
) {
    public TradingProperties {
        if (runtime == null) {
            runtime = new RuntimeProperties(RuntimeMode.LIVE);
        }
        if (hotPath == null) {
            hotPath = new HotPathProperties(0);
        }
        if (universe == null) {
            universe = new UniverseProperties(0);
        }
        if (candles == null) {
            candles = new CandleProperties(null);
        }
        if (download == null) {
            download = new DownloadProperties(0L, 2);
        }
        if (sync == null) {
            sync = new SyncProperties(true, "0 0 16 * * MON-FRI", 3, "NSE_EQ", 50, 500L, false, true);
        }
        if (historicalEquity == null) {
            historicalEquity = new HistoricalEquityProperties(
                    "data/historical-equity",
                    Nifty500UniverseFetcher.DEFAULT_UNIVERSE_URL,
                    true,
                    8,
                    200L,
                    "",
                    "",
                    "",
                    HiveCacheImportConfig.DEFAULT_FROM_MONTH,
                    ""
            );
        }
        if (analytics == null) {
            analytics = new AnalyticsProperties(
                    "duckdb",
                    "data/historical-equity",
                    "runtime-dev/historical.duckdb",
                    "",
                    false,
                    true,
                    10_000,
                    30_000L
            );
        }
    }

    public record HotPathProperties(
            @DefaultValue("0") int shardCount
    ) {
        public int effectiveShardCount() {
            if (shardCount > 0) {
                return shardCount;
            }
            return Math.max(1, Runtime.getRuntime().availableProcessors());
        }
    }

    public record UniverseProperties(
            @DefaultValue("0") int maxSubscriptionsPerBatch
    ) {
    }

    public record CandleProperties(
            List<String> intervals
    ) {
        public CandleProperties {
            if (intervals == null || intervals.isEmpty()) {
                intervals = List.of("1s", "5m");
            }
        }
    }

    public record RuntimeProperties(
            @DefaultValue("LIVE") RuntimeMode mode
    ) {
    }

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

    public record UpstoxProperties(
            @NotBlank String clientId,
            @NotBlank String clientSecret,
            String redirectUri,
            String accessToken,
            String refreshToken,
            String analyticsToken,
            String extendedToken,
            @DefaultValue("false") boolean analyticsOnly,
            @DefaultValue("true") boolean sandbox,
            @DefaultValue("18080") int redirectServerPort,
            @DefaultValue("1800000") long refreshBufferMs,
            @DefaultValue("600000") long tokenExpiryBufferMs,
            @DefaultValue("5") long optionExpiryCacheTtlMinutes
    ) {
        @AssertTrue(message = "trade.upstox.analytics-token is required when trade.upstox.analytics-only is true")
        public boolean hasAnalyticsTokenWhenRequired() {
            return !analyticsOnly || (analyticsToken != null && !analyticsToken.isBlank());
        }
    }

    public record IciciProperties(
            @NotBlank String appKey,
            @NotBlank String secretKey,
            String sessionToken,
            @DefaultValue("BROWSER_AUTOMATED") IciciAuthMode authMode,
            @DefaultValue("config/icici-totp-secret.txt") String totpSecretFile,
            @DefaultValue("config/icici-username.txt") String usernameFile,
            @DefaultValue("config/icici-password.txt") String passwordFile,
            @DefaultValue("config/icici-api-session.txt") String apiSessionFile,
            @DefaultValue("runtime/icici-token-state.json") String tokenStateFile,
            @DefaultValue("false") boolean ordersEnabled,
            @DefaultValue("10") long refreshBufferMinutes,
            @DefaultValue("9080") int loginRedirectPort,
            @DefaultValue("/api") String loginRedirectPath,
            @DefaultValue("true") boolean browserHeadless,
            @DefaultValue("120") long browserLoginTimeoutSeconds
    ) {
    }

    public record StorageProperties(
            @NotBlank String chroniclePath,
            @NotBlank String duckdbPath,
            @DefaultValue("runtime-dev/historical.duckdb") String historicalWarehousePath
    ) {
    }

    public record DownloadProperties(
            @DefaultValue("0") long delayMs,
            @DefaultValue("2") int workers
    ) {
    }

    public record HistoricalEquityProperties(
            @DefaultValue("data/historical-equity") String rootPath,
            @DefaultValue(Nifty500UniverseFetcher.DEFAULT_UNIVERSE_URL) String universeUrl,
            @DefaultValue("true") boolean refreshUniverseOnJobStart,
            @DefaultValue("8") int workers,
            @DefaultValue("200") long delayMs,
            @DefaultValue("") String sourceHivePath,
            @DefaultValue("") String sourceUniverseCsv,
            @DefaultValue("") String sourceIndustryParquet,
            @DefaultValue(HiveCacheImportConfig.DEFAULT_FROM_MONTH) String importFromMonth,
            @DefaultValue("") String importToMonth
    ) {
    }

    public record AnalyticsProperties(
            @DefaultValue("duckdb") String engine,
            @DefaultValue("data/historical-equity") String equityRoot,
            @DefaultValue("runtime-dev/historical.duckdb") String optionsWarehouse,
            @DefaultValue("") String runtimeDbPath,
            @DefaultValue("false") boolean attachRuntimeDb,
            @DefaultValue("true") boolean sqlEnabled,
            @DefaultValue("10000") int sqlMaxRows,
            @DefaultValue("30000") long sqlMaxRuntimeMs
    ) {
    }

    public record RiskProperties(
            long maxDailyLossPaisa,
            int maxConsecutiveLosses,
            long maxOrderValuePaisa,
            Integer maxOpenPositions,
            @DefaultValue("3") int maxOpenPositionQuantity,
            @DefaultValue("10") int maxDistinctOpenPositions,
            @DefaultValue("false") boolean enforceMargin,
            @DefaultValue("false") boolean enforceUnrealizedLoss,
            @DefaultValue("5") int marginCacheTtlMinutes
    ) {
        public RiskProperties {
            if (maxOpenPositionQuantity <= 0 && maxOpenPositions != null && maxOpenPositions > 0) {
                maxOpenPositionQuantity = maxOpenPositions;
            }
            if (maxOpenPositionQuantity <= 0) {
                maxOpenPositionQuantity = 3;
            }
        }

        public int effectiveMaxOpenPositionQuantity() {
            return maxOpenPositionQuantity;
        }
    }

    public record InstrumentProperties(
            String csvPath,
            String cacheDirectory,
            boolean autoDownload
    ) {
    }

    public record PortfolioProperties(
            long defaultCapitalPaisa,
            long maxNetExposurePaisa
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
            long initialDelaySeconds,
            @DefaultValue("false") boolean autoHalt,
            @DefaultValue("0") long mismatchToleranceQty
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

    public record SyncProperties(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("0 0 16 * * MON-FRI") String cron,
            @DefaultValue("3") int lookbackMonths,
            @DefaultValue("NSE_EQ") String segment,
            @DefaultValue("50") int batchSize,
            @DefaultValue("500") long delayMs,
            @DefaultValue("false") boolean autoResample,
            @DefaultValue("true") boolean refreshHolidays
    ) {
    }
}
