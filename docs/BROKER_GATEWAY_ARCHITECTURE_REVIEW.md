# Broker Gateway Architecture Review

**Reviewer**: Principal Broker Architect / API Design Expert / Plugin Architecture Expert
**Date**: 2026-06-06
**Scope**: BrokerGateway, BrokerHandle, MarketGateway, BrokerExplorer, BrokerInspector, BrokerExtras, SPI, CLI exposure

---

## ANSWER TO THE MOST IMPORTANT QUESTION

> Can every current and future broker capability be discovered, tested, invoked, exposed to CLI, exposed to UI, exposed to MCP, and exposed to Market Gateway WITHOUT modifying gateway core?

**ANSWER: MOSTLY YES — with 5 specific gaps.**

| Path | Status | Evidence |
|------|:---:|----------|
| **Discovered** | **YES** | `BrokerExplorer.inspect()` checks all 15 ports + 6 markers; `BrokerDescriptor` declares per-broker capabilities |
| **Tested** | **YES** | 97 broker-gateway tests, 69 broker module tests, BrokerInspector 20 live probes |
| **Invoked** | **PARTIAL** | `BrokerHandle` covers 14/15 ports + advanced orders; **missing: `invoke()` on BrokerHandle**, no dynamic method dispatch |
| **Exposed to CLI** | **PARTIAL** | `tradej broker inspect` works; **missing: `tradej broker capabilities <name>`** dedicated command |
| **Exposed to UI** | **YES** | `BrokerInspectionReport` serializable via JSON; gateway WebSocket broadcasts health |
| **Exposed to MCP** | **YES** | MCP server can call `BrokerExplorer.inspect()` + `BrokerHandle.*` methods |
| **Exposed to MarketGateway** | **YES** | `MarketGateway.create(gateway)` routes through `BrokerRouter.active()` |
| **Future capabilities** | **PARTIAL** | SPI + `BrokerExtras.invoke()` allow extension, but **new port interfaces require BrokerHandle modification** |

---

## 1. CAPABILITY DISCOVERY REVIEW

### Current Implementation

**Static discovery** via `BrokerExplorer.inspect(handle)`:
- Checks **15 port interfaces**: MarketDataProvider, OptionsProvider, OrderCommand, OrderQuery, PortfolioProvider, MarginProvider, InstrumentResolver, WebSocketMultiplexer, FuturesProvider, BracketOrderProvider, GttOrderProvider, SliceOrderCommand, SessionRiskProvider, ConditionalAlertProvider, NewsProvider
- Checks **6 capability markers**: OptionsCapable, FuturesCapable, MarginCapable, AlertCapable, AdvancedOrderCapable, NewsCapable
- Returns `BrokerInspectionReport` with `Map<String, Boolean>` capability map

**Declared discovery** via `BrokerProvider.descriptor()`:
- Each provider returns `BrokerDescriptor` with `capabilities`, `metadata`, `supportedSegments`, `rateLimitInfo`

### Gaps Found

| Gap | Severity | Impact |
|-----|:---:|--------|
| **No capability descriptions** | Medium | Consumers don't know what "BracketOrderProvider" does |
| **No capability versioning** | Low | Cannot detect API evolution |
| **No capability categories** | Low | No grouping (market/orders/portfolio/advanced) |
| **No capability metadata per-entry** | Medium | Cannot express "requires market hours" or "requires subscription" |
| **BrokerDescriptor capabilities are hardcoded** | Medium | Duplicates BrokerExplorer — two sources of truth |

### Missing from Discovery

