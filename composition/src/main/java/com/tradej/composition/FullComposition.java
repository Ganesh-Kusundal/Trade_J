package com.tradej.composition;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.composition.config.BrokerProfile;
import com.tradej.composition.config.RiskProfile;
import com.tradej.composition.config.StorageProfile;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.strategy.portfolio.PortfolioEngine;

/**
 * Aggregates all composition roots into a single entry point.
 * Provides a fully wired system without Spring Boot.
 *
 * <p>Usage:
 * <pre>
 *   FullComposition system = FullComposition.create(brokerProfile, storageProfile, riskProfile);
 *   IBrokerConnection broker = system.broker().brokerConnection();
 *   PositionRiskHandler risk = system.execution().positionRiskHandler();
 * </pre>
 */
public final class FullComposition {

    private final BrokerComposition broker;
    private final DataComposition data;
    private final ExecutionComposition execution;

    private FullComposition(
            BrokerComposition broker,
            DataComposition data,
            ExecutionComposition execution
    ) {
        this.broker = broker;
        this.data = data;
        this.execution = execution;
    }

    public static FullComposition create(
            BrokerProfile brokerProfile,
            StorageProfile storageProfile,
            RiskProfile riskProfile
    ) {
        BrokerComposition broker = BrokerComposition.create(brokerProfile);
        DataComposition data = DataComposition.create(storageProfile);

        return new FullComposition(broker, data, null);
    }

    /**
     * Creates a fully wired system including ExecutionComposition.
     * Use this for live trading and backtesting where risk enforcement,
     * position tracking, and order management are required.
     *
     * @param brokerProfile broker configuration
     * @param storageProfile storage configuration
     * @param riskProfile risk limits and enforcement settings
     * @param portfolioEngine portfolio engine (null to create a default)
     * @param oms order management service (null to create a default)
     */
    public static FullComposition createFull(
            BrokerProfile brokerProfile,
            StorageProfile storageProfile,
            RiskProfile riskProfile,
            PortfolioEngine portfolioEngine,
            OrderManagementService oms
    ) {
        BrokerComposition broker = BrokerComposition.create(brokerProfile);
        DataComposition data = DataComposition.create(storageProfile);
        IBrokerConnection connection = broker.brokerConnection();

        PortfolioEngine effectivePortfolio = portfolioEngine != null
                ? portfolioEngine : new PortfolioEngine();
        OrderManagementService effectiveOms = oms != null
                ? oms : new OrderManagementService(connection, new RuntimeModeHolder(), new LiveTradingClock(), null);

        ExecutionComposition execution = ExecutionComposition.create(
                riskProfile, effectivePortfolio, connection, effectiveOms);

        return new FullComposition(broker, data, execution);
    }

    /**
     * Creates a fully wired system with default portfolio engine and OMS.
     */
    public static FullComposition createFull(
            BrokerProfile brokerProfile,
            StorageProfile storageProfile,
            RiskProfile riskProfile
    ) {
        return createFull(brokerProfile, storageProfile, riskProfile, null, null);
    }

    public static FullComposition brokerOnly(BrokerProfile brokerProfile) {
        BrokerComposition broker = BrokerComposition.create(brokerProfile);
        return new FullComposition(broker, null, null);
    }

    public BrokerComposition broker() {
        return broker;
    }

    public DataComposition data() {
        return data;
    }

    public ExecutionComposition execution() {
        return execution;
    }

    public IBrokerConnection brokerConnection() {
        return broker.brokerConnection();
    }

    public BrokerLifecycleManager lifecycleManager() {
        return broker.lifecycleManager();
    }
}
