package com.tradej.composition;

import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.execution.position.EventSourcedNetPositionProvider;
import com.tradej.execution.risk.KillSwitchCoordinator;
import com.tradej.execution.risk.MarginEnforcementHandler;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.IdempotencyCachePort;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.composition.config.RiskProfile;
import com.tradej.strategy.portfolio.PortfolioEngine;

import java.time.Duration;

public final class ExecutionComposition {

    private final EventSourcedNetPositionProvider netPositionProvider;
    private final PositionRiskHandler positionRiskHandler;
    private final MarginEnforcementHandler marginEnforcementHandler;
    private final KillSwitchCoordinator killSwitchCoordinator;
    private final IdempotencyCachePort idempotencyCache;

    private ExecutionComposition(
            EventSourcedNetPositionProvider netPositionProvider,
            PositionRiskHandler positionRiskHandler,
            MarginEnforcementHandler marginEnforcementHandler,
            KillSwitchCoordinator killSwitchCoordinator,
            IdempotencyCachePort idempotencyCache
    ) {
        this.netPositionProvider = netPositionProvider;
        this.positionRiskHandler = positionRiskHandler;
        this.marginEnforcementHandler = marginEnforcementHandler;
        this.killSwitchCoordinator = killSwitchCoordinator;
        this.idempotencyCache = idempotencyCache;
    }

    public static ExecutionComposition create(
            RiskProfile riskProfile,
            PortfolioEngine portfolioEngine,
            IBrokerConnection brokerConnection,
            OrderManagementService orderManagementService
    ) {
        EventSourcedNetPositionProvider netPositionProvider = new EventSourcedNetPositionProvider();
        IdempotencyCachePort idempotencyCache = new CaffeineIdempotencyCache();

        MarginProvider margin = brokerConnection == null ? null
                : brokerConnection.getCapability(MarginProvider.class).orElse(null);
        PortfolioProvider portfolio = brokerConnection == null ? null
                : brokerConnection.getCapability(PortfolioProvider.class).orElse(null);

        MarginEnforcementHandler marginHandler = new MarginEnforcementHandler(
                riskProfile.enforceMargin(),
                margin,
                portfolio,
                Duration.ofMinutes(Math.max(1, riskProfile.marginCacheTtlMinutes()))
        );

        KillSwitchCoordinator killSwitch = new KillSwitchCoordinator(
                brokerConnection, orderManagementService);

        PositionRiskHandler riskHandler = new PositionRiskHandler(
                riskProfile.limits(),
                netPositionProvider,
                portfolioEngine,
                marginHandler,
                killSwitch
        );

        return new ExecutionComposition(
                netPositionProvider, riskHandler, marginHandler, killSwitch, idempotencyCache);
    }

    public EventSourcedNetPositionProvider netPositionProvider() {
        return netPositionProvider;
    }

    public PositionRiskHandler positionRiskHandler() {
        return positionRiskHandler;
    }

    public MarginEnforcementHandler marginEnforcementHandler() {
        return marginEnforcementHandler;
    }

    public KillSwitchCoordinator killSwitchCoordinator() {
        return killSwitchCoordinator;
    }

    public IdempotencyCachePort idempotencyCache() {
        return idempotencyCache;
    }
}