| Capability | In Explorer? | In Descriptor? | In BrokerHandle? |
|-----------|:---:|:---:|:---:|
| Quotes (LTP/Quote/OHLC) | YES | YES | YES |
| Depth (5-level) | YES | YES | YES |
| Depth (20-level) | NO | NO | NO |
| Historical Candles | YES | YES | YES |
| Batch LTP/Quote | NO | NO | NO |
| Option Chain | YES | YES | YES |
| Option Greeks | YES | YES | YES |
| Strike Selection | YES | YES | YES |
| Rolling Options | NO | NO | NO |
| Futures | YES | YES | YES |
| Orders (place/modify/cancel) | YES | YES | YES |
| Kill Switch | NO | NO | YES |
| Square Off Intraday | NO | NO | NO |
| Bracket Orders | YES | YES | YES |
| Cover Orders | NO | NO | NO |
| GTT Orders | YES | YES | YES |
| Slice Orders | YES | YES | YES |
| Order Preview | NO | NO | YES |
| Order Book | YES | YES | YES |
| Trade Book | YES | YES | YES |
| Portfolio (balance/positions/holdings) | YES | YES | YES |
| Margin Estimate | YES | YES | YES |
| News | YES | YES | YES |
| Session Risk | YES | YES | NO (via extras only) |
| Conditional Alerts | YES | YES | NO |
| WebSocket | YES | YES | YES |
| Order Stream | NO | NO | NO |
| Market Status | NO | NO | NO |

---

## 2. BROKER-SPECIFIC ACCESS REVIEW

### Current Implementation

`BrokerHandle.extras()` returns `BrokerExtras`:
- **Dhan**: `DhanExtras` — exposes `sessionRisk()` via `invoke("sessionRisk")`
- **Upstox**: `UpstoxExtras` — exposes `news()` via `news()` and `invoke("news")`
- **ICICI/Simulation**: Default `BrokerExtras` with no extras

### Gaps Found

| Gap | Severity | Impact |
|-----|:---:|--------|
| **No ICICI extras** | Medium | ICICI has no broker-specific access despite having unique Breeze API features |
| **Dhan extras too thin** | High | Dhan has 20-depth, rolling options, kill switch, square-off — none accessible via extras |
| **Upstox extras too thin** | Medium | Upstox has data services, analytics charges, holidays — none accessible via extras |
| **No type-safe extras** | High | `invoke(String, Object...)` returns `Optional<Object>` — no compile-time safety |
| **No extras discovery** | Medium | Cannot list what extras a broker supports without calling invoke() and catching exceptions |
| **Extras created per-call** | Low | `extras()` creates a new instance every time — should be cached |

### Broker-Specific Features Not Exposed

| Feature | Broker | Accessible via Handle? | Accessible via Extras? |
|---------|--------|:---:|:---:|
| 20-level depth | Dhan | NO | NO |
| Rolling options | Dhan | NO | NO |
| Kill switch | Dhan | YES (via handle) | NO |
| Square off intraday | Dhan | NO | NO |
| Cancel & square off | Dhan | NO | NO |
| Data services | Upstox | NO | NO |
| Analytics charges | Upstox | NO | NO |
| Holidays | Upstox | NO | NO |
| Profile | Upstox | NO | NO |
| Breeze portfolio analytics | ICICI | NO | NO |

---

## 3. DYNAMIC INVOCATION REVIEW

### Current Implementation

`BrokerExtras.invoke(String methodName, Object... args)` is the only dynamic dispatch mechanism:
- Dhan: supports `"sessionRisk"` only
- Upstox: supports `"news"` only
- No parameter validation, no method discovery, no type safety

### Assessment: **INADEQUATE**

| Criterion | Status | Detail |
|-----------|:---:|--------|
| Dynamic invocation works | PARTIAL | Only 2 methods exposed across all brokers |
| Method discovery | **NO** | Cannot list available methods |
| Parameter validation | **NO** | No type checking on args |
| Runtime type safety | **NO** | Returns `Optional<Object>` |
| Error handling | PARTIAL | Throws `UnsupportedOperationException` |
| Capability lookup efficiency | **NO** | Must try-catch to discover |

### What's Missing

