#!/bin/bash
# Quick MCX Market Live Data Check
# Tests if MCX option chains are working during live market hours

echo "============================================"
echo "  MCX Live Market Data Check"
echo "============================================"
echo ""

# Check current time
CURRENT_IST=$(TZ='Asia/Kolkata' date +"%H:%M")
echo "Current IST: $CURRENT_IST"
echo "MCX Hours: 09:00 - 23:30 IST"
echo ""

# Create a simple test file
cat > /tmp/McxQuickTest.java << 'EOF'
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

public class McxQuickTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== MCX Quick Live Data Test ===\n");
        
        // Load credentials
        String clientId = System.getenv("DHAN_CLIENT_ID");
        String accessToken = System.getenv("DHAN_ACCESS_TOKEN");
        
        if (clientId == null || accessToken == null) {
            System.out.println("❌ DHAN_CLIENT_ID or DHAN_ACCESS_TOKEN not set");
            System.out.println("Please set environment variables first");
            return;
        }
        
        System.out.println("✓ Credentials loaded");
        
        // Create connection
        DhanConnectionSettings settings = DhanConnectionSettings.builder()
                .clientId(clientId)
                .accessToken(accessToken)
                .build();
        
        DhanBrokerConnection broker = new DhanBrokerConnection(
                settings,
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );
        
        System.out.println("✓ Connection created");
        
        // Load instrument catalog
        System.out.println("\nLoading instrument catalog...");
        broker.loadDailyInstrumentCatalog(Files.createTempDirectory("mcx-test"), false);
        System.out.println("✓ Catalog loaded\n");
        
        // Test commodities
        String[] commodities = {"GOLD", "SILVER", "CRUDEOIL"};
        
        for (String commodity : commodities) {
            System.out.println("--- " + commodity + " ---");
            
            try {
                // Get expiries
                List<LocalDate> expiries = broker.options()
                        .getExpiries(commodity, ExchangeSegment.MCX_COMM);
                
                if (expiries.isEmpty()) {
                    System.out.println("  ⚠ No expiries available\n");
                    continue;
                }
                
                LocalDate expiry = expiries.getFirst();
                System.out.println("  Nearest expiry: " + expiry);
                
                // Get option chain
                OptionChainSnapshot chain = broker.options()
                        .getOptionChain(commodity, ExchangeSegment.MCX_COMM, expiry);
                
                System.out.println("  Spot price: ₹" + (chain.spotPricePaisa() / 100.0));
                System.out.println("  Strikes: " + chain.strikes().size());
                
                long contractCount = chain.strikes().stream()
                        .mapToLong(e -> (e.call() != null ? 1 : 0) + (e.put() != null ? 1 : 0))
                        .sum();
                
                System.out.println("  Total contracts: " + contractCount);
                
                // Check for live data
                long withLtp = chain.strikes().stream()
                        .flatMap(e -> java.util.stream.Stream.of(e.call(), e.put()))
                        .filter(java.util.Objects::nonNull)
                        .filter(q -> q.ltp() > 0)
                        .count();
                
                System.out.println("  Contracts with LTP: " + withLtp);
                
                if (chain.spotPricePaisa() > 0 && contractCount > 0) {
                    System.out.println("  ✓ " + commodity + " working!\n");
                } else {
                    System.out.println("  ⚠ " + commodity + " may have issues\n");
                }
                
            } catch (Exception e) {
                System.out.println("  ❌ Error: " + e.getMessage() + "\n");
            }
        }
        
        broker.disconnect();
        System.out.println("=== Test Complete ===");
    }
}
EOF

echo "Test file created. To run:"
echo ""
echo "1. Set your Dhan credentials:"
echo "   export DHAN_CLIENT_ID=your_client_id"
echo "   export DHAN_ACCESS_TOKEN=your_access_token"
echo ""
echo "2. Compile and run:"
echo "   cd /Users/apple/Downloads/Trade_J"
echo "   ./gradlew :broker-dhan:build -x test"
echo "   javac -cp broker-dhan/build/libs/*:broker-api/build/libs/*:core/build/libs/*:broker-core/build/libs/*:execution/build/libs/* /tmp/McxQuickTest.java"
echo "   java -cp /tmp:broker-dhan/build/libs/*:broker-api/build/libs/*:core/build/libs/*:broker-core/build/libs/*:execution/build/libs/* McxQuickTest"
echo ""
echo "OR simply run the integration test:"
echo "   ./gradlew :app:test --tests '*McxCommodity*' --include-tag integration --include-tag broker-rest"
echo ""
