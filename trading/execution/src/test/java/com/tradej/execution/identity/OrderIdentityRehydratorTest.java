package com.tradej.execution.identity;

import com.tradej.core.domain.oms.OrderAcknowledged;
import com.tradej.core.domain.oms.OrderSubmitted;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class OrderIdentityRehydratorTest {

    private Path tempDir;
    private EventSourcedOrderRepository omsRepo;
    private OrderIdentityRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("oms-rehydrate-");
        omsRepo = new EventSourcedOrderRepository(tempDir);
        registry = new OrderIdentityRegistry();
    }

    @AfterEach
    void tearDown() {
        omsRepo.close();
    }

    @Test
    void rehydratesBrokerAndSignalMappings() {
        omsRepo.append(OrderSubmitted.create("ORD-1", "SIG-1", "SBIN", 10));
        omsRepo.append(OrderAcknowledged.event("ORD-1", "BRK-1"));

        int count = OrderIdentityRehydrator.rehydrate(omsRepo, registry);

        assertEquals(1, count);
        assertEquals("ORD-1", registry.resolveInternalId("BRK-1"));
        assertEquals("ORD-1", registry.resolveBySignalId("SIG-1"));
    }
}