The gateway has **no equivalent of**:
```java
// These don't exist:
gateway.broker("dhan").invoke("getOptionGreeks", params);
gateway.broker("dhan").invoke("getMarketDepth20", params);
gateway.broker("dhan").methods();  // list available methods
gateway.broker("dhan").methodSignature("bracketOrder");  // describe params
```

`BrokerHandle` is a **static facade** — all methods are hardcoded. There is no reflection-based or registry-based dynamic dispatch.

---

## 4. EXTENSION MODEL REVIEW

### Adding a New Broker

**What works (no gateway modification needed)**:
1. Implement `BrokerProvider` interface
2. Register in `META-INF/services/com.tradej.brokergateway.spi.BrokerProvider`
3. `ServiceLoaderBrokerRegistry` auto-discovers it
4. `BrokerExplorer.inspect()` auto-detects port capabilities
5. `BrokerHandle.extras()` falls back to default (empty) extras

**What DOES NOT work (requires gateway modification)**:

| Scenario | Requires Change To | Severity |
|----------|-------------------|:---:|
| New port interface (e.g. `MarketBreadthProvider`) | `BrokerHandle` + `BrokerExplorer` | **HIGH** |
| New capability marker | `BrokerExplorer` | MEDIUM |
| New extras method | `BrokerExtras` interface | MEDIUM |
| Broker-specific extras (e.g. `ZerodhaExtras`) | `BrokerHandle.extras()` switch statement | **HIGH** |
| New BrokerSource enum value | `BrokerSource` enum | MEDIUM |

### Verdict: Extension model is **open for new brokers** but **closed for new capabilities**.

The SPI allows plugging in new broker implementations without touching gateway core. However, adding a new *type* of capability (new port interface) requires modifying `BrokerHandle`, `BrokerExplorer`, and potentially `BrokerExtras`.

---

## 5. CLI EXPOSURE REVIEW

### Current CLI Commands

```
tradej broker dhan quote RELIANCE IDX_I     ✓ works
tradej broker dhan depth RELIANCE NSE_EQ     ✓ works
tradej broker dhan ltp RELIANCE NSE_EQ       ✓ works
tradej broker dhan chain NIFTY IDX_I         ✓ works
tradej broker dhan historical RELIANCE       ✓ works
tradej broker dhan balance                    ✓ works
tradej broker dhan positions                  ✓ works
tradej broker dhan orders                     ✓ works
tradej broker validate dhan RELIANCE NSE_EQ   ✓ works (25 checks)
tradej broker inspect dhan                    ✓ works (static caps)
```

### Missing CLI Commands

| Command | Status | Impact |
|---------|--------|--------|
| `tradej broker capabilities dhan` | **MISSING** | Cannot list capabilities from CLI |
| `tradej broker extras dhan` | **MISSING** | Cannot discover broker-specific features |
| `tradej broker invoke dhan sessionRisk` | **MISSING** | Cannot call dynamic methods |
| `tradej broker capabilities --json dhan` | **MISSING** | Cannot get machine-readable capabilities |
| `tradej broker compare` | **MISSING** | Cannot compare brokers side-by-side |

### Current `tradej broker inspect` Output

```
=== Broker Inspector: DHAN ===

  Port Interfaces:
    ✓ MarketDataProvider
    ✓ OptionsProvider
    ✓ OrderCommand
    ...
    ✗ NewsProvider

  Capability Markers:
    ✓ OptionsCapable
    ✓ FuturesCapable
    ...

  Metadata:
    broker:              DHAN
    instruments:         5842
    catalogLoaded:       true
```

This is good but lacks: descriptions, categories, market-hours requirements, authentication requirements.

---

## 6. COVERAGE MATRIX

