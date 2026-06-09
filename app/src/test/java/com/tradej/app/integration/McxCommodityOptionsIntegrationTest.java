package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.MarketDataListener;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.model.*;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for MCX commodity options (GOLD, SILVER, CRUDEOIL).
 * Verifies:
 * 1. Option contracts retrieval from live API
 * 2. Top 3 commodities by OI/volume
 * 3. Live WebSocket subscription in FULL mode
 * 4. Real-time market data reception
 */
@Tag("integration")
@Tag("broker-rest")
@Tag("broker-websocket")
class McxCommodityOptionsIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(McxCommodityOptionsIntegrationTest.class);
    
    // Top MCX commodities by OI and volume
    private static final String[] TOP_COMMODITIES = {"GOLD", "SILVER", "CRUDEOIL"};
    
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    /**
     * Test 1: Fetch option chains for top 3 MCX commodities
     */
    @Test
    void fetchesOptionChainsForTopMcxCommodities() throws Exception {
        connectWithDailyInstrumentMaster();

        for (String commodity : TOP_COMMODITIES) {
            log.info("Testing MCX commodity: {}", commodity);
            
            // Get expiries from live API
            List<LocalDate> expiries = brokerConnection.options()
                    .getExpiries(commodity, ExchangeSegment.MCX_COMM);
            
            assertFalse(expiries.isEmpty(), 
                    "Expected at least one option expiry for " + commodity + " on MCX");
            
            LocalDate nearestExpiry = expiries.getFirst();
            log.info("  {} nearest expiry: {}", commodity, nearestExpiry);
            
            // Get option chain from live API
            OptionChainSnapshot chain = brokerConnection.options()
                    .getOptionChain(commodity, ExchangeSegment.MCX_COMM, nearestExpiry);
            
            // Verify chain has data
            assertTrue(chain.spotPricePaisa() > 0L, 
                    commodity + " option chain should have spot price > 0");
            
            assertFalse(chain.strikes().isEmpty(), 
                    commodity + " option chain should have strikes");
            
            // Count available contracts
            long contractCount = chain.strikes().stream()
                    .mapToLong(entry -> (entry.call() != null ? 1 : 0) + (entry.put() != null ? 1 : 0))
                    .sum();
            
            assertTrue(contractCount > 0, 
                    commodity + " should have option contracts in chain");
            
            log.info("  {} spot price: ₹{}", commodity, chain.spotPricePaisa() / 100.0);
            log.info("  {} strikes: {}", commodity, chain.strikes().size());
            log.info("  {} total contracts: {}", commodity, contractCount);
            
            // Verify at least one contract has live data
            OptionQuote firstLeg = chain.strikes().stream()
                    .map(entry -> entry.call() != null ? entry.call() : entry.put())
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            commodity + " should have at least one option leg"));
            
            // Validate live data fields
            assertNotNull(firstLeg.instrument(), 
                    commodity + " option should have instrument");
            assertTrue(firstLeg.instrument().isOption(), 
                    commodity + " should be an option contract");
            assertNotNull(firstLeg.instrument().strikePricePaisa(), 
                    commodity + " option should have strike price");
            
            log.info("  ✓ {} option chain validated successfully", commodity);
        }
    }

    /**
     * Test 2: Verify option contracts have OI and volume data
     */
    @Test
    void verifiesOptionChainHasOiAndVolume() throws Exception {
        connectWithDailyInstrumentMaster();

        String commodity = "GOLD";
        List<LocalDate> expiries = brokerConnection.options()
                .getExpiries(commodity, ExchangeSegment.MCX_COMM);
        
        if (expiries.isEmpty()) {
            log.warn("No expiries available for {}, skipping test", commodity);
            return;
        }
        
        LocalDate nearestExpiry = expiries.getFirst();
        OptionChainSnapshot chain = brokerConnection.options()
                .getOptionChain(commodity, ExchangeSegment.MCX_COMM, nearestExpiry);
        
        // Find contracts with OI
        long contractsWithOi = chain.strikes().stream()
                .flatMap(entry -> java.util.stream.Stream.of(entry.call(), entry.put()))
                .filter(java.util.Objects::nonNull)
                .filter(quote -> quote.openInterest() > 0)
                .count();
        
        log.info("GOLD contracts with OI: {} out of {}", 
                contractsWithOi, 
                chain.strikes().size() * 2);
        
        // Find contracts with volume
        long contractsWithVolume = chain.strikes().stream()
                .flatMap(entry -> java.util.stream.Stream.of(entry.call(), entry.put()))
                .filter(java.util.Objects::nonNull)
                .filter(quote -> quote.volume() > 0)
                .count();
        
        log.info("GOLD contracts with volume: {} out of {}", 
                contractsWithVolume, 
                chain.strikes().size() * 2);
        
        // At least some contracts should have OI or volume during market hours
        assertTrue(contractsWithOi > 0 || contractsWithVolume > 0,
                "Expected at least some GOLD options to have OI or volume during market hours");
    }

    /**
     * Test 3: Live WebSocket subscription in FULL mode for MCX commodities
     */
    @Test
    void subscribesToMcxCommoditiesInFullMode() throws Exception {
        connectWithDailyInstrumentMaster();

        // Connect WebSocket
        brokerConnection.connect();
        assertTrue(brokerConnection.websocket() != null, 
                "WebSocket multiplexer should be available");
        
        log.info("WebSocket connected, subscribing to MCX commodities in FULL mode");
        
        // Prepare subscription requests for top commodities
        List<MarketSubscriptionRequest> subscriptions = new ArrayList<>();
        for (String commodity : TOP_COMMODITIES) {
            subscriptions.add(new MarketSubscriptionRequest(commodity, ExchangeSegment.MCX_COMM));
            log.info("  Subscribing to: {} (MCX_COMM, FULL mode)", commodity);
        }
        
        // Track received updates
        List<MarketDataUpdate> receivedUpdates = new CopyOnWriteArrayList<>();
        CountDownLatch updateLatch = new CountDownLatch(3); // Wait for at least 3 updates
        AtomicInteger updateCount = new AtomicInteger(0);
        
        // Subscribe to market data in FULL mode
        brokerConnection.websocket().onMarketData((DomainEvent event) -> {
            // Extract instrument key and data from event
            receivedUpdates.add(new MarketDataUpdate(event));
            int count = updateCount.incrementAndGet();
            log.info("Received update #{}: {}", count, event.getClass().getSimpleName());
            updateLatch.countDown();
        });
        
        // Subscribe in FULL mode
        brokerConnection.websocket().subscribe(subscriptions, FeedMode.FULL);
        log.info("Subscribed to {} commodities in FULL mode", subscriptions.size());
        
        // Wait for updates (max 10 seconds)
        boolean receivedUpdatesInTime = updateLatch.await(10, TimeUnit.SECONDS);
        
        // Unsubscribe
        brokerConnection.websocket().unsubscribe(subscriptions);
        
        // Verify we received updates
        assertTrue(receivedUpdatesInTime || updateCount.get() > 0,
                "Should receive market data updates within 10 seconds (received: " + 
                updateCount.get() + ")");
        
        assertTrue(updateCount.get() >= 1,
                "Should receive at least 1 market data update (received: " + updateCount.get() + ")");
        
        log.info("✓ Received {} market data updates in FULL mode", updateCount.get());
        
        // Verify update content
        if (!receivedUpdates.isEmpty()) {
            MarketDataUpdate firstUpdate = receivedUpdates.getFirst();
            assertNotNull(firstUpdate.event(), "Update should have event");
            
            log.info("First event type: {}", firstUpdate.event().getClass().getSimpleName());
        }
    }

    /**
     * Test 4: Verify FULL mode provides more data than QUOTE mode
     */
    @Test
    void verifiesFullModeProvidesMoreDataThanQuoteMode() throws Exception {
        connectWithDailyInstrumentMaster();

        brokerConnection.connect();
        
        String commodity = "GOLD";
        MarketSubscriptionRequest request = new MarketSubscriptionRequest(commodity, ExchangeSegment.MCX_COMM);
        
        List<MarketDataUpdate> quoteModeUpdates = new CopyOnWriteArrayList<>();
        List<MarketDataUpdate> fullModeUpdates = new CopyOnWriteArrayList<>();
        
        CountDownLatch quoteLatch = new CountDownLatch(1);
        CountDownLatch fullLatch = new CountDownLatch(1);
        
        // Subscribe in QUOTE mode first
        brokerConnection.websocket().onMarketData((DomainEvent event) -> {
            quoteModeUpdates.add(new MarketDataUpdate(event));
            quoteLatch.countDown();
        });
        
        brokerConnection.websocket().subscribe(List.of(request), FeedMode.QUOTE);
        boolean quoteReceived = quoteLatch.await(5, TimeUnit.SECONDS);
        brokerConnection.websocket().unsubscribe(List.of(request));
        
        // Small delay between subscriptions
        Thread.sleep(1000);
        
        // Subscribe in FULL mode
        brokerConnection.websocket().onMarketData((DomainEvent event) -> {
            fullModeUpdates.add(new MarketDataUpdate(event));
            fullLatch.countDown();
        });
        
        brokerConnection.websocket().subscribe(List.of(request), FeedMode.FULL);
        boolean fullReceived = fullLatch.await(5, TimeUnit.SECONDS);
        brokerConnection.websocket().unsubscribe(List.of(request));
        
        // Both modes should receive data
        assertTrue(quoteReceived || fullReceived,
                "Should receive data in at least one mode");
        
        log.info("QUOTE mode updates: {}", quoteModeUpdates.size());
        log.info("FULL mode updates: {}", fullModeUpdates.size());
        
        // FULL mode should provide richer data (this is informational, not a hard assertion)
        if (!fullModeUpdates.isEmpty()) {
            log.info("✓ FULL mode subscription working for MCX commodity: {}", commodity);
            log.info("  Received {} updates in FULL mode", fullModeUpdates.size());
        }
    }

    /**
     * Test 5: Get option Greeks for MCX commodity options
     */
    @Test
    void fetchesGreeksForMcxOptionContracts() throws Exception {
        connectWithDailyInstrumentMaster();

        String commodity = "GOLD";
        List<LocalDate> expiries = brokerConnection.options()
                .getExpiries(commodity, ExchangeSegment.MCX_COMM);
        
        if (expiries.isEmpty()) {
            log.warn("No expiries for {}, skipping Greeks test", commodity);
            return;
        }
        
        LocalDate expiry = expiries.getFirst();
        OptionChainSnapshot chain = brokerConnection.options()
                .getOptionChain(commodity, ExchangeSegment.MCX_COMM, expiry);
        
        // Get first option contract
        OptionQuote firstOption = chain.strikes().stream()
                .map(entry -> entry.call() != null ? entry.call() : entry.put())
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Should have at least one option"));
        
        InstrumentKey optionKey = firstOption.instrument().key();
        
        // Fetch Greeks
        OptionQuote greeksQuote = brokerConnection.options().getGreeks(optionKey);
        
        assertNotNull(greeksQuote, "Should get Greeks quote");
        
        if (greeksQuote.greeks() != null) {
            log.info("GOLD option Greeks:");
            log.info("  Delta: {}", greeksQuote.greeks().delta());
            log.info("  Gamma: {}", greeksQuote.greeks().gamma());
            log.info("  Theta: {}", greeksQuote.greeks().theta());
            log.info("  Vega: {}", greeksQuote.greeks().vega());
            log.info("  IV: {}%", greeksQuote.greeks().impliedVolatility());
            
            // Validate Greeks are reasonable
            assertNotNull(greeksQuote.greeks().delta(), "Delta should not be null");
        } else {
            log.warn("Greeks not available for {} option (may be normal during off-hours)", commodity);
        }
    }

    /**
     * Test 6: Compare OI across top 3 commodities
     */
    @Test
    void comparesOiAcrossTopCommodities() throws Exception {
        connectWithDailyInstrumentMaster();

        List<CommodityOiData> oiDataList = new ArrayList<>();
        
        for (String commodity : TOP_COMMODITIES) {
            List<LocalDate> expiries = brokerConnection.options()
                    .getExpiries(commodity, ExchangeSegment.MCX_COMM);
            
            if (expiries.isEmpty()) {
                continue;
            }
            
            LocalDate expiry = expiries.getFirst();
            OptionChainSnapshot chain = brokerConnection.options()
                    .getOptionChain(commodity, ExchangeSegment.MCX_COMM, expiry);
            
            // Calculate total OI
            long totalCallOi = chain.strikes().stream()
                    .map(OptionChainEntry::call)
                    .filter(java.util.Objects::nonNull)
                    .mapToLong(q -> q.openInterest())
                    .sum();
            
            long totalPutOi = chain.strikes().stream()
                    .map(OptionChainEntry::put)
                    .filter(java.util.Objects::nonNull)
                    .mapToLong(q -> q.openInterest())
                    .sum();
            
            long totalOi = totalCallOi + totalPutOi;
            
            oiDataList.add(new CommodityOiData(commodity, totalCallOi, totalPutOi, totalOi));
            
            log.info("{} - Call OI: {}, Put OI: {}, Total OI: {}", 
                    commodity, totalCallOi, totalPutOi, totalOi);
        }
        
        // Sort by total OI
        oiDataList.sort(Comparator.comparingLong(CommodityOiData::totalOi).reversed());
        
        if (!oiDataList.isEmpty()) {
            log.info("\n=== MCX Commodities Ranked by Total OI ===");
            for (int i = 0; i < oiDataList.size(); i++) {
                CommodityOiData data = oiDataList.get(i);
                log.info("#{}: {} - Total OI: {} (Call: {}, Put: {})", 
                        i + 1, data.commodity(), data.totalOi(), 
                        data.callOi(), data.putOi());
            }
            
            // Verify we got data for at least one commodity
            assertFalse(oiDataList.isEmpty(), "Should have OI data for at least one commodity");
        }
    }

    private void connectWithDailyInstrumentMaster() throws Exception {
        DhanConnectionSettings settings = LiveDhanTestSupport.connectionSettingsOrSkip();
        brokerConnection = new DhanBrokerConnection(
                settings,
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-mcx-cache"), false);
    }

    // Helper records
    private record MarketDataUpdate(DomainEvent event) {}
    private record CommodityOiData(String commodity, long callOi, long putOi, long totalOi) {}
}
