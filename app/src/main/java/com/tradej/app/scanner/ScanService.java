package com.tradej.app.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.composition.config.ScanProperties;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.institutional.InstitutionalScanEngine;
import com.tradej.institutional.model.InstitutionalScanResult;
import com.tradej.institutional.model.ScoredBar;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.tradej.persistence.duckdb.DuckDbScanStore;
import com.tradej.scanner.engine.ScanDependencies;
import com.tradej.scanner.engine.ScanEngine;
import com.tradej.scanner.option.OptionLiquidityScanner;
import com.tradej.core.domain.scan.AssetClass;
import com.tradej.core.domain.scan.ScanHit;
import com.tradej.scanner.model.ScanMode;
import com.tradej.scanner.model.ScanProfile;
import com.tradej.core.domain.scan.ScanResult;
import com.tradej.core.domain.scan.ScanRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ScanService {
    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private final ScanProperties scanProperties;
    private final ScanEngine scanEngine;
    private final OptionLiquidityScanner optionLiquidityScanner;
    private final InstitutionalScanEngine institutionalScanEngine;
    private final HistoricalBarRepository historicalBarRepository;
    private final DuckDbScanStore scanStore;
    private final RuntimeSubscriptionManager subscriptionManager;
    private final GatewayTopicRouter gatewayRouter;
    private final ObjectMapper objectMapper;

    public ScanService(
            ScanProperties scanProperties,
            ScanDependencies scanDependencies,
            DuckDbScanStore scanStore,
            RuntimeSubscriptionManager subscriptionManager,
            GatewayTopicRouter gatewayRouter,
            ObjectMapper objectMapper,
            InstitutionalScanEngine institutionalScanEngine,
            HistoricalBarRepository historicalBarRepository,
            RuntimeModeHolder runtimeModeHolder
    ) {
        this.scanProperties = scanProperties;
        this.scanEngine = new ScanEngine(scanDependencies);
        this.optionLiquidityScanner = new OptionLiquidityScanner(scanDependencies.optionsProvider());
        this.institutionalScanEngine = resolveInstitutionalScanEngine(institutionalScanEngine, scanProperties, runtimeModeHolder);
        this.historicalBarRepository = resolveHistoricalBarRepository(historicalBarRepository, scanProperties, runtimeModeHolder);
        this.scanStore = scanStore;
        this.subscriptionManager = subscriptionManager;
        this.gatewayRouter = gatewayRouter != null ? gatewayRouter : new com.tradej.gateway.router.NoOpGatewayTopicRouter();
        this.objectMapper = objectMapper;
    }

    private static InstitutionalScanEngine resolveInstitutionalScanEngine(
            InstitutionalScanEngine institutionalScanEngine,
            ScanProperties scanProperties,
            RuntimeModeHolder runtimeModeHolder
    ) {
        if (institutionalScanEngine != null) {
            return institutionalScanEngine;
        }
        if (requiresHistoricalScan(scanProperties) && !runtimeModeHolder.policy().permitsFallbackInfrastructure()) {
            throw new IllegalStateException("LIVE mode requires InstitutionalScanEngine for PARQUET_HISTORICAL scan profiles");
        }
        return new com.tradej.institutional.NoOpInstitutionalScanEngine();
    }

    private static HistoricalBarRepository resolveHistoricalBarRepository(
            HistoricalBarRepository historicalBarRepository,
            ScanProperties scanProperties,
            RuntimeModeHolder runtimeModeHolder
    ) {
        if (historicalBarRepository != null) {
            return historicalBarRepository;
        }
        if (requiresHistoricalScan(scanProperties) && !runtimeModeHolder.policy().permitsFallbackInfrastructure()) {
            throw new IllegalStateException("LIVE mode requires HistoricalBarRepository for PARQUET_HISTORICAL scan profiles");
        }
        return new com.tradej.core.domain.port.NoOpHistoricalBarRepository();
    }

    private static boolean requiresHistoricalScan(ScanProperties scanProperties) {
        return scanProperties.profiles().stream()
                .anyMatch(profile -> profile.mode() == ScanMode.PARQUET_HISTORICAL);
    }

    public ScanResult runProfile(String profileId) {
        ScanProfile profile = resolveProfile(profileId);
        ScanResult result;
        if (profile.mode() == ScanMode.PARQUET_HISTORICAL) {
            result = runInstitutionalScan(profile);
        } else {
            result = profile.isOptionLiquidityProfile()
                    ? optionLiquidityScanner.scanProfile(profile)
                    : scanEngine.run(profile);
        }
        persistAndPromote(profile, result);
        return result;
    }

    private ScanResult runInstitutionalScan(ScanProfile profile) {
        if (institutionalScanEngine instanceof com.tradej.institutional.NoOpInstitutionalScanEngine) {
            throw new IllegalStateException("Institutional scan engine is not configured");
        }
        if (historicalBarRepository instanceof com.tradej.core.domain.port.NoOpHistoricalBarRepository) {
            throw new IllegalStateException("Historical bar repository is not configured");
        }
        LocalDate scanDate = historicalBarRepository.latestAvailableTradingDay(0)
                .orElseThrow(() -> new IllegalStateException("No parquet trading days available"));
        int universeSize = historicalBarRepository.querySymbols(200).size();
        ScanRun run = ScanRun.started(profile.id(), universeSize);
        try {
            InstitutionalScanResult institutional = institutionalScanEngine.runHistoricalScan(scanDate, null);
            List<ScanHit> hits = institutional.candidates().stream()
                    .map(this::toInstitutionalHit)
                    .toList();
            return new ScanResult(run.completed(hits.size(), 0), hits);
        } catch (RuntimeException ex) {
            log.error("Institutional scan failed for profile {}: {}", profile.id(), ex.getMessage(), ex);
            return new ScanResult(run.failed(ex.getMessage()), List.of());
        }
    }

    private ScanHit toInstitutionalHit(ScoredBar bar) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("masterScore", bar.masterScore());
        snapshot.put("rsScore", bar.rsScore());
        snapshot.put("volumeExpansionScore", bar.volumeExpansionScore());
        snapshot.put("trendEfficiencyScore", bar.trendEfficiencyScore());
        snapshot.put("openingDriveScore", bar.openingDriveScore());
        snapshot.put("rank", bar.rank());
        snapshot.put("barTimeMs", bar.barTime().toEpochMilli());
        return new ScanHit(
                InstrumentKey.of(bar.symbol(), ExchangeSegment.NSE_EQ),
                AssetClass.EQUITY,
                bar.symbol(),
                bar.masterScore(),
                List.of("institutional-baseline"),
                snapshot,
                false
        );
    }

    public ScanResult runDefaultProfile() {
        String profileId = scanProperties.defaultProfile();
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalStateException("No default scan profile configured");
        }
        return runProfile(profileId);
    }

    public Optional<ScanResult> latest(String profileId) {
        try {
            return scanStore.latestByProfile(profileId);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load latest scan for " + profileId, ex);
        }
    }

    public Optional<ScanResult> byRunId(String runId) {
        try {
            return scanStore.findByRunId(runId);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load scan run " + runId, ex);
        }
    }

    public List<com.tradej.core.domain.scan.ScanRun> listRuns(String profileId, int limit) {
        try {
            return scanStore.listRuns(profileId, limit);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to list scan runs for " + profileId, ex);
        }
    }

    public List<ScanProfile> configuredProfiles() {
        return scanProperties.profiles().stream()
                .map(ScanProfileMapper::toDomain)
                .toList();
    }

    private ScanProfile resolveProfile(String profileId) {
        return scanProperties.profiles().stream()
                .filter(p -> p.id().equals(profileId))
                .findFirst()
                .map(ScanProfileMapper::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("Unknown scan profile: " + profileId));
    }

    private void persistAndPromote(ScanProfile profile, ScanResult result) {
        try {
            scanStore.save(result);
        } catch (Exception ex) {
            log.error("Failed to persist scan run {}: {}", result.run().runId(), ex.getMessage());
        }
        if (profile.mode() == ScanMode.HYBRID || profile.mode() == ScanMode.WS_LIVE) {
            subscriptionManager.applyPromotion(profile, result.hits());
        }
        publishGateway(result);
    }

    private void publishGateway(ScanResult result) {
        if (!(gatewayRouter instanceof com.tradej.gateway.router.NoOpGatewayTopicRouter)) {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("runId", result.run().runId());
                payload.put("profileId", result.run().profileId());
                payload.put("status", result.run().status().name());
                payload.put("hitCount", result.hits().size());
                payload.put("hits", result.hits().stream().map(this::hitPayload).toList());
                gatewayRouter.publish(GatewayTopic.SCAN_COMPLETED, objectMapper.writeValueAsBytes(payload));
            } catch (Exception ex) {
                log.warn("Failed to publish SCAN_COMPLETED: {}", ex.getMessage());
            }
        }
    }

    private Map<String, Object> hitPayload(ScanHit hit) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("symbol", hit.symbol());
        map.put("exchangeSegment", hit.exchangeSegment().name());
        map.put("assetClass", hit.assetClass().name());
        map.put("underlying", hit.underlying());
        map.put("score", hit.score());
        map.put("reasons", hit.reasons());
        map.put("promoted", hit.promoted());
        return map;
    }
}