| Capability | Broker | Unit | Contract | Integration | Smoke | Live | Coverage % | Missing |
|-----------|--------|:---:|:---:|:---:|:---:|:---:|:---:|---------|
| Quotes | Dhan | ✓ | ✓ | ✓ | ✓ | ✓ | 100% | — |
| Quotes | Upstox | ✓ | — | ✓ | — | ✓ | 70% | Contract test |
| Quotes | ICICI | ✓ | ✓ | ✓ | — | ✓ | 85% | Smoke test |
| Depth | Dhan | ✓ | — | ✓ | — | ✓ | 75% | Contract test |
| Depth | Upstox | ✓ | — | — | — | — | 40% | Integration, live |
| Depth | ICICI | ✓ | — | — | — | — | 40% | Integration, live |
| Historical | Dhan | ✓ | — | ✓ | — | ✓ | 80% | Contract test |
| Historical | Upstox | ✓ | — | ✓ | — | ✓ | 80% | Contract test |
| Historical | ICICI | ✓ | — | ✓ | — | ✓ | 80% | Contract test |
| Option Chain | Dhan | ✓ | — | ✓ | — | ✓ | 80% | Contract test |
| Option Chain | Upstox | ✓ | — | ✓ | — | ✓ | 80% | Contract test |
| Option Chain | ICICI | ✓ | — | ✓ | — | ✓ | 80% | Contract test |
| Orders | Dhan | ✓ | ✓ | ✓ | ✓ | ✓ | 100% | — |
| Orders | Upstox | ✓ | — | ✓ | — | ✓ | 70% | Contract, smoke |
| Orders | ICICI | ✓ | ✓ | ✓ | — | ✓ | 85% | Smoke test |
| Portfolio | Dhan | ✓ | — | ✓ | — | ✓ | 75% | Contract test |
| Portfolio | Upstox | ✓ | — | ✓ | — | ✓ | 75% | Contract test |
| Portfolio | ICICI | ✓ | — | ✓ | — | ✓ | 75% | Contract test |
| Margin | Dhan | ✓ | — | ✓ | — | ✓ | 75% | Contract test |
| Margin | Upstox | ✓ | — | ✓ | — | ✓ | 75% | Contract test |
| Margin | ICICI | ✓ | — | — | — | — | 50% | Integration, live |
| Bracket | Dhan | ✓ | — | ✓ | — | ✓ | 75% | Contract test |
| Bracket | Upstox | — | — | — | — | — | 0% | **Not implemented** |
| Bracket | ICICI | — | — | — | — | — | 0% | **Not implemented** |
| GTT | Dhan | ✓ | — | ✓ | — | ✓ | 75% | Contract test |
| GTT | Upstox | ✓ | — | ✓ | — | ✓ | 75% | Contract test |
| GTT | ICICI | — | — | — | — | — | 0% | **Not implemented** |
| News | Upstox | — | — | ✓ | — | ✓ | 50% | Unit, contract |
| News | Dhan | — | — | — | — | — | 0% | **Not supported** |
| News | ICICI | — | — | — | — | — | 0% | **Not supported** |
| WebSocket | Dhan | ✓ | — | ✓ | — | ✓ | 75% | Contract, reconnect |
| WebSocket | Upstox | ✓ | — | ✓ | — | ✓ | 75% | Contract, reconnect |
| WebSocket | ICICI | ✓ | — | ✓ | — | ✓ | 75% | Contract, reconnect |

---

## 7. REAL ENDPOINT VALIDATION MATRIX

