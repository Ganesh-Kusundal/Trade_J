package com.tradej.execution.identity;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("unit")
class OrderIdentityRegistryTest {

    @Test
    void registerAndResolveByBrokerId() {
        var reg = new OrderIdentityRegistry();
        reg.register("ORD-1", "D123", "SIG-1");
        assertEquals("ORD-1", reg.resolveInternalId("D123"));
    }

    @Test
    void registerAndResolveBySignalId() {
        var reg = new OrderIdentityRegistry();
        reg.register("ORD-1", null, "SIG-1");
        assertEquals("ORD-1", reg.resolveBySignalId("SIG-1"));
    }

    @Test
    void acknowledgeCompletesMapping() {
        var reg = new OrderIdentityRegistry();
        reg.register("ORD-1", null, "SIG-1");
        assertNull(reg.resolveInternalId("D123"));

        reg.acknowledge("ORD-1", "D123");
        assertEquals("ORD-1", reg.resolveInternalId("D123"));
        assertEquals("D123", reg.resolveBrokerOrderId("ORD-1"));
    }

    @Test
    void removeCleansAllMaps() {
        var reg = new OrderIdentityRegistry();
        reg.register("ORD-1", "D123", "SIG-1");
        reg.remove("ORD-1");

        assertNull(reg.resolveInternalId("D123"));
        assertNull(reg.resolveBySignalId("SIG-1"));
        assertNull(reg.resolveBrokerOrderId("ORD-1"));
        assertEquals(0, reg.size());
    }

    @Test
    void acknowledgeOverwritesExistingBrokerMapping() {
        var reg = new OrderIdentityRegistry();
        reg.register("ORD-1", "D123", "SIG-1");
        // Acknowledge with same ID should be fine
        reg.acknowledge("ORD-1", "D123");
        assertEquals("ORD-1", reg.resolveInternalId("D123"));
    }
}
