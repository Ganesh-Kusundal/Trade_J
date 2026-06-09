# Broker Architecture: Certification Status

**Last Updated:** 2026-06-09  
**Status:** 🔴 CRITICAL - 0/8 certification criteria passing  
**Priority:** BLOCKS all broker-related feature work

---

## Executive Summary

Trade-J has **4 competing composition mechanisms** that can create divergent broker runtimes. This is the highest-priority architectural risk in the codebase.

**Before implementing any new broker features, adding new brokers, or enhancing gateway functionality, we must converge to a single composition root.**

---

## Certification Criteria Status

| # | Criterion | Status | Evidence | Target |
|---|-----------|--------|----------|--------|
| 1 | Only one composition root exists | 🔴 FAIL | 4 paths: Composition, Spring AutoConfig, Spring Manual Config, SPI | Single path via BrokerRegistry |
| 2 | Spring does not create broker runtimes directly | 🔴 FAIL | `BrokerConfiguration.java` (652 lines) creates broker beans | Spring only consumes IBrokerConnection |
| 3 | CLI and Spring produce identical broker graphs | 🔴 FAIL | Different constructors used | Same BrokerProvider.create() |
| 4 | All broker discovery goes through SPI | 🔴 FAIL | SPI in broker-gateway, not used by Spring/CLI | SPI in broker-api, used by all |
| 5 | New brokers can be added without modifying composition | 🔴 FAIL | Adding broker requires 6+ file changes | Add broker module + SPI registration only |
| 6 | Broker lifecycle is fully encapsulated | 🔴 FAIL | Lifecycle duplicated across 4 modules | Broker module owns lifecycle |
| 7 | Token/session management is fully encapsulated | 🟡 PARTIAL | Token managers in broker modules ✅, but Composition/Spring reference them ❌ | No external references to token managers |
| 8 | No broker-specific classes outside broker modules | 🔴 FAIL | Composition imports adapter classes, Spring imports settings | Only SPI contracts visible outside |

---

## Current Architecture (Problem)

```
┌─────────────────────────────────────────────────┐
│  4 COMPOSITION PATHS                            │
│                                                  │
│  Path A: BrokerComposition.create()             │
│  Path B: Spring @AutoConfiguration              │
│  Path C: Spring @Configuration (652 lines)      │
│  Path D: ServiceLoader<BrokerProvider>          │
│                                                  │
│  Each can produce DIFFERENT object graphs       │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│  VIOLATIONS                                     │
│                                                  │
│  ✗ OCP: Adding broker = 6+ file changes        │
│  ✗ DRY: Factory logic in 4 places              │
│  ✗ Encapsulation: Spring knows Dhan settings   │
│  ✗ Encapsulation: Composition knows adapters   │
│  ✗ Consistency: CLI != Spring != Tests         │
└─────────────────────────────────────────────────┘
```

## Target Architecture (Solution)

```
┌─────────────────────────────────────────────┐
│  Consumers (CLI, Spring, Replay, Backtest)  │
│  All use SAME Composition Root              │
└──────────────┬──────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────┐
│  Composition Root                           │
│  BrokerRegistry (ServiceLoader)             │
│  ↓                                          │
│  BrokerProvider.create(config)              │
│  ↓                                          │
│  IBrokerConnection                          │
└──────────────┬──────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────┐
│  broker-api (SPI Contracts)                 │
│  - BrokerProvider                           │
│  - BrokerDescriptor                         │
│  - Port interfaces                          │
└──────────────┬──────────────────────────────┘
               │
    ┌──────────┼──────────┐
    ▼          ▼          ▼
┌────────┐ ┌────────┐ ┌────────┐
│ broker │ │broker  │ │broker  │
│ -dhan  │ │-upstox │ │-icici  │
│        │ │        │ │        │
│ Each   │ │Each    │ │Each    │
│ owns:  │ │owns:   │ │owns:   │
│ -Token│ │-Token   │ │-Token  │
│ -Conn │ │-Conn    │ │-Conn   │
│ -WS   │ │-WS      │ │-WS     │
└────────┘ └────────┘ └────────┘
```

---

## Migration Progress

### Phase 0: Certification Tests ✅ COMPLETE
- [x] Architecture certification test suite created
- [x] Architectural Decision Record written
- [x] Current violations documented

### Phase 1-7: NOT STARTED
See [ADR-SINGLE-COMPOSITION-ROOT.md](./ADR-SINGLE-COMPOSITION-ROOT.md) for detailed migration plan.

---

## Impact Assessment

### What's Blocked Until This Is Fixed

🚫 **Adding new brokers** (Zerodha, Angel One, etc.) - Would multiply the problem  
🚫 **Broker gateway features** - Building on unstable foundation  
🚫 **Simulation/paper trading enhancements** - May diverge from live path  
🚫 **Production reliability work** - Can't trust runtime consistency  

### What Can Proceed

✅ Non-broker features (strategy, pipeline, data)  
✅ UI/frontend work  
✅ Documentation  
✅ Test infrastructure  

---

## Next Steps

1. **Team review** of this document and ADR
2. **Approve migration plan** or propose alternatives
3. **Assign ownership** for Phase 1 (Move SPI to broker-api)
4. **Set timeline** for each phase
5. **Run certification tests** after each phase

### Immediate Action Items

- [ ] Schedule architecture review meeting
- [ ] Assign team member to Phase 1
- [ ] Add certification tests to CI pipeline (must pass before merging broker changes)
- [ ] Create tracking issue for migration

---

## FAQ

**Q: Why can't we just fix it incrementally?**  
A: We can and will - that's why we have 7 phases. But we need the certification tests to enforce convergence.

**Q: What if we just live with 4 composition paths?**  
A: Every new broker multiplies the maintenance burden. With 3 brokers today, we have 12 paths. With 6 brokers, 24 paths. This doesn't scale.

**Q: Can we skip the certification tests and just refactor?**  
A: No. Without tests, we can't prove convergence. We need measurable criteria.

**Q: How long will the migration take?**  
A: Estimated 2-4 weeks across 7 phases. Each phase is independently testable and reversible.

**Q: What's the risk of not doing this?**  
A: Production incidents where CLI works but Spring fails, or tests pass but production fails. These are nightmare debugging scenarios.

---

## References

- [Architecture Decision Record](./ADR-SINGLE-COMPOSITION-ROOT.md)
- [Certification Test Suite](../architecture-test/src/test/java/com/tradej/architecture/BrokerCompositionArchitectureTest.java)
- [Broker Module Independence Principle](../docs/BROKER_MODULE_INDEPENDENCE.md)
- [Original Architecture Audit](../docs/BROKER_ARCHITECTURE_AUDIT.md)