| Capability | Implemented | Tested | Mapped | Exposed (Handle) | Discoverable | Callable | Hidden? |
|-----------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| LTP | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Quote | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| OHLC | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Depth (5) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Depth (20) | ✓ | ✓ | ✓ | **NO** | **NO** | **NO** | **YES** |
| Batch LTP | ✓ | ✓ | ✓ | **NO** | **NO** | **NO** | **YES** |
| Batch Quote | ✓ | ✓ | ✓ | **NO** | **NO** | **NO** | **YES** |
| Historical | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Option Chain | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Greeks | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Strike Select | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Rolling Options | ✓ | ✓ | ✓ | **NO** | **NO** | **NO** | **YES** |
| Futures | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Place Order | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Modify Order | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Cancel Order | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Kill Switch | ✓ | ✓ | ✓ | ✓ | **NO** | ✓ | **PARTIAL** |
| Square Off | ✓ | ✓ | ✓ | **NO** | **NO** | **NO** | **YES** |
| Bracket | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| GTT | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Slice | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Order Preview | ✓ | ✓ | ✓ | ✓ | **NO** | ✓ | **PARTIAL** |
| Balance | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Positions | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Holdings | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Margin | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| News | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Session Risk | ✓ | ✓ | ✓ | **NO** | **PARTIAL** | **PARTIAL** | **PARTIAL** |
| Alerts | ✓ | ✓ | ✓ | **NO** | ✓ | **NO** | **YES** |
| Order Book | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Trade Book | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| WebSocket | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | NO |
| Order Stream | ✓ | ✓ | ✓ | **NO** | **NO** | **NO** | **YES** |
| Market Status | **NO** | **NO** | **NO** | **NO** | **NO** | **NO** | **N/A** |

### Hidden Capabilities (implemented but not exposed through gateway)

| Capability | Available Via | Fix Required |
|-----------|--------------|-------------|
| 20-level depth | `connection.marketData()` | Add `depth20()` to BrokerHandle |
| Batch LTP/Quote | `connection.marketData()` | Add `batchLtp()`, `batchQuote()` |
| Rolling options | `connection.options()` | Add `rollingOptions()` or extras |
| Square off | `connection.orders()` | Add `squareOffIntraday()` |
| Cancel & square off | `connection.orders()` | Add `cancelAndSquareOff()` |
| Session risk | `connection.sessionRisk()` | Add to handle or expand extras |
| Alerts | `connection.alerts()` | Add `createAlert()`, `getAlerts()` |
| Order stream | `connection.websocket()` | Expose order update subscription |

---

## 8. BROKER FEATURE PARITY REVIEW

### What Dhan Exposes That Others Don't

| Feature | Accessible via Gateway? | Fix |
|---------|:---:|-----|
| 20-level depth | NO | Add to BrokerHandle or extras |
| Rolling options | NO | Add to BrokerHandle or extras |
| Kill switch | YES | Already in BrokerHandle |
| Square off intraday | NO | Add to BrokerHandle or extras |
| Cancel all + square off | NO | Add to BrokerHandle or extras |
| Session risk (PnL exit) | NO (extras only) | Expand extras or add to handle |
| Bracket orders | YES | Already in BrokerHandle |
| Conditional alerts | NO | Add to BrokerHandle |

### What Upstox Exposes That Others Don't

| Feature | Accessible via Gateway? | Fix |
|---------|:---:|-----|
| News provider | YES | Already in BrokerHandle |
| Data services | NO | Add to UpstoxExtras |
| Analytics charges | NO | Add to UpstoxExtras |
| Holidays | NO | Add to UpstoxExtras |
| Profile provider | NO | Add to UpstoxExtras |

### What ICICI Exposes That Others Don't

| Feature | Accessible via Gateway? | Fix |
|---------|:---:|-----|
| (Nothing unique not in other brokers) | — | — |

### Verdict: **No capability is lost due to lowest-common-denominator design.**

The `BrokerHandle` does NOT restrict access to common features only. Capability-gated methods (`bracketOrder`, `gttOrder`, `sliceOrder`, `news`) throw `UnsupportedOperationException` for unsupported brokers, which is correct behavior. The escape hatch via `connection()` provides raw access.

However, **8 capabilities are hidden** (implemented but not exposed through the gateway API).

---

## 9. PLUGIN ARCHITECTURE REVIEW

### Current Plugin Model

| Component | Mechanism | Status |
|-----------|-----------|:---:|
| **SPI** | `BrokerProvider` via `ServiceLoader` | **PASS** |
| **ServiceLoader** | `META-INF/services/` registration | **PASS** |
| **Capability Registry** | `BrokerDescriptor.capabilities` + `BrokerExplorer` | **PARTIAL** |
| **Plugin Lifecycle** | `isEnabled()` on BrokerProvider | **BASIC** |

