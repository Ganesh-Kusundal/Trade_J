# PortfolioEngine DDD Migration Guide

## Overview

This guide explains how to migrate from the monolithic `PortfolioEngine` to the new DDD-structured components.

## Architecture Changes

### Before (Monolithic)
```
PortfolioEngine
├── ConcurrentHashMap<String, StrategyAllocation> allocations
├── ConcurrentHashMap<String, Long> netPositions
├── ConcurrentHashMap<String, String> signalToStrategy
├── ConcurrentHashMap<String, Long> signalToEstimatedCapital
├── ConcurrentHashMap<String, Long> signalDeltas
├── ConcurrentHashMap<String, String> orderIdToSignalId
└── ConcurrentHashMap<String, TradeInfo> openTrades
```

### After (DDD Structured)
```
PortfolioEngineRefactored (Thin Orchestrator)
├── CapitalReservationService (Domain Service)
│   └── SignalAttribution (Entity)
├── TradeAttributionService (Domain Service)
│   └── PositionBook (Entity)
└── Value Objects
    ├── CapitalPaisa
    └── StrategyId
```

## Migration Steps

### Step 1: Update Imports

**Before:**
```java
import com.tradej.strategy.portfolio.PortfolioEngine;
```

**After:**
```java
import com.tradej.strategy.portfolio.PortfolioEngineRefactored;
// OR continue using PortfolioEngine (backward compatible)
```

### Step 2: Constructor Injection

**Before:**
```java
@Bean
public PortfolioEngine portfolioEngine() {
    return new PortfolioEngine(1_000_000L, 5_000_000L);
}
```

**After (Option A - Backward Compatible):**
```java
@Bean
public PortfolioEngine portfolioEngine() {
    return new PortfolioEngineRefactored(1_000_000L, 5_000_000L);
}
```

**After (Option B - Direct DDD):**
```java
@Bean
public CapitalReservationService capitalReservationService() {
    SignalAttribution attribution = new SignalAttribution();
    return new CapitalReservationService(
            attribution,
            CapitalPaisa.of(1_000_000L),
            CapitalPaisa.of(5_000_000L)
    );
}

@Bean
public TradeAttributionService tradeAttributionService(
        CapitalReservationService capitalService) {
    return new TradeAttributionService(new SignalAttribution(), capitalService);
}
```

### Step 3: API Usage (Unchanged)

The public API remains identical:

```java
// These methods work identically with PortfolioEngineRefactored
portfolioEngine.onDomainEvent(event, downstream);
portfolioEngine.reserveSignal(signal);
portfolioEngine.releaseSignal(signalId);
portfolioEngine.usedCapitalPaisa("momentum");
portfolioEngine.netPosition("RELIANCE");
portfolioEngine.snapshot();
portfolioEngine.restore(snapshot);
```

### Step 4: Advanced Usage (New Capabilities)

#### Direct Access to DDD Components

```java
// Access position books directly
PositionBook relianceBook = tradeAttributionService.positionBook("RELIANCE");
long netPos = relianceBook.netPosition();

// Access signal attribution
SignalAttribution attribution = ...; // injected
StrategyId strategy = attribution.strategyFor("signal-123");
CapitalPaisa estimated = attribution.estimatedCapitalFor("signal-123");

// Access capital service directly
CapitalPaisa used = capitalService.usedCapital(StrategyId.of("momentum"));
```

#### Using Value Objects

```java
// Instead of raw longs
CapitalPaisa capital = CapitalPaisa.ofRupees(10000); // ₹10,000
CapitalPaisa required = CapitalPaisa.of(quantity * price);

// Type-safe strategy IDs
StrategyId strategy = StrategyId.of("momentum");
// Prevents typos: StrategyId.of("momentm") throws IllegalArgumentException
```

## Backward Compatibility

All existing code continues to work without modification:

- `PortfolioEngine` interface unchanged
- `DefaultCapitalReservationService` delegates to `PortfolioEngine`
- `DefaultExposureTracker` works with `PortfolioEngineRefactored`
- `DomainExposureTrackerAdapter` bridges DDD `PositionBook` to `ExposureTracker` interface
- `DomainCapitalReservationServiceAdapter` bridges DDD `CapitalReservationService` to portfolio interface

## Benefits of Migration

1. **Testability**: Each component can be unit tested in isolation
2. **Maintainability**: Business rules are encapsulated in domain services
3. **Type Safety**: Value objects prevent unit confusion (paisa vs rupees)
4. **Replay Isolation**: Snapshot/restore built into each entity
5. **Thread Safety**: Concurrent access patterns are explicit per component
6. **DDD Compliance**: Clear separation between entities, value objects, and domain services

## Rollback Plan

If issues arise, simply revert the Spring configuration:

```java
@Bean
public PortfolioEngine portfolioEngine() {
    return new PortfolioEngine(1_000_000L, 5_000_000L); // Original implementation
}
```

All DDD components remain available for gradual migration.

## Performance Considerations

- DDD components use the same concurrent data structures as the original
- Adapter layers add minimal overhead (method delegation)
- Benchmark tests verify no significant performance regression
- See `PortfolioDddPerformanceTest` for throughput metrics

## Testing

### Unit Tests
- `PositionBookTest` - 8 tests
- `SignalAttributionTest` - 9 tests
- `CapitalReservationServiceTest` - 10 tests
- `TradeAttributionServiceTest` - 7 tests

### Integration Tests
- `PortfolioDddIntegrationTest` - 7 tests
- `PortfolioDddPipelineIntegrationTest` - 6 tests

### Performance Tests
- `PortfolioDddPerformanceTest` - 6 benchmarks

## FAQ

**Q: Do I need to migrate all at once?**
A: No. Use `PortfolioEngineRefactored` as a drop-in replacement first, then gradually adopt DDD components.

**Q: What about existing snapshots?**
A: Snapshots from `PortfolioEngine` are not compatible with `PortfolioEngineRefactored`. Plan a state reset during migration.

**Q: Is thread safety maintained?**
A: Yes. All DDD components use thread-safe concurrent data structures.

**Q: What about the existing `PortfolioEngine`?**
A: It remains available for backward compatibility but is deprecated in favor of `PortfolioEngineRefactored`.
