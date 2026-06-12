package com.tradej.app.contract;

import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.DomainEventVisitor;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.gateway.protocol.GatewayTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Contract test: ensures backward compatibility of core platform contracts.
 * <p>
 * Guards against accidental removal of record components, interface methods,
 * and enum constants that downstream modules depend on.
 */
@Tag("contract")
class BackwardCompatibilityTest {

    // ── Test 1 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("EventMetadata record must retain all its existing fields")
    void eventMetadataMustRetainFields() {
        // These are the fields that downstream code depends on — never remove them.
        List<String> requiredFields = List.of(
                "eventId",
                "timestampMs",
                "timestampMonotonic",
                "sequenceId",
                "correlationId",
                "schemaVersion"
        );

        RecordComponent[] components = EventMetadata.class.getRecordComponents();
        Set<String> actualFields = Arrays.stream(components)
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        List<String> missing = requiredFields.stream()
                .filter(f -> !actualFields.contains(f))
                .toList();

        assertTrue(missing.isEmpty(),
                "EventMetadata is missing required record components: " + missing
                        + " — removing fields breaks backward compatibility");

        // Also verify types for key fields
        for (RecordComponent rc : components) {
            switch (rc.getName()) {
                case "eventId" -> assertTrue(rc.getType().equals(String.class),
                        "EventMetadata.eventId must be String");
                case "timestampMs" -> assertTrue(rc.getType().equals(long.class),
                        "EventMetadata.timestampMs must be long");
                case "timestampMonotonic" -> assertTrue(rc.getType().equals(long.class),
                        "EventMetadata.timestampMonotonic must be long");
                case "sequenceId" -> assertTrue(rc.getType().equals(long.class),
                        "EventMetadata.sequenceId must be long");
                case "correlationId" -> assertTrue(rc.getType().equals(String.class),
                        "EventMetadata.correlationId must be String");
                case "schemaVersion" -> assertTrue(rc.getType().equals(int.class),
                        "EventMetadata.schemaVersion must be int");
            }
        }
    }

    // ── Test 2 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("DomainEvent interface must retain all required methods")
    void domainEventMustRetainMethods() {
        // These methods are part of the public contract — downstream code depends on them.
        List<String[]> requiredMethods = List.of(
                new String[]{"metadata", "com.tradej.core.domain.event.EventMetadata"},
                new String[]{"eventId", "java.lang.String"},
                new String[]{"timestampMs", "long"},
                new String[]{"correlationId", "java.lang.String"},
                new String[]{"schemaVersion", "int"},
                new String[]{"accept", "void"}
        );

        List<String> missing = new java.util.ArrayList<>();

        for (String[] methodSpec : requiredMethods) {
            String methodName = methodSpec[0];
            String expectedReturn = methodSpec[1];
            Method found = findMethod(DomainEvent.class, methodName);
            if (found == null) {
                missing.add(methodName + " — method not found");
            } else if (!found.getReturnType().getName().equals(expectedReturn)) {
                missing.add(methodName + " — return type is "
                        + found.getReturnType().getName() + ", expected " + expectedReturn);
            }
        }

        assertTrue(missing.isEmpty(),
                "DomainEvent interface is missing required methods:\n  "
                        + String.join("\n  ", missing));
    }

    // ── Test 3 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GatewayTopic enum must not remove existing constants")
    void gatewayTopicMustRetainConstants() {
        // All 17 known constants — removing any breaks wire protocol compatibility.
        List<String> requiredConstants = List.of(
                "MARKET_TICK",
                "MARKET_DEPTH",
                "CANDLE_DEVELOPING",
                "CANDLE_CLOSED",
                "ORDER_UPDATE",
                "POSITION_UPDATE",
                "STRATEGY_SIGNAL",
                "PNL_UPDATE",
                "REPLAY_CONTROL",
                "PIPELINE_HEALTH",
                "SCAN_COMPLETED",
                "DEPTH_IMBALANCE",
                "HEATMAP_CHUNK",
                "ICEBERG_ALERT",
                "ABSORPTION_ALERT",
                "SR_LEVELS_UPDATE",
                "ORDER_BOOK_SNAPSHOT"
        );

        Set<String> actualConstants = Arrays.stream(GatewayTopic.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        List<String> missing = requiredConstants.stream()
                .filter(c -> !actualConstants.contains(c))
                .toList();

        assertTrue(missing.isEmpty(),
                "GatewayTopic enum is missing required constants: " + missing
                        + " — removing enum constants breaks wire protocol compatibility");

        // Verify total count matches expectation
        assertTrue(actualConstants.size() >= 17,
                "GatewayTopic should have at least 17 constants, found " + actualConstants.size());
    }

    // ── Test 4 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("BrokerSource enum must retain existing constants")
    void brokerSourceMustRetainConstants() {
        // Core broker sources that downstream routing and persistence depend on.
        List<String> requiredConstants = List.of(
                "DHAN",
                "UPSTOX",
                "ICICI",
                "SIMULATION"
        );

        Set<String> actualConstants = Arrays.stream(BrokerSource.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        List<String> missing = requiredConstants.stream()
                .filter(c -> !actualConstants.contains(c))
                .toList();

        assertTrue(missing.isEmpty(),
                "BrokerSource enum is missing required constants: " + missing
                        + " — removing enum constants breaks broker routing compatibility");
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * Finds a method by name on the given class (any parameter count).
     * Returns null if not found.
     */
    private static Method findMethod(Class<?> clazz, String name) {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }
}
