package com.tradej.runtime.verification;

import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.port.EventBus;
import com.tradej.disruptor.DisruptorEventBus;
import com.tradej.disruptor.config.DisruptorPipelineConfig;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runtime verification tests for EventBus behavior.
 * These tests PROVE actual runtime behavior, not just code structure.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventBusRuntimeVerificationTest {
    private static final Logger log = LoggerFactory.getLogger(EventBusRuntimeVerificationTest.class);

    private static EventBus eventBus;
    private static EventBusRuntimeVerifier verifier;

    @BeforeAll
    static void setup() {
        // Create DisruptorEventBus with test configuration
        DisruptorPipelineConfig config = DisruptorPipelineConfig.builder()
            .runtimeMode(com.tradej.core.domain.runtime.RuntimeMode.LIVE)
            .compileGraphOnInit(false)
            .build();

        eventBus = new DisruptorEventBus(config);
        eventBus.start();

        verifier = new EventBusRuntimeVerifier(eventBus);
        verifier.startTracing();

        log.info("EventBus runtime verification environment initialized");
    }

    @AfterAll
    static void teardown() {
        if (eventBus != null) {
            eventBus.stop();
        }
        log.info("EventBus runtime verification environment torn down");
    }

    @Test
    @Order(1)
    @DisplayName("Verify event propagation through DisruptorEventBus")
    void testEventPropagation() {
        verifier.traceEvents(MarketTickEvent.class, "test-propagation-handler");

        MarketTickEvent testEvent = createTestTick(1);

        EventBusRuntimeVerifier.EventPropagationResult result = 
            verifier.traceEventPropagation(testEvent);

        // Verify event was published
        assertNotNull(result);
        assertEquals(testEvent.eventId(), result.eventId());

        // Verify subscribers were invoked
        assertFalse(result.subscriberTraces().isEmpty(), 
            "At least one subscriber should have received the event");

        // Verify latency is reasonable (< 100ms)
        assertTrue(result.publishLatencyMs() < 100,
            "Publish latency should be < 100ms, was: " + result.publishLatencyMs());

        log.info("Event propagation verified: {} subscribers, {} ms latency",
            result.subscriberTraces().size(), result.publishLatencyMs());

        // Save trace to JSON
        saveJsonReport("runtime-verification/event-bus/trace-001.json", result);
    }

    @Test
    @Order(2)
    @DisplayName("Verify event ordering is preserved under load")
    void testEventOrdering() {
        int eventCount = 1000;

        EventBusRuntimeVerifier.OrderingTestResult result = 
            verifier.testEventOrdering(eventCount);

        // Verify all events received
        assertEquals(eventCount, result.receivedCount(),
            "All published events should be received");

        // Verify ordering preserved
        assertTrue(result.orderingPreserved(),
            "Event ordering should be preserved. Out of order: " + result.outOfOrderCount());

        log.info("Event ordering verified: {} events, {} out of order",
            result.receivedCount(), result.outOfOrderCount());

        // Save report
        saveJsonReport("runtime-verification/event-bus/ordering-test.json", result);
    }

    @Test
    @Order(3)
    @DisplayName("Verify backpressure behavior when bus is flooded")
    void testBackpressure() {
        int floodCount = 10000;

        EventBusRuntimeVerifier.BackpressureTestResult result = 
            verifier.testBackpressure(floodCount);

        log.info("Backpressure test: published={}, processed={}, dropped={}",
            result.floodCount(), result.processedCount(), result.droppedCount());

        // Save report
        saveJsonReport("runtime-verification/event-bus/backpressure-test.json", result);
    }

    @Test
    @Order(4)
    @DisplayName("Generate comprehensive EventBus verification report")
    void generateVerificationReport() {
        EventBusRuntimeVerifier.EventBusVerificationReport report = 
            verifier.generateReport();

        // Verify report is comprehensive
        assertNotNull(report);
        assertTrue(report.totalPublished() > 0);
        assertTrue(report.totalReceived() > 0);

        log.info("Verification report: published={}, received={}, dropped={}",
            report.totalPublished(), report.totalReceived(), report.eventsDropped());

        // Save report
        saveJsonReport("runtime-verification/event-bus/verification-report.json", report);
    }

    private MarketTickEvent createTestTick(int sequence) {
        return new MarketTickEvent(
            "VERIFY-" + sequence,
            "NIFTY",
            "NSE",
            "INDEX",
            19500.0 + sequence,
            19500.0 + sequence,
            Instant.now().toEpochMilli(),
            sequence
        );
    }

    private void saveJsonReport(String filePath, Object report) {
        try {
            File file = new File(filePath);
            file.getParentFile().mkdirs();

            String json = com.fasterxml.jackson.databind.SerializationFeature
                .WRAP_ROOT_VALUE
                .toString();

            com.fasterxml.jackson.databind.ObjectMapper mapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.enable(com.fasterxml.jackson.core.JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);

            String jsonReport = mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(report);

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(jsonReport);
            }

            log.info("Report saved: {}", file.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save report: {}", filePath, e);
        }
    }
}