### What a New Plugin Can Add Without Modifying Gateway Core

| Extension | Possible? | Mechanism |
|-----------|:---:|----------|
| New broker (e.g. Zerodha) | YES | BrokerProvider SPI |
| New port implementation | YES | `IBrokerConnection.getCapability()` |
| New extras class | **PARTIAL** | Must modify `BrokerHandle.extras()` switch |
| New capability marker | **NO** | Must modify `BrokerExplorer.inspect()` |
| New port interface | **NO** | Must modify `BrokerHandle` + `BrokerExplorer` |
| New data provider | **NO** | Must modify `BrokerHandle` |
| New market service | **NO** | Must modify `BrokerHandle` |

### Plugin Lifecycle Gaps

| Feature | Status | Detail |
|---------|:---:|--------|
| Plugin enable/disable at runtime | NO | `isEnabled()` checked only at startup |
| Plugin health check | PARTIAL | `BrokerInspector` probes, but no plugin-level health |
| Plugin version detection | NO | No version field in `BrokerProvider` or `BrokerDescriptor` |
| Plugin dependency declaration | NO | No way to declare dependencies on other plugins |
| Plugin configuration | PARTIAL | `BrokerProfile` exists but no plugin-level config schema |
| Plugin hot-reload | NO | ServiceLoader is one-shot at startup |

---

## 10. FAILURE MODE REVIEW

| Failure Scenario | Current Behavior | Adequate? | Fix |
|-----------------|-----------------|:---:|-----|
| **Capability missing** | `UnsupportedOperationException` from `requireCapability()` | YES | — |
| **Capability unsupported** | `Optional.empty()` from `getCapability()` | YES | — |
| **Broker unavailable** | Exception propagates from REST call | PARTIAL | Circuit breaker exists but error message could be clearer |
| **Auth expired** | `DhanAuthenticationException` / `UpstoxApiException.isAuthFailure()` | YES | Token managers auto-refresh |
| **Invalid parameters** | Broker API returns 400, mapped to `VALIDATION_ERROR` | YES | — |
| **Method not found (extras)** | `UnsupportedOperationException` from `invoke()` | PARTIAL | No method listing available |
| **Version mismatch** | Not detected | **NO** | No version negotiation |
| **Plugin not loaded** | `BrokerProvider` not in registry | PARTIAL | No diagnostic message |
| **Broker timeout** | `RetryExecutor` retries, then `CircuitBreaker` opens | YES | — |
| **Rate limited** | 429 detected, backoff applied | YES | — |
| **WebSocket disconnect** | `ReconnectManager` reconnects with backoff | YES | — |
| **Duplicate events** | Dhan dedup via `latestOrderStatuses` | PARTIAL | Upstox/ICICI dedup now added |
| **Partial fill** | `OrderPartiallyFilled` event emitted | YES | — |
| **Stale data (market closed)** | Returns last known values | PARTIAL | No `MarketStatus` capability |

---

## 11. REFACTORING RECOMMENDATIONS

### P0 — Critical Gaps

| # | Recommendation | Effort | Impact |
|---|---------------|:---:|--------|
| 1 | **Expose hidden capabilities in BrokerHandle**: Add `depth20()`, `batchLtp()`, `batchQuote()`, `rollingOptions()`, `squareOffIntraday()`, `cancelAndSquareOff()`, `createAlert()`, `getAlerts()` | 5d | 8 capabilities become accessible |
| 2 | **Add `tradej broker capabilities <name>` CLI command** | 1d | Capability discovery from CLI |
| 3 | **Expand DhanExtras**: Add `depth20()`, `rollingOptions()`, `squareOff()`, `killSwitch()` | 3d | Dhan-specific features accessible |
| 4 | **Expand UpstoxExtras**: Add `dataServices()`, `analyticsCharges()`, `holidays()`, `profile()` | 3d | Upstox-specific features accessible |
| 5 | **Add IciciExtras**: Create with Breeze-specific features | 2d | ICICI parity |

