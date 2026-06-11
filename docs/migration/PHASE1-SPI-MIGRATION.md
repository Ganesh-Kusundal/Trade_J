# Phase 1 SPI Migration Guide

## Files to Move from broker-gateway to broker-api

### Source Files (broker-gateway → broker-api)

1. `BrokerSource.java` 
   - FROM: `broker-gateway/src/main/java/com/tradej/brokergateway/result/BrokerSource.java`
   - TO: `broker-api/src/main/java/com/tradej/broker/api/spi/BrokerSource.java`
   - Package: `com.tradej.brokergateway.result` → `com.tradej.broker.api.spi`
   - ✅ DONE

2. `CapabilityMetadata.java`
   - FROM: `broker-gateway/src/main/java/com/tradej/brokergateway/spi/CapabilityMetadata.java`
   - TO: `broker-api/src/main/java/com/tradej/broker/api/spi/CapabilityMetadata.java`
   - Package: `com.tradej.brokergateway.spi` → `com.tradej.broker.api.spi`
   - ✅ DONE

3. `BrokerDescriptor.java`
   - FROM: `broker-gateway/src/main/java/com/tradej/brokergateway/spi/BrokerDescriptor.java`
   - TO: `broker-api/src/main/java/com/tradej/broker/api/spi/BrokerDescriptor.java`
   - Package: `com.tradej.brokergateway.spi` → `com.tradej.broker.api.spi`
   - ✅ DONE

4. `BrokerProvider.java`
   - FROM: `broker-gateway/src/main/java/com/tradej/brokergateway/spi/BrokerProvider.java`
   - TO: `broker-api/src/main/java/com/tradej/broker/api/spi/BrokerProvider.java`
   - Package: `com.tradej.brokergateway.spi` → `com.tradej.broker.api.spi`
   - TODO

5. `BrokerRegistry.java`
   - FROM: `broker-gateway/src/main/java/com/tradej/brokergateway/spi/BrokerRegistry.java`
   - TO: `broker-api/src/main/java/com/tradej/broker/api/spi/BrokerRegistry.java`
   - Package: `com.tradej.brokergateway.spi` → `com.tradej.broker.api.spi`
   - TODO

6. `BrokerPluginRegistry.java`
   - FROM: `broker-gateway/src/main/java/com/tradej/brokergateway/spi/BrokerPluginRegistry.java`
   - TO: `broker-api/src/main/java/com/tradej/broker/api/spi/BrokerPluginRegistry.java`
   - Package: `com.tradej.brokergateway.spi` → `com.tradej.broker.api.spi`
   - TODO

7. `DefaultBrokerRegistry.java`
   - FROM: `broker-gateway/src/main/java/com/tradej/brokergateway/spi/DefaultBrokerRegistry.java`
   - TO: `broker-api/src/main/java/com/tradej/broker/api/spi/DefaultBrokerRegistry.java`
   - Package: `com.tradej.brokergateway.spi` → `com.tradej.broker.api.spi`
   - TODO

8. `ServiceLoaderBrokerRegistry.java`
   - FROM: `broker-gateway/src/main/java/com/tradej/brokergateway/spi/ServiceLoaderBrokerRegistry.java`
   - TO: `broker-api/src/main/java/com/tradej/broker/api/spi/ServiceLoaderBrokerRegistry.java`
   - Package: `com.tradej.brokergateway.spi` → `com.tradej.broker.api.spi`
   - TODO

9. `BrokerHealthCheck.java`
   - FROM: `broker-gateway/src/main/java/com/tradej/brokergateway/spi/BrokerHealthCheck.java`
   - TO: `broker-api/src/main/java/com/tradej/broker/api/spi/BrokerHealthCheck.java`
   - Package: `com.tradej.brokergateway.spi` → `com.tradej.broker.api.spi`
   - TODO

## Import Updates Required

### Files that import from `com.tradej.brokergateway.result.BrokerSource`:
- All broker provider implementations
- BrokerHandle, BrokerGateway, etc.
- Update: `import com.tradej.brokergateway.result.BrokerSource;`
- To: `import com.tradej.broker.api.spi.BrokerSource;`

### Files that import from `com.tradej.brokergateway.spi.*`:
- broker-gateway classes
- broker-dhan DhanBrokerProvider
- broker-upstox UpstoxBrokerProvider
- broker-icici IciciBrokerProvider
- composition classes
- Update: `import com.tradej.brokergateway.spi.X;`
- To: `import com.tradej.broker.api.spi.X;`

## ServiceLoader Configuration

### Old:
`broker-gateway/src/main/resources/META-INF/services/com.tradej.brokergateway.spi.BrokerProvider`

### New:
`broker-api/src/main/resources/META-INF/services/com.tradej.broker.api.spi.BrokerProvider`

Content should list provider implementations in their new locations:
```
com.tradej.broker.dhan.DhanBrokerProvider
com.tradej.broker.upstox.UpstoxBrokerProvider
com.tradej.broker.icici.IciciBrokerProvider
com.tradej.brokergateway.simulation.SimulationBrokerProvider
```

## Build.gradle Updates

### broker-api/build.gradle
No changes needed (already has java-test-fixtures plugin)

### broker-gateway/build.gradle
- Add: `api project(':broker-api')` (if not already present)
- Remove SPI files from source (they're moved)

### broker-dhan/build.gradle
- No dependency changes needed (already depends on broker-api)

### broker-upstox/build.gradle  
- No dependency changes needed (already depends on broker-api)

### broker-icici/build.gradle
- No dependency changes needed (already depends on broker-api)

## Deletions (after move)

Delete from broker-gateway:
- `src/main/java/com/tradej/brokergateway/result/BrokerSource.java`
- `src/main/java/com/tradej/brokergateway/spi/CapabilityMetadata.java`
- `src/main/java/com/tradej/brokergateway/spi/BrokerDescriptor.java`
- `src/main/java/com/tradej/brokergateway/spi/BrokerProvider.java`
- `src/main/java/com/tradej/brokergateway/spi/BrokerRegistry.java`
- `src/main/java/com/tradej/brokergateway/spi/BrokerPluginRegistry.java`
- `src/main/java/com/tradej/brokergateway/spi/DefaultBrokerRegistry.java`
- `src/main/java/com/tradej/brokergateway/spi/ServiceLoaderBrokerRegistry.java`
- `src/main/java/com/tradej/brokergateway/spi/BrokerHealthCheck.java`
- `src/main/resources/META-INF/services/com.tradej.brokergateway.spi.BrokerProvider`
