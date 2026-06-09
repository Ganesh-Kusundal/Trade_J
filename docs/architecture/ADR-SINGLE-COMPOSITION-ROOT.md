# ADR: Single Composition Root with SPI-Based Broker Discovery

**Status:** Proposed  
**Date:** 2026-06-09  
**Context:** Broker module architecture analysis and dual composition systems audit

---

## Context

Trade-J currently has **4 independent composition paths** that can create broker runtimes:

1. **Programmatic Composition** (`BrokerComposition.create()`) - Used by CLI, tests, `FullComposition`
2. **Spring AutoConfiguration** (broker module `@AutoConfiguration`) - Direct Spring boot
3. **Spring Manual Configuration** (`app/config/BrokerConfiguration.java`) - Production Spring boot
4. **SPI Provider** (`BrokerProvider` via ServiceLoader) - broker-gateway (unused in production)

This creates **divergent object graphs** where:
- CLI and Spring use different constructors
- Configuration sources differ (`BrokerProfile` vs `TradingProperties` vs `Environment`)
- Feature parity gaps exist (e.g., `UpstoxExpiredOptionService` only in Spring path)
- Adding a new broker requires changes to 6+ files

**Highest-priority risk:** Absence of a single composition root leads to inconsistent runtime behavior.

---

## Decision

Adopt **Option 1.5: SPI + Composition Root** architecture.

### Target Architecture

```
┌─────────────────────────────────────────────┐
│  Consumers (CLI, Spring, Replay, Backtest)  │
│                                              │
│  All use the same Composition Root           │
└──────────────┬──────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────┐
│  Composition Root                           │
│                                              │
│  BrokerRegistry (ServiceLoader)             │
│                                              │
│  Responsibilities:                          │
│  - Discover BrokerProvider implementations  │
│  - Route configuration to correct provider  │
│  - Return IBrokerConnection                 │
└──────────────┬──────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────┐
│  SPI Layer (broker-api)                     │
│                                              │
│  - BrokerProvider interface                 │
│  - BrokerDescriptor                         │
│  - BrokerCapabilities                       │
│  - Port interfaces                          │
└──────────────┬──────────────────────────────┘
               │
    ┌──────────┼──────────┐
    ▼          ▼          ▼
┌────────┐ ┌────────┐ ┌────────┐
│ Dhan   │ │Upstox  │ │ ICICI  │
│Provider│ │Provider│ │Provider│
└────────┘ └────────┘ └────────┘
```

### Key Principles

1. **Broker modules own everything** - Token management, session management, connection creation, WebSocket lifecycle, rate limiting, retry policies, circuit breakers
2. **SPI in broker-api** - `BrokerProvider` moves from `broker-gateway` to `broker-api`
3. **No broker-specific code outside broker modules** - Composition, Spring, and CLI only know about contracts
4. **Single composition path** - All consumers go through `BrokerRegistry` → `BrokerProvider`
5. **Spring is a consumer, not a creator** - Spring consumes `IBrokerConnection` beans, doesn't create them

---

## Consequences

### Positive

✅ **Single source of truth** for broker creation  
✅ **Open/Closed Principle** - New brokers via SPI, no composition changes  
✅ **Runtime consistency** - CLI, Spring, tests all use same path  
✅ **Reduced maintenance** - 1 file per broker, not 6+  
✅ **Plugin architecture** - Drop in `broker-zerodha.jar` without code changes  
✅ **Clear boundaries** - Broker modules fully encapsulated  

### Negative

🔴 **Refactoring required** - 652-line `BrokerConfiguration.java` must be simplified  
🔴 **Migration risk** - Need parallel run during transition  
🟡 **Breaking changes** - `BrokerProfile.DhanConfig` records will be replaced  
🟡 **Test updates** - All integration tests need migration  

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Divergent behavior during migration | Architecture certification tests enforce convergence |
| Production regression | Dual-run period with assertion that both paths produce identical graphs |
| Configuration complexity | Generic `BrokerConfiguration` DTO replaces broker-specific records |

---

## Migration Plan

### Phase 0: Certification Tests (CURRENT)
- [x] Create architecture certification test suite
- [ ] All tests currently FAIL (expected)
- [ ] Tests document current violations