### P1 — Architecture Improvements

| # | Recommendation | Effort | Impact |
|---|---------------|:---:|--------|
| 6 | **Add capability metadata to BrokerDescriptor**: description, category, requiresMarketHours, requiresAuth, version | 3d | Better discovery |
| 7 | **Unify capability sources**: Remove hardcoded capabilities from BrokerExplorer; derive from BrokerProvider.descriptor() at runtime | 3d | Single source of truth |
| 8 | **Add method listing to BrokerExtras**: `Set<String> methods()` | 1d | Dynamic invocation discovery |
| 9 | **Cache extras instances**: `BrokerHandle.extras()` should cache, not create per-call | 0.5d | Performance |
| 10 | **Add `BrokerHandle.invoke(String method, Map<String, Object> params)`**: Dynamic dispatch for any handle method | 3d | Runtime invocation |

### P2 — Plugin Architecture

| # | Recommendation | Effort | Impact |
|---|---------------|:---:|--------|
| 11 | **Add capability registration to BrokerProvider**: `void registerCapabilities(CapabilityRegistry)` | 3d | Plugins can add new capability types |
| 12 | **Add version to BrokerProvider and BrokerDescriptor** | 1d | Version negotiation |
| 13 | **Add plugin health check interface**: `BrokerHealthCheck` | 2d | Plugin-level health |
| 14 | **Add plugin configuration schema**: `BrokerConfigSchema` | 2d | Validated plugin config |

### P3 — Future

| # | Recommendation | Effort | Impact |
|---|---------------|:---:|--------|
| 15 | **MarketStatus port interface** | 3d | Market hours awareness |
| 16 | **Cover Order port interface** | 3d | Feature parity |
| 17 | **Capability categories** (market/orders/portfolio/advanced/admin) | 2d | Better organization |
| 18 | **Plugin hot-reload** (replace ServiceLoader with custom registry) | 5d | Runtime plugin management |

---

## 12. FINAL SCORE

| Dimension | Score | Detail |
|-----------|:---:|--------|
| **Capability Coverage** | 75% | 15/20 port interfaces exposed; 5 hidden (20-depth, batch, rolling, square-off, alerts) |
| **Discovery** | 80% | BrokerExplorer + BrokerDescriptor work well; lacks metadata, categories, descriptions |
| **Broker-Specific Access** | 50% | BrokerExtras exists but too thin; only 2 invoke methods; ICICI has no extras |
| **Dynamic Invocation** | 20% | `invoke()` exists but only 2 methods; no listing, no validation, no type safety |
| **Extension Model** | 65% | New brokers plug in via SPI; new capabilities require gateway modification |
| **CLI Exposure** | 70% | Inspect/validate work; missing dedicated capabilities command |
| **Failure Handling** | 85% | Circuit breaker, retry, rate limiting, dedup all present; good error classification |
| **Test Coverage** | 75% | Strong unit tests; gaps in contract tests and live integration tests |
| **Plugin Architecture** | 55% | SPI works; lifecycle, versioning, config schema missing |

### Overall Score: **64/100**

### Verdict

The Broker Gateway is **functionally solid** for the implemented capabilities. The core abstractions (`BrokerGateway` → `BrokerHandle` → `IBrokerConnection`) are clean and well-tested. The SPI plugin model works for adding new brokers.

The primary weaknesses are:
1. **8 capabilities are hidden** behind the raw `connection()` escape hatch instead of being first-class gateway methods
2. **Dynamic invocation is nearly non-existent** — only 2 methods exposed via `invoke()`
3. **Broker-specific access is too thin** — DhanExtras has 1 method, UpstoxExtras has 1 method, ICICI has none
4. **New capability types require gateway modification** — the extension model is open for brokers but closed for capabilities

These are fixable with ~30 days of focused work (P0 + P1 items).
