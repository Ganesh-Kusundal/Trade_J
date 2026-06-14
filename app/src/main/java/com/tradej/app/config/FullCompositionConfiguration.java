package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.composition.BrokerComposition;
import com.tradej.composition.ClockComposition;
import com.tradej.composition.FullComposition;
import com.tradej.composition.config.BrokerProfile;
import com.tradej.composition.config.RiskProfile;
import com.tradej.composition.config.StorageProfile;
import com.tradej.core.domain.port.FeatureStore;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.scanner.engine.ScanEngine;
import com.tradej.scanner.model.ScanProfile;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.GraphStrategySandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Wires {@link FullComposition} as a Spring bean. The composition layer is additive —
 * the existing {@code TradingConfiguration}, {@code PipelineConfiguration}, and broker
 * adapter configurations still own the per-bean wiring they already perform.
 * {@code FullComposition} provides a single entry point that aggregates the
 * {@link BrokerComposition} and the position/risk beans owned by
 * {@code ExecutionComposition}.
 *
 * <p>Phase 2B progression: this is the additive step. The "strip" step (removing
 * duplicate @Bean methods from TradingConfiguration once FullComposition is the
 * single root) is deferred to a follow-up commit because it is invasive (20+ bean
 * methods, dozens of consumers).
 */
@Configuration
public class FullCompositionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FullCompositionConfiguration.class);

    @Bean
    public ClockComposition clockComposition() {
        return ClockComposition.live();
    }

    @Bean
    public FullComposition fullComposition(
            TradingProperties properties,
            PortfolioEngine portfolioEngine,
            ObjectProvider<IBrokerConnection> brokerConnection,
            OrderManagementService orderManagementService,
            CandleAggregationService candleAggregationService,
            GraphStrategySandbox graphStrategySandbox,
            ExecutionHandler executionHandler,
            ObjectProvider<FeatureStore> featureStore,
            ObjectProvider<ScanEngine> scanEngine,
            ObjectProvider<com.tradej.composition.config.ScanProperties> scanProperties,
            ObjectProvider<BrokerComposition> brokerCompositionBean
    ) {
        // ── Build composition profiles from TradingProperties ──
        StorageProfile storageProfile = new StorageProfile(
                java.nio.file.Path.of(properties.storage().chroniclePath()),
                java.nio.file.Path.of(properties.storage().duckdbPath())
        );

        BrokerProfile brokerProfile = buildBrokerProfile(properties, brokerCompositionBean);

        RiskProfile riskProfile = new RiskProfile(
                new com.tradej.core.domain.model.RiskLimits(
                        properties.risk().maxDailyLossPaisa(),
                        properties.risk().maxConsecutiveLosses(),
                        properties.risk().maxOrderValuePaisa(),
                        properties.risk().effectiveMaxOpenPositionQuantity(),
                        properties.risk().maxDistinctOpenPositions()
                ),
                properties.risk().enforceMargin(),
                properties.risk().marginCacheTtlMinutes(),
                properties.risk().enforceUnrealizedLoss()
        );

        // ── Resolve scan profiles from composition config (nullable) ──
        Map<String, ScanProfile> scanProfilesById = scanProperties.getIfAvailable() == null
                ? Map.of()
                : scanProperties.getIfAvailable().profiles().stream()
                        .filter(p -> p.id() != null)
                        .map(com.tradej.app.scanner.ScanProfileMapper::toDomain)
                        .collect(Collectors.toMap(
                                ScanProfile::id,
                                profile -> profile,
                                (left, right) -> right));

        log.info("FullCompositionConfiguration creating FullComposition (broker={}, scanProfiles={})",
                brokerProfile.brokerType(), scanProfilesById.size());

        return FullComposition.createFull(
                clockComposition(),
                brokerProfile,
                storageProfile,
                riskProfile,
                portfolioEngine,
                brokerConnection.getIfAvailable(),
                orderManagementService,
                candleAggregationService,
                graphStrategySandbox,
                executionHandler,
                featureStore.getIfAvailable(),
                scanEngine.getIfAvailable(),
                scanProfilesById
        );
    }

    /**
     * Build a {@link BrokerProfile} from {@link TradingProperties}. If a
     * {@link BrokerComposition} bean is already registered, reuse its profile
     * (which has already been SPI-resolved and validated). Otherwise, build a
     * SIMULATION profile as a safe default.
     */
    private BrokerProfile buildBrokerProfile(
            TradingProperties properties,
            ObjectProvider<BrokerComposition> brokerCompositionBean
    ) {
        BrokerComposition existing = brokerCompositionBean.getIfAvailable();
        if (existing != null) {
            return existing.profile();
        }
        // Fallback: SIMULATION profile. No credentials needed; composition layer
        // will throw if downstream code tries to use the broker connection.
        return new BrokerProfile(
                BrokerProfile.BrokerType.SIMULATION,
                null, null, null
        );
    }
}