### Phase 1: Move SPI to broker-api
- [ ] Move `BrokerProvider`, `BrokerDescriptor`, `BrokerCapabilities` to `broker-api`
- [ ] Update `broker-gateway` to import from `broker-api`
- [ ] Each broker module implements `BrokerProvider` in its own package
- [ ] **Certification test 4 passes**

### Phase 2: Extract ConnectionFactories
- [ ] Create `DhanConnectionFactory` inside `broker-dhan`
- [ ] Create `UpstoxConnectionFactory` inside `broker-upstox`
- [ ] Create `IciciConnectionFactory` inside `broker-icici`
- [ ] Each `BrokerProvider.create()` delegates to its `ConnectionFactory`
- [ ] **Certification test 6, 7 pass**

### Phase 3: Simplify BrokerComposition
- [ ] Replace switch statement with `BrokerRegistry.provider(id).create(config)`
- [ ] Remove broker-specific imports from composition
- [ ] **Certification test 1, 5 pass**

### Phase 4: Simplify Spring Configuration
- [ ] Delete `BrokerConfiguration.DhanAdapterConfig` inner class (150 lines)
- [ ] Delete `BrokerConfiguration.IciciAdapterConfig` inner class (130 lines)
- [ ] Delete `UpstoxBrokerConfiguration.java` (205 lines)
- [ ] Spring only creates wrapper beans (`ObservableMarketDataProvider`, etc.)
- [ ] Spring gets `IBrokerConnection` from `BrokerComposition`
- [ ] **Certification test 2 passes**

### Phase 5: Simplify CLI
- [ ] Replace `DhanBrokerSession`, `UpstoxBrokerSession`, `IciciBrokerSession`
- [ ] CLI uses `BrokerRegistry` → `BrokerProvider`
- [ ] **Certification test 3 passes**

### Phase 6: Clean Up
- [ ] Delete broker module `@AutoConfiguration` classes
- [ ] Remove `broker-gateway` direct dependencies on broker modules
- [ ] Update `settings.gradle` if needed
- [ ] **Certification test 8 passes**

### Phase 7: Verification
- [ ] All certification tests PASS
- [ ] Runtime parity test: CLI graph == Spring graph
- [ ] Add new broker test: Can add broker without touching composition
- [ ] Performance regression tests

---

## Architecture Certification Criteria

**All 8 criteria must pass before considering migration complete:**

1. ✅ Only one composition root exists
2. ✅ Spring does not create broker runtimes directly
3. ✅ CLI and Spring produce identical broker graphs
4. ✅ All broker discovery goes through SPI
5. ✅ New brokers can be added without modifying composition
6. ✅ Broker lifecycle is fully encapsulated
7. ✅ Token/session management is fully encapsulated
8. ✅ No broker-specific class appears outside its broker module (except SPI contracts)

**If these tests fail, fix those failures before touching:**
- Gateway features
- Simulation/paper trading
- Additional brokers
- New capabilities

---

## Alternatives Considered

### Option 2: Unify on Composition Layer
- Keep `BrokerComposition` as single source
- Delete Spring adapter configs
- Spring only creates wrapper beans

**Rejected because:** Still requires editing composition for new brokers (OCP violation)

### Option 3: Hybrid (Current Direction)
- Spring → BrokerComposition → BrokerFactory
- CLI → BrokerComposition → BrokerFactory
- broker-gateway → SPI (optional)

**Rejected because:** Doesn't solve the core problem - composition still knows about brokers

### Option 1: Full SPI
- All paths go through ServiceLoader
- No composition layer

**Modified to Option 1.5** because: Composition layer still useful for platform-wide wiring (pipeline, data, execution), just not broker-specific wiring

---

## References

- [Architecture Audit Findings](../docs/BROKER_ARCHITECTURE_AUDIT.md) - Detailed analysis of dual composition systems
- [Broker Module Independence Principle](../memory/development_practice_specification.md) - Broker modules must fully encapsulate their own connection creation
- [CompositionVsAutoConfigurationTest](../composition/src/test/java/com/tradej/composition/CompositionVsAutoConfigurationTest.java) - Existing parity test
- [BrokerCompositionArchitectureTest](../architecture-test/src/test/java/com/tradej/architecture/BrokerCompositionArchitectureTest.java) - New certification test suite
