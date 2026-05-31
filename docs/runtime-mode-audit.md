# Runtime Mode Audit — Trade-J Institutional Trading Platform

> **Document:** `docs/runtime-mode-audit.md`
> **Last updated:** May 2026
> **Phase:** Phase 0 — Foundation &amp; Build System

---

## 1. Runtime Mode Overview

The platform supports three runtime modes defined in `RuntimeMode.java` (`core/src/main/java/...`):

| Mode | Enum | Allows Broker Orders? | Uses Simulated Execution? | Uses Backtest Engine? |
|------|------|----------------------|--------------------------|----------------------|
| **Live** | `LIVE` | ✅ Yes | ❌ No | ❌ No |
| **Replay** | `REPLAY` | ❌ No | ✅ Yes | ❌ No |
| **Backtest** | `BACKTEST` | ❌ No | ✅ Yes | ✅ Yes |

Source: `core/src/main/java/com/tradej/core/domain/runtime/RuntimeMode.java`

```java
public enum RuntimeMode {
    LIVE,
    REPLAY,
    BACKTEST;

    public boolean allowsBrokerOrders() { return this == LIVE; }
    public boolean usesSimulatedExecution() { return this == REPLAY || this == BACKTEST; }
    public boolean usesBacktestEngine() { return this == BACKTEST; }
}
```

---

## 2. Mode Configuration Points

### 2.1 Spring Boot Property

Configured via `trade.runtime.mode` in `application.yml` or environment variable:

```yaml
trade:
  runtime:
    mode: LIVE          # default; alternatives: REPLAY, BACKTEST
```

**Property binding:** `TradingProperties.RuntimeProperties (trade-app)`

**Default:** `RuntimeMode.LIVE` — if the property is absent or blank, the system defaults to LIVE.

### 2.2 RuntimeModeHolder

A singleton volatile holder in `trade-core`:

```java
// core/src/main/java/com/tradej/core/domain/runtime/RuntimeModeHolder.java
private volatile RuntimeMode mode = RuntimeMode.LIVE;
```

Injected into services via Spring's dependency injection. Used to gate replay endpoints and broker-visible operations.

### 2.3 CLI Profile

The Trade-J CLI (`trade-cli`) uses a separate `Profile` enum (`LIVE` / `SANDBOX`) for broker API targeting:

```java
// trade-cli/src/main/java/com/tradej/cli/config/CliConfig.java
public enum Profile { LIVE, SANDBOX }
```

This is **independent** of `RuntimeMode` — the CLI profile selects broker API environment (production vs sandbox), while `RuntimeMode` controls whether broker side effects are permitted at runtime.

---

## 3. Mode-Controlled Behavior Matrix

| Component | Config Point | LIVE | REPLAY | BACKTEST |
|-----------|-------------|------|--------|----------|
| **Broker connection** | `IBrokerConnection` | Full broker connect | Disconnected / offline | Disconnected / offline |
| **Order placement** | `ExecutionHandler` | Routes to broker API | Routes to `SimulatedOrderService` | Routes to `SimulatedOrderService` |
| **Matching engine** | `MatchingEngine` | Not used | In-process matching | In-process matching |
| **PnL ledger** | `PnLLedger` | Not used | Tracks simulated PnL | Tracks simulated PnL |
| **Replay endpoints** | `AdminController` | ❌ Blocked (HTTP 409) | ✅ Allowed | ❌ Blocked* |
| **WebSocket market data** | `MarketDataPipeline` | Live feed | Replayed ticks | Replayed ticks |
| **Historical data service** | `HistoricalRangeService` | Live query | Replay from journal | Replay from journal |
| **Kill switch** | `AdminController.setKillSwitch` | Routes to broker | No-op | No-op |
| **Order reconciliation** | `OrderReconciler` | Full reconciliation | Simulated check | Simulated check |
| **Circuit breaker** | `TradingCircuitBreaker` | Active monitoring | Disabled | Disabled |
| **Daily risk reset** | `DailyRiskResetScheduler` | Scheduled reset | N/A | N/A |

