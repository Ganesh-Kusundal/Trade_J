package com.tradej.composition;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.composition.config.BrokerProfile;
import com.tradej.composition.config.RiskProfile;
import com.tradej.composition.config.StorageProfile;

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
