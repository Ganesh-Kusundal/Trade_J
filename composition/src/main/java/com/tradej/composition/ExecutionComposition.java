package com.tradej.composition;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.composition.config.RiskProfile;
import com.tradej.core.domain.service.PositionService;
import com.tradej.execution.risk.KillSwitchCoordinator;
import com.tradej.execution.risk.MarginEnforcementHandler;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.strategy.portfolio.PortfolioEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Execution composition root — owns the canonical position, risk, and idempotency beans
 * for both the Spring app path and the CLI / replay path.
 *
 * <p>Mirrors the bean wiring that {@code app/.../config/TradingConfiguration.java:140-235}
 * historically performed.
 */
public final class ExecutionComposition {

    private static final Logger log = LoggerFactory.getLogger(ExecutionComposition.class);

    private final RiskProfile profile;
    private final PositionService positionService;
    private final CaffeineIdempotencyCache idempotencyCache;
    private final MarginEnforcementHandler marginEnforcementHandler;
    private final KillSwitchCoordinator killSwitchCoordinator;
    private final PositionRiskHandler positionRiskHandler;

    private ExecutionComposition(
            RiskProfile profile,
            PositionService positionService,
            CaffeineIdempotencyCache idempotencyCache,
            MarginEnforcementHandler marginEnforcementHandler,
            KillSwitchCoordinator killSwitchCoordinator,
            PositionRiskHandler positionRiskHandler
    ) {
        this.profile = profile;
        this.positionService = positionService;
        this.idempotencyCache = idempotencyCache;
        this.marginEnforcementHandler = marginEnforcementHandler;
        this.killSwitchCoordinator = killSwitchCoordinator;
        this.positionRiskHandler = positionRiskHandler;
    }

    /**
     * Build the execution composition from a {@link RiskProfile} and its runtime dependencies.
     *
     * @param profile            risk profile (limits, enforce flags, margin cache TTL)
     * @param positionService    canonical position service (created by the caller; this composition
     *                            holds a reference, not an owner-of-creation)
     * @param portfolioEngine    non-null portfolio engine
     * @param brokerConnection   broker connection (used to resolve margin + portfolio providers; may be null if no broker)
     * @param orderManagementService non-null order management service
     * @return fully-wired {@link ExecutionComposition}
     * @throws NullPointerException if any required argument is null
     */
    public static ExecutionComposition create(
            RiskProfile profile,
            PositionService positionService,
            PortfolioEngine portfolioEngine,
            IBrokerConnection brokerConnection,
            OrderManagementService orderManagementService
    ) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(positionService, "positionService");
        Objects.requireNonNull(portfolioEngine, "portfolioEngine");
        Objects.requireNonNull(orderManagementService, "orderManagementService");

        // P3.3: PositionService is the canonical event-sourced position source.
        // The legacy EventSourcedNetPositionProvider is no longer wired into
        // the composition path — PositionRiskHandler now takes PositionService
        // directly. The legacy class is still used by replay engine and
        // broker startup orchestrator (separate consumers, not migrated yet).
        CaffeineIdempotencyCache idempotencyCache = new CaffeineIdempotencyCache();

        boolean enforceMargin = profile.enforceMargin();
        Duration marginCacheTtl = Duration.ofMinutes(Math.max(1, profile.marginCacheTtlMinutes()));
        MarginProvider marginProvider = brokerConnection == null
                ? null
                : brokerConnection.getCapability(MarginProvider.class).orElse(null);
        PortfolioProvider portfolioProvider = brokerConnection == null
                ? null
                : brokerConnection.getCapability(PortfolioProvider.class).orElse(null);
        MarginEnforcementHandler marginEnforcementHandler = new MarginEnforcementHandler(
                enforceMargin, marginProvider, portfolioProvider, marginCacheTtl
        );

        KillSwitchCoordinator killSwitchCoordinator = new KillSwitchCoordinator(brokerConnection, orderManagementService);

        // P3.3: pass PositionService (the canonical source) to PositionRiskHandler.
        PositionRiskHandler positionRiskHandler = new PositionRiskHandler(
                profile.limits(),
                positionService,
                portfolioEngine,
                marginEnforcementHandler,
                killSwitchCoordinator
        );

        log.info("ExecutionComposition created (enforceMargin={}, marginCacheTtlMinutes={})",
                enforceMargin, profile.marginCacheTtlMinutes());

        return new ExecutionComposition(
                profile,
                positionService,
                idempotencyCache,
                marginEnforcementHandler,
                killSwitchCoordinator,
                positionRiskHandler
        );
    }

    public RiskProfile profile() {
        return profile;
    }

    public PositionService positionService() {
        return positionService;
    }

    public CaffeineIdempotencyCache idempotencyCache() {
        return idempotencyCache;
    }

    public MarginEnforcementHandler marginEnforcementHandler() {
        return marginEnforcementHandler;
    }

    public KillSwitchCoordinator killSwitchCoordinator() {
        return killSwitchCoordinator;
    }

    public PositionRiskHandler positionRiskHandler() {
        return positionRiskHandler;
    }

    /**
     * Helper for callers that need a {@link java.util.Optional} view of the broker connection's
     * margin provider — useful for the Spring path that uses {@code ObjectProvider}.
     */
    public static Optional<MarginProvider> resolveMarginProvider(IBrokerConnection connection) {
        return connection == null ? Optional.empty() : connection.getCapability(MarginProvider.class);
    }
}