> \* BACKTEST mode currently does not expose replay endpoints via `AdminController` — the guard is `RuntimeMode.LIVE` only, meaning REPLAY and BACKTEST both pass through. This may be intentional (backtest uses its own engine) but should be reviewed.

---

## 4. Replay Guard Analysis

The replay safety guard is implemented as a private method in `AdminController`:

```java
private ResponseEntity<Map<String, Object>> rejectIfLiveReplay() {
    if (runtimeModeHolder.mode() == RuntimeMode.LIVE) {
        return ResponseEntity.status(409).body(Map.of(
            "error", "Historical replay is blocked in LIVE mode",
            "hint", "Set trade.runtime.mode=REPLAY before invoking replay endpoints"
        ));
    }
    return null;
}
```

**Finding:** The guard only blocks `LIVE` mode. Both `REPLAY` and `BACKTEST` are permitted. This is correct for replay but the error message does not mention `BACKTEST` as a valid mode.

### Affected endpoints:
- `POST /admin/historical/replay/ticks`
- `POST /admin/historical/replay/candles`
- `POST /admin/historical/replay/fills`
- `POST /admin/historical/replay/orders`
- `POST /admin/chronicle/replay`

---

## 5. Dependency Injection Points

The following services receive `RuntimeModeHolder` via constructor injection:

| Service | File | Mode Usage |
|---------|------|-----------|
| `AdminController` | `app/.../admin/AdminController.java` | Guards replay endpoints |
| (Future) `ExecutionHandler` | `trade-execution/.../ExecutionHandler.java` | Should use `RuntimeMode` to route fills |

**Note:** The `ExecutionHandler` currently does NOT reference `RuntimeMode`. Based on the architecture, it should route to `SimulatedOrderService` when `usesSimulatedExecution()` returns true. This is a gap that should be addressed in Phase 1.

---

## 6. Security Implications

| Concern | LIVE | REPLAY | BACKTEST |
|---------|------|--------|----------|
| Broker API credentials in memory | ✅ Required | ❌ Not needed | ❌ Not needed |
| WebSocket feed costs | ✅ Incurred | ❌ None | ❌ None |
| Order placement risk | ✅ Real money | ❌ No-op/simulated | ❌ No-op/simulated |
| PnL accuracy | ✅ Real PnL | ⚠️ Simulated PnL | ⚠️ Simulated PnL |
| Rate limit consumption | ✅ Broker rate limits | ❌ None | ❌ None |

---

## 7. Recommendations

1. **ExecutionHandler routing:** Inject `RuntimeModeHolder` into `ExecutionHandler` and conditionally route orders to `SimulatedOrderService` in REPLAY and BACKTEST modes. Currently the routing is either missing or happens at a higher layer.

2. **Replay guard message:** Update the error hint in `AdminController.rejectIfLiveReplay()` to mention BACKTEST as an alternative:
   ```
   "hint", "Set trade.runtime.mode=REPLAY or trade.runtime.mode=BACKTEST before invoking replay endpoints"
   ```

3. **Documentation:** Add runtime mode configuration to CONFIG.md with clear instructions on how to start the app in each mode.

4. **Test coverage:** Add a `RuntimeModeIntegrationTest` that verifies:
   - LIVE mode blocks replay endpoints with 409
   - REPLAY mode allows replay endpoints
   - BACKTEST mode allows replay endpoints
   - Mode switching at runtime (if supported)

5. **CLI parity:** Ensure the CLI profile selection (`--profile live|sandbox`) does not accidentally override the runtime mode. These are separate concerns but could be confused by operators.

---

## 8. Audit Trail

| Date | Auditor | Change |
|------|---------|--------|
| 2026-05 | Phase 0 | Initial runtime mode audit |
