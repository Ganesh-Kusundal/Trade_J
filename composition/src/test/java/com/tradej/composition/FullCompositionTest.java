package com.tradej.composition;

import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.composition.config.BrokerProfile;
import com.tradej.composition.config.RiskProfile;
import com.tradej.composition.config.StorageProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class FullCompositionTest {

    private static BrokerProfile testBrokerProfile() {
        return new BrokerProfile(
                BrokerProfile.BrokerType.DHAN,
                new BrokerProfile.DhanConfig("test-client", "test-token",
                        DhanApiEnvironment.SANDBOX, null, DhanAuthMode.STATIC,
                        null, null, null, 30, false, null),
                null, null
        );
    }

    @Test
    void brokerProfileGenericConfigFiltersNullOptionalValues() {
        Map<String, Object> config = testBrokerProfile().toGenericConfig();

        assertEquals("DHAN", config.get("brokerType"));
        assertEquals("test-client", config.get("clientId"));
        assertEquals("test-token", config.get("accessToken"));
        assertFalse(config.containsKey("restBaseUrl"));
        assertFalse(config.containsKey("pinFile"));
        assertFalse(config.containsKey("totpSecretFile"));
        assertFalse(config.containsKey("tokenStateFile"));
        assertFalse(config.containsKey("instrumentCacheDirectory"));
    }

    @Test
    void brokerOnly_executionIsNull() {
        FullComposition system = FullComposition.brokerOnly(testBrokerProfile());
        assertNotNull(system.broker(), "Broker composition should be wired");
        assertNull(system.data(), "Data composition should be null for brokerOnly");
        assertNull(system.execution(), "Execution composition should be null for brokerOnly");
    }

    @Test
    void createFull_wiresExecutionComposition() {
        FullComposition system = FullComposition.createFull(
                testBrokerProfile(), StorageProfile.defaults(), RiskProfile.defaults());
        assertNotNull(system.broker(), "Broker composition should be wired");
        assertNotNull(system.data(), "Data composition should be wired");
        assertNotNull(system.execution(), "Execution composition should be wired via createFull");
        assertNotNull(system.execution().positionRiskHandler(), "PositionRiskHandler should be available");
        assertNotNull(system.execution().marginEnforcementHandler(), "MarginEnforcementHandler should be available");
    }

    @Test
    void create_preservesBackwardCompatibility() {
        FullComposition system = FullComposition.create(
                testBrokerProfile(), StorageProfile.defaults(), RiskProfile.defaults());
        assertNotNull(system.broker(), "Broker composition should be wired");
        assertNotNull(system.data(), "Data composition should be wired");
        assertNull(system.execution(), "Legacy create() should not wire execution (backward compat)");
    }
}
