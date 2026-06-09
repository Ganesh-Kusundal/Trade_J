package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.time.TradingClock;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.execution.service.TradingCircuitBreaker;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("cross-layer")
class ExecutionToSandboxBrokerIntegrationTest {
    private DhanBrokerConnection brokerConnection;
    private ExecutionHandler executionHandler;
    private EventSourcedOrderRepository omsRepository;
    private String placedBrokerOrderId;

    @AfterEach
    void tearDown() {
        if (executionHandler != null) {
            executionHandler.stop();
        }
        if (placedBrokerOrderId != null && brokerConnection != null) {
            try {
                brokerConnection.orders().cancelOrder(placedBrokerOrderId);
            } catch (Exception ignored) {
            }
        }
        LiveDhanTestSupport.cancelTrackedSandboxOrders(brokerConnection);
        if (omsRepository != null) {
            omsRepository.close();
        }
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void executionHandlerPlacesAndCancelsOnSandboxBroker() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_CROSS_LAYER_TEST_ENABLED", "dhan.crossLayerTestEnabled", "false")),
                "Set DHAN_CROSS_LAYER_TEST_ENABLED=true to run cross-layer sandbox execution tests.");

        String symbol = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SYMBOL", "dhan.testOrderSymbol", "TCS");
        String securityId = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SECURITY_ID", "dhan.testOrderSecurityId", "11536");
        String segmentCode = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SEGMENT", "dhan.testOrderSegment", "NSE_EQ");
        String exchangeCode = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_EXCHANGE", "dhan.testOrderExchange", "NSE");

        brokerConnection = new DhanBrokerConnection(
                LiveDhanTestSupport.sandboxConnectionSettingsOrSkip(),
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadInstrumentCatalog(writeCatalog(symbol, exchangeCode, segmentCode, securityId));

        Path omsPath = Files.createTempDirectory("cross-layer-oms");
        omsRepository = new EventSourcedOrderRepository(omsPath);
        var runtimeModeHolder = new com.tradej.core.domain.runtime.RuntimeModeHolder();
        TradingClock clock = new LiveTradingClock();
        List<DomainEvent> emitted = new ArrayList<>();
        executionHandler = new ExecutionHandler(
                new OrderManagementService(brokerConnection, runtimeModeHolder, clock, omsRepository),
                runtimeModeHolder,
                clock,
                new TradingCircuitBreaker(),
                new OrderIdentityRegistry(),
                com.tradej.core.domain.port.DeadLetterQueue.noop(),
                com.tradej.execution.service.ExecutionConfig.DEFAULTS.withDownstream(emitted::add)
        );
        executionHandler.start();

        String correlationId = "xlayer" + System.currentTimeMillis();
        OrderRequest orderRequest = new OrderRequest(
                symbol,
                ExchangeSegment.valueOf(segmentCode),
                Side.BUY,
                1L,
                OrderType.MARKET,
                0L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                correlationId
        );

        executionHandler.onDomainEvent(new SignalPendingExecution(
                EventMetadata.root(),
                "sig-xlayer",
                orderRequest,
                Map.of()
        ));

        awaitOrderAccepted(emitted, 30);
        OrderAccepted accepted = emitted.stream()
                .filter(OrderAccepted.class::isInstance)
                .map(OrderAccepted.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected OrderAccepted, got: " + emitted));

        placedBrokerOrderId = accepted.order().orderId();
        assertFalse(placedBrokerOrderId.isBlank());
        assertTrue(brokerConnection.orders().cancelOrder(placedBrokerOrderId));
    }

    private Path writeCatalog(String symbol, String exchange, String segment, String securityId) {
        try {
            Path file = Files.createTempFile("dhan-cross-layer-catalog", ".csv");
            Files.writeString(file, """
                    symbol,exchange,exchangeSegment,securityId
                    %s,%s,%s,%s
                    """.formatted(symbol, exchange, segment, securityId));
            return file;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create temporary Dhan catalog", ex);
        }
    }

    private static void awaitOrderAccepted(List<DomainEvent> emitted, long timeoutSeconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            if (emitted.stream().anyMatch(OrderAccepted.class::isInstance)) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("Timed out waiting for OrderAccepted; got " + emitted);
    }
}
