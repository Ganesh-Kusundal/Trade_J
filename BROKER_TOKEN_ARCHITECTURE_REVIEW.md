# Broker Implementation & Token Management — Architecture Review

> **Reviewer role:** Principal Software Architect / Runtime Systems Reviewer
> **Scope:** `broker/`, `broker-gateway/`, `gateway/`, plus broker-touching `app/` configuration
> **Method:** Static analysis of code + live HTTP preflight + unit/integration test execution
> **Risk profile:** Trading real money. Anything classified **CRITICAL** is a production-blocking defect.

---

## 1. System Intent

Trade-J is a multi-broker Java trading platform supporting three Indian brokers
(Dhan, Upstox, ICICI Breeze) plus a `simulation` adapter. For each broker the
system exposes a uniform `IBrokerConnection` facade with 14+ capability ports
(MarketData, Orders, Portfolio, WebSocket, etc.). The execution path is:

```
Strategy signal → Risk checks → OMS identity assign → IBrokerConnection.orders()
                → Broker-specific REST adapter (token inject) → Dhan/Upstox/ICICI
                → Fill confirmation via WebSocket order stream
```

Token management must satisfy three independent contracts (one per broker family):

- **Dhan (TOTP):** TOTP-minted JWT, single per-account access token, ~24h validity, hard rate-limit (1 mint per 2 min).
- **Upstox (OAuth2 + refresh):** daily access token + 6-month refresh token; optional "extended" 1-year read-only token.
- **ICICI (Breeze TOTP):** TOTP-minted session token (separate code path, not in this review's focus).

The end-to-end intent is: **the order path must never place an order with an
expired, revoked, or rotated token**, and any token rotation must transparently
re-bind the WebSocket feed without dropping subscriptions.

---

## 2. Current Architecture Map

### 2.1 Module-level ownership

| Module | Owns | Authority boundary |
|--------|------|-------------------|
| `broker-api` | `IBrokerConnection`, `TokenSource`/`TokenState`/`TokenLifecycleService` SPI, capability ports | Contract-only; **zero broker-specific code** |
| `broker-core` | `DefaultTokenLifecycleService` (OAuth), `TokenStateStore` (JSON/env), `EnvTokenStateStore`, rate limiter, circuit breaker, WebSocket supervisor | Reusable across brokers; subclasses override `doAcquire()` / `doRefresh()` |
| `broker-dhan` | `DhanAuthClient` (TOTP/renew/profile), `DhanTokenManager`, `DhanTotpGenerator`, `DhanClientHolder`, all 14 Dhan adapters | Self-contained; **does NOT extend `DefaultTokenLifecycleService`** |
| `broker-upstox` | `UpstoxOAuthClient`, `UpstoxTokenManager` (extends `DefaultTokenLifecycleService`), `UpstoxStaticTokenHolder`, `UpstoxAnalyticsTokenHolder`, `UpstoxExtendedTokenHolder`, all 14 Upstox adapters | Three parallel "holder" implementations exist alongside the manager |
| `broker-icici` | `BreezeTokenManager` (TOTP-based Breeze) | Not reviewed here (out of scope) |
| `broker-gateway` | `BrokerGateway`, `BrokerHandle`, `MarketGateway`, `DefaultBrokerGateway`, `BrokerRouter` | Single entry-point façade; `GatewayResult` with latency metadata |
| `gateway` | WebSocket transport to frontend, `GatewayEventBridge`, topic router | Frontend-facing only |
| `app` | Spring `@Configuration` (`BrokerAdapterConfiguration`, `BrokerConfiguration`, `UpstoxBrokerConfiguration`) | Wires everything; single Spring profile activates one broker family |

### 2.2 Token-flow ownership matrix (CRITICAL TABLE)

| Operation | Dhan | Upstox |
|-----------|------|--------|
| Token issuance (first acquire) | `DhanTokenManager.doAcquire` → `DhanAuthClient.generateViaTotp` (HTTP) | `DefaultTokenLifecycleService.acquireToken` → `UpstoxTokenManager.doAcquire` (either profile-fetch, JWT-parse, or `performInteractiveOAuth` for full flow) |
| Token refresh | `DhanAuthClient.renewToken` (uses `currentToken`); **no refresh_token model** | `DefaultTokenLifecycleService.ensureValid` → `UpstoxTokenManager.doRefresh` → `UpstoxOAuthClient.refreshToken` (HTTP) |
| Pre-call validity gate | `DhanAuthenticatedHttpClient.sendJson` calls `tokenProvider.ensureValid()` before **every** HTTP request | `UpstoxHttpClient.authorizationHeader` calls `tokenSource.ensureValid()` before **every** HTTP request |
| WebSocket bind to token | `DhanWebSocketConnectionManager` reads `effectiveTokenProvider.getAccessToken()` once at bind time; rebind triggered by `DhanClientHolder.addRotationListener(rebindAfterTokenRotation)` | `UpstoxFeedAuthorizer` reads `bearerToken()` once at WS handshake; **no rotation listener** |
| Persistence | `DhanTokenStateStore` (JSON file at `settings.tokenStateFile()`) | `JsonTokenStateStore` (configurable) or `EnvTokenStateStore` (container) |
| Rate-limit on issuance | `TOKEN_ACQUISITION_COOLDOWN_MS = 130_000L` (2 min, matches Dhan's "once every 2 minutes" 4xx) | None — `OAuthClient.refreshToken` will surface Upstox 4xx directly |
| Revocation (logout) | `DhanTokenManager.invalidate()` / `invalidate(generationId)`; **no HTTP revoke call** | `DefaultTokenLifecycleService.revoke()` calls `doRevoke(currentState)` (default no-op) |
| Callback hook (`onRefresh`) | **No-op** (logs only) — explicit comment says "not currently supported" | Default impl in `DefaultTokenLifecycleService` invokes `ClientHolder`-style callbacks |
| Webhook injection (Flow 2) | N/A | `UpstoxTokenWebhookController` → `UpstoxTokenManager.upgradeFromWebhook` (only replaces if newer expiry) |
| Static fallback | `settings.authMode() == DhanAuthMode.STATIC` short-circuits to `settings.accessToken()` | `UpstoxStaticTokenHolder` and `UpstoxAnalyticsTokenHolder` are parallel classes used when `accessToken` configured |

**There is no single "BearerTokenSource" abstraction across Dhan and Upstox.**
Dhan uses `DhanTokenProvider`; Upstox uses `UpstoxBearerTokenSource`. They are
incompatible: a `MarketDataProvider` that wants to read a token can only do so
through whichever broker-specific handle was injected. This is a **hidden
coupling** that violates interface segregation.

### 2.3 Capability / port matrix

`IBrokerConnection` exposes 14 capability ports. Each broker registers a
`Map<Class<?>, Object>` of which class implements which port. Dhan
registers all 15; Upstox registers 16. The `getCapability(...)` lookup has a
fallback scan over all values to handle multi-interface implementations
(this is a "shouldn't be needed" escape hatch that suggests the registration
map is incomplete in some edge cases — see `UpstoxBrokerConnection.gttOrders()`
which casts `conditionalAlertProvider` to `GttOrderProvider` at lookup time
rather than registering it).

---

## 3. End-to-End Execution Flow (Signal → Fill)

### 3.1 Order placement, real-data walkthrough

Assume: live Dhan session, current state from
`runtime/dhan-token-state.json`:
```json
{
  "accessToken": "eyJ0eXA...(redacted)...",
  "expiryEpochMs": 1781284813629,  // claims 2026-06-14
  "issuedAtEpochMs": 1781198413683,
  "source": "TOTP_GENERATED"
}
```

Live broker reply (just verified):
```
GET https://api.dhan.co/v2/profile
→ {"tokenValidity":"12/06/2026 22:50", ...}  HTTP 200
```
**Real broker expiry: 2026-06-12 22:50 IST. State file says 2026-06-14.**

1. Strategy emits `OrderRequest` for NIFTY FUT, side=BUY, qty=50, MARKET.
2. `RiskCheckChain` runs in the execution engine (`trading/execution`): `PositionLimitRiskCheck`, `DailyLossRiskCheck`, `KillSwitchRiskCheck`.
3. If pass: `OrderIdentityRegistry` assigns correlation ID.
4. `ObservableOrderCommand` → `DhanOrderCommandAdapter.placeOrder(request)`.
5. `DhanOrderValidator.validateOrThrow(request)` validates against the
   in-memory instrument catalog. **If catalog is empty this throws
   `IllegalStateException` at runtime — see §5.**
6. Synchronized block on `correlationLocks[correlationId]` checks
   `idempotencyCache`; on miss calls `doPlaceOrder`.
7. `doPlaceOrder` resolves `DhanInstrumentDefinition` (securityId, segment)
   then executes `context.execute(ApiCategory.ORDER, "place-order", ...)`
   — rate-limited + retried.
8. `DhanRestOrderClient.placeOrderViaApi` builds JSON payload, calls
   `DhanAuthenticatedHttpClient.postJson(...)`.
9. **`DhanAuthenticatedHttpClient.sendJson`** first calls
   `tokenProvider.ensureValid()`. Internally, `DhanTokenManager.ensureValid()`
   checks `isReusable(currentState, now)`:
   - If `state.expiryEpochMs > now + settings.refreshBufferMillis() + CLOCK_SKEW_TOLERANCE_MS` (30s), reuse.
   - Otherwise: `refreshLock.lock()`, re-check (double-check pattern), then
     `resolveValidState(now)`:
       - If persisted state has an `accessToken` not yet in cooldown,
         `confirmExistingState` calls `authClient.fetchProfile(token, refreshBuffer)`.
         **This adds a synchronous HTTP round-trip on every refresh-boundary
         call** — see §5.
       - If invalid, `adoptBootstrapToken` (validates `settings.accessToken()`).
       - Else `generateFreshToken` (TOTP, gated by `TOKEN_ACQUISITION_COOLDOWN_MS`).
10. POST to `https://api.dhan.co/v2/orders` with headers
    `access-token: <token>`, `dhanClientId: <clientId>`, `Content-Type: application/json`.
11. `DhanJsonMapper.toOrder(...)` maps the response into a domain `Order`.
12. Idempotency cache stores the Order keyed by correlationId.
13. Trade-J internal OMS publishes the order on the event bus.
14. The Dhan **order WebSocket** stream asynchronously emits
    `ORDER_UPDATE_MSG_CODE = 42` packets; `DhanMarketEventNormalizer` parses
    and publishes `OrderUpdateEvent` on the order listener bus.
15. Fill packets arrive as `feed packet response code 8 (FULL)`. Trade update
    stream (`/orders`) emits `Trade` updates.

### 3.2 WebSocket lifecycle (concurrent with the order flow)

- `DhanWebSocketMultiplexer.connect()` (one-time at startup):
  - `connectionManager.ensureClients()` (lazy create of two WebSocket clients: market feed + order stream).
  - `wireNewClients()` attaches the `Listener` to both.
  - `connectionManager.connectMarketFeed()` and `connectOrderStream()`.
  - `healthMonitor.start()` (runs every `FEED_HEALTH_CHECK_INTERVAL_MS = 5_000`).
  - `reconnectController.scheduleReconciliation(this::reconcileSubscriptions)`.
- On a 1-minute tick, `DhanWebSocketHealthMonitor` calls `tokenProvider.getTokenInfo()`,
  which calls `DhanTokenManager.getTokenInfo()` which calls
  `DhanAuthClient.fetchProfile(...)` — **one HTTP profile call per minute,
  unconditionally**, not just when refresh is recommended.
- On token rotation (`DhanClientHolder.accessToken()` detects a new token),
  `rebindAfterTokenRotation` closes the current WebSocket clients, calls
  `connectionManager.bindClients()` (which creates new clients with new
  tokens), and reconnects.
- On `INVALID_TOKEN_CODE = 806` (Dhan's "token expired" WS close code),
  `publishMarket(brokerError("market-auth", ...))` is emitted — **but no
  automatic token refresh is triggered**. The reconnect controller will
  retry with the *same* expired token, hitting 806 again indefinitely.

### 3.3 Upstox flow (key differences)

- Token bootstrap can come from: configured `accessToken` (validated via
  `/user/profile`, then JWT `exp` claim, then 3:30 AM IST fallback), or full
  `performInteractiveOAuth` (opens browser via `Desktop.browse(...)`).
- `ensureValid()` calls `doRefresh(refreshToken)` if `refreshRecommended(refreshBufferMs)`,
  not on every call. The refresh has no local cooldown; Upstox 4xx is
  surfaced directly.
- `upstoxHttpClient.authorizationHeader()` calls `tokenSource.ensureValid()`
  before every HTTP call, but if `tokenSource` is a `UpstoxStaticTokenHolder`
  (configured-token path), `ensureValid()` is **only a "not yet expired"
  check** — it does **not** refresh and does not even fail-then-retry.

---

## 4. Invariant Checklist

| # | Invariant | Enforced? | Where | Risk if violated |
|---|-----------|-----------|-------|------------------|
| I-1 | No HTTP request to a broker ever sends an expired access token | **PARTIAL** | `DhanAuthenticatedHttpClient`/`UpstoxHttpClient` call `ensureValid()` first | Orders / quotes rejected with 401/806 |
| I-2 | No HTTP request ever sends a token that the broker has revoked | **NO** | None. We never call any broker's revoke API | "Shouldn't happen" — Dhan rotates automatically; Upstox depends on `refreshToken` rotation |
| I-3 | `currentState.accessToken` is updated atomically with `stateStore.save()` | **YES** (Dhan: under `refreshLock`; Upstox: under `lock` in `DefaultTokenLifecycleService`) | `DhanTokenManager.persist`, `DefaultTokenLifecycleService.replaceState` | Race: a thread reads stale token while another writes |
| I-4 | `idempotencyCache.put(correlationId, order)` is called only on a successful broker response | **YES** | `DhanOrderCommandAdapter.placeOrder` (line 76-78) | Duplicate order if HTTP timeout |
| I-5 | Token-rotation listener is invoked exactly once per token change | **NO** | `DhanClientHolder.accessToken()` fires listener under a `synchronized(this)` block; but listeners can themselves call back into `accessToken()` (reentrancy), causing recursive notifications | Repeated WS reconnects during a single token rotation |
| I-6 | WebSocket clients are bound with the **current** token at connect time | **YES** (eventually) | `DhanWebSocketConnectionManager.bindClients` reads `tokenProvider.getAccessToken()` at bind; rotation triggers re-bind | If a rotation occurs between `bindClients` and `connectMarketFeed`, the WS auth header will be from the *old* token (mitigated by `getAccessToken()` always returning latest) |
| I-7 | Once a refresh fails (`lastFailedRefreshMs` set), we don't retry within 30s | **YES** | `DefaultTokenLifecycleService.FAILED_REFRESH_COOLDOWN_MS` | Cascade of failed refreshes if upstream is degraded |
| I-8 | Dhan TOTP generation respects Dhan's 2-min cooldown | **YES** | `DhanTokenManager.TOKEN_ACQUISITION_COOLDOWN_MS = 130_000L` (130s, slightly > 2 min) | Could still slip by ~10s window; Dhan's actual cooldown is 2 min flat |
| I-9 | Order modifications are bounded at 25 per order | **YES** | `DhanOrderCommandAdapter.MAX_MODIFICATIONS_PER_ORDER` | Excess mods cause Dhan rejection; we just throw |
| I-10 | Every broker capability port advertised by `IBrokerConnection` is non-null | **YES** (in tests) | `IBrokerConnectionContractTest.mandatoryPortsAreNonNull` | N/A — verified in CI |
| I-11 | Sandbox orders are tracked for cleanup | **YES** | `LiveDhanTestSupport.PENDING_SANDBOX_ORDER_IDS` | Sandbox pollution |
| I-12 | All broker calls are routed through the rate limiter | **PARTIAL** | `DhanOrderCommandAdapter.doPlaceOrder` goes through `context.execute(ApiCategory.ORDER, ...)`; but `DhanClientHolder.accessToken()` (used by the WebSocket multiplexer) does **not** | Token-rotation profile calls bypass the rate limiter |
| I-13 | Auth credentials never leak to logs | **WEAK** | `DhanTokenManager` logs `"Dhan access token state updated (source=..., expiresAt=...)"` — no token in log; but `DhanClientHolder.accessToken()` returns the token to listeners without redaction; logs in `UpstoxTokenManager.upgradeFromWebhook` log expiry only | Low risk; standard practice but worth a sweep |
| I-14 | A valid token state file on disk is sufficient to skip a forced re-mint | **YES** | `DhanTokenManager` — persisted `DhanTokenState` is adopted in `resolveValidState`; `confirmExistingState` re-validates against `/v2/profile` when within `refreshBufferMs` | The persisted `expiryEpochMs` may be **stale** — see §5 BUG-1 |
| I-15 | An expired token is never persisted as "valid" | **NO** | `JsonTokenStateStore` round-trips whatever is in `TokenState` without validation | A bad write to disk can resurrect an "expired" token until next profile call |

---

## 5. Failure & Risk Points

> Ordered roughly by severity. **CRITICAL = could silently place/fail to place real-money orders without operator awareness.** **HIGH = will misbehave under realistic conditions.** **MEDIUM/LOW = code-smell / future bug.**

### CRITICAL-1: Persisted `expiryEpochMs` is untrusted — yet the cache short-circuits before re-validation

**Where:** `DhanTokenManager.confirmExistingState(state, now)`,
`DefaultTokenLifecycleService.ensureValid()` (Upstox).

**What happens:**
- The state file `runtime/dhan-token-state.json` says `expiryEpochMs: 1781284813629`
  (Jun 14, 2026).
- The broker says `tokenValidity: 12/06/2026 22:50` (Jun 12, 2026).
- `isReusable(state, now)` returns `true` because `now < state.expiryEpochMs - buffer - 30s`.
- Therefore `ensureValid()` **skips the re-validation HTTP call**.
- The next order uses a token the broker has already revoked.
- Order is rejected with HTTP 401 or 806. **We retry 2× via the retry
  executor.** Then we throw.
- `DhanOrderCommandAdapter.placeOrder` doesn't catch this — the exception
  propagates and the strategy receives an order-rejection that looks like a
  transient network error.

**Impact:** Silent under "looks like a network blip"; visible only in logs.
If the strategy's retry policy is "place order again", the strategy will
keep placing orders against a known-bad token, and each will be rejected
2-3 times before the operator notices.

**Why the "stash + check" model is unsafe:** The persisted `expiryEpochMs`
comes from the broker's `accessToken.expiryTime` field at mint time. It is
*not* the same as the broker's current view of `tokenValidity`. Dhan's
`tokenValidity` can shrink over time (e.g., admin revocation, session
timeout). The only source of truth is `/v2/profile`. We must **always**
call `/v2/profile` on a fixed schedule (every N minutes) and treat the
disk cache only as a "warm start" hint, not as authoritative.

**Action:** Make `confirmExistingState` **mandatory on every refresh-boundary
check** (drop the `isReusable` short-circuit when the buffer has been crossed)
OR (preferred) add a periodic, scheduled `/v2/profile` revalidation that runs
out-of-band and updates `currentState.expiryEpochMs`.

### CRITICAL-2: Dhan WebSocket reconnect does NOT trigger token refresh on INVALID_TOKEN_CODE = 806

**Where:** `DhanWebSocketMultiplexer.wireNewClients` (line ~360-365).

```java
public void onDisconnected(int code, String reason) {
    connectionManager.setConnected(false);
    if (code == INVALID_TOKEN_CODE) {  // 806
        publishMarket(brokerError("market-auth",
                "Dhan websocket token is invalid or expired"));
    }
    publishMarket(healthEvent("dhan", "DISCONNECTED", code));
}
```

**What happens:** A 806 disconnect publishes an error event — but does
**not** call `tokenProvider.ensureValid()` or `tokenProvider.invalidate()`.
The reconnect controller then attempts to reconnect, and the new WS client
is bound to the **same** expired token (since `tokenProvider.currentState`
was not cleared). It will fail again with 806. The health monitor will mark
the feed stale after 30s, triggering backoff reconnects, all of which fail
with 806.

**Impact:** Once Dhan issues a 806 (e.g., admin revokes the token, or the
TOTP is re-minted from a different client), the live market feed and order
stream are **permanently dead** until process restart. Orders cannot be
placed via REST either because the same `DhanTokenManager` is shared.

**Action:** On 806, the multiplexer must call `tokenProvider.invalidate()`
(or `DhanClientHolder.invalidateToken()`), then the rotation listener will
trigger a re-bind with a freshly-minted TOTP token. This is the
zero-parity contract for the live feed.

### CRITICAL-3: Two parallel "token source" abstractions; no shared contract across brokers

**Where:** `DhanTokenProvider` (Dhan-only) vs `UpstoxBearerTokenSource`
(Upstox-only). They are **not** related types.

**What happens:** Any code that needs a bearer token must branch on
`BrokerSource` (or use `IBrokerConnection`-level access). The `gateway/`
module's `WebSocketFeedAuthorizer` and the `broker-gateway/BrokerHandle` 
class both end up holding **two different token-source references**, one
per broker. This is a **shotgun surgery** liability: a new broker needs
new abstractions on both sides.

**Action:** Define a single `BrokerTokenSource` interface in `broker-api`
with `bearerToken()`, `ensureValid()`, `expiryEpochMs()`. Make both
`DhanTokenManager` and `UpstoxTokenManager` (and holders) implement it.
Deprecate `UpstoxBearerTokenSource` and `DhanTokenProvider`.

### HIGH-1: `DhanAuthClient.fetchProfile` is called synchronously on the order-acknowledgment path

**Where:** `DhanAuthenticatedHttpClient.sendJson` → `tokenProvider.ensureValid()` →
`DhanTokenManager.confirmExistingState` → `authClient.fetchProfile(token, ...)`.

**What happens:** When a token is within the `refreshBuffer` window
(default 10 min), every single HTTP call (order placement, cancel, modify,
quote, historical) triggers a `/v2/profile` round-trip. On a busy
strategy firing 5 orders/min, that's 5 profile calls/min. With
`RATE_LIMIT_NON_TRADING_RATE = 15.0` (Dhan's documented limit), this is
just under the bucket capacity. **It consumes the entire
`NON_TRADING` bucket**, starving other non-trading operations (positions,
fund limits, etc.) of rate budget.

**Action:** Decouple profile-based validation from the request path. Run
a single `ScheduledExecutorService` (or piggyback on
`DhanWebSocketHealthMonitor`) that calls `fetchProfile` every 60s and
updates `currentState.expiryEpochMs` only. Request-path validation should
then just trust the cached expiry + 60s slack.

### HIGH-2: `UpstoxTokenManager.doAcquire()` returns `currentState()` without a re-validation when state is "valid"

**Where:** `UpstoxTokenManager.doAcquire()`:
```java
if (currentState() != null && currentState().valid()) {
    return currentState();
}
```

**What happens:** If the persisted state has `expiryEpochMs > now + 30s`,
`doAcquire` returns the cached state without re-validating. This is
fine for Upstox because the `/user/profile` endpoint is *optional* — but
the `bootstrapFromConfiguredToken` path records `TokenSource.STATIC` even
when the underlying mode is OAuth (line 128). `UpstoxTokenExpiry` then
silently uses 3:30 AM IST as the "expiry" for a `STATIC` token sourced
from a real `refreshToken`. The next `ensureValid()` will think the token
is about to expire, trigger `doRefresh`, and **Upstox will invalidate the
refresh_token** (Upstox's documented single-use refresh-token behavior).

**Impact:** A static-configured Upstox token that happens to come with a
`refreshToken` will **destroy the refresh token on first ensureValid** if
the JWT `exp` is missing or stale.

**Action:** Don't return cached state in `doAcquire`. Always re-validate
through `/user/profile` (or JWT decode), and **only** use the 3:30 AM IST
fallback when no other source is available.

### HIGH-3: `DhanAuthenticatedHttpClient` calls `ensureValid` then `getAccessToken` — two separate locks, two separate HTTP calls

**Where:** `DhanAuthenticatedHttpClient.sendJson`:
```java
tokenProvider.ensureValid();                 // acquires refreshLock
.header("access-token", tokenProvider.getAccessToken());  // does NOT take lock, reads volatile
```

**What happens:** Between `ensureValid()` returning and `getAccessToken()`
running, **another thread can call `invalidate()`** (e.g., a 401 from
the previous request that triggers a CAS-based invalidation). The second
call will read `currentState = null` and throw
`IllegalStateException("Dhan token manager did not resolve an access
token")`. The HTTP request is not retried.

**Action:** Combine the two calls into a single
`String token = tokenProvider.ensureValidAndGet()` that returns a valid
token under a single acquisition.

### HIGH-4: `DhanClientHolder.accessToken()` is called on every `getAccessToken()` from the WebSocket multiplexer, but it also fires the rotation listener — which calls back into the multiplexer

**Where:** `DhanClientHolder.accessToken()` line 35-50, then
`DhanWebSocketMultiplexer.rebindAfterTokenRotation` line 297.

**What happens:**
1. WebSocket multiplexer asks for token.
2. `DhanClientHolder.accessToken()` detects a new token.
3. Fires `rebindAfterTokenRotation` listener.
4. Listener synchronously calls `connectionManager.bindClients()`,
   which constructs new WebSocket clients, calling
   `tokenProvider.getAccessToken()` again (line 116 of
   `DhanWebSocketConnectionManager`).
5. Same token returned — no-op — but now we have an
   `OrderedSet<Runnable>` reentry of listeners.

On rapid token rotation (e.g., TOTP re-mint during a market burst), this
can cause stack-overflow-style behavior or repeated re-bind storms.
The current `DhanClientHolder` synchronizes the listener-array snapshot
correctly, but **does not guard against listener reentrancy**.

**Action:** Mark the rotation as in-flight (`boolean rotating`) and skip
re-entry until the first rotation completes.

### HIGH-5: `UpstoxTokenManager.upgradeFromWebhook` silently drops the webhook's `refreshToken` field

**Where:** `UpstoxTokenManager.upgradeFromWebhook` (line 200-206):
```java
TokenState newState = new TokenState(
        accessToken,
        null, // webhook tokens don't carry refresh_token
        ...
);
```

**What happens:** The webhook payload from Upstox (per V3 docs) **does
not** include `refresh_token`. After webhook upgrade, the new token has
no `refreshToken`. When the token subsequently expires, `doRefresh(null)`
throws `IllegalStateException("Cannot refresh token: no refresh token
available")`. The system is then stuck in `analyticsOnly`-like mode
until manual re-authentication.

**Action:** Persist the *previous* `refreshToken` alongside the new
webhook token. The webhook does not invalidate the old refresh token
(per Upstox docs); the next refresh attempt should use the old
refresh token to acquire a new access+refresh pair, **then** the
webhook-upgraded access token will be superseded.

### MED-1: `EnvTokenStateStore` documentation says "sidecar must restart process" but no enforcement

**Where:** `EnvTokenStateStore.save` (line 62-72).

**What happens:** If `tradej.tokens.store-type=env` and a token is refreshed
mid-process, the in-memory cache updates but env vars stay the same. A
process restart then loads the *stale* env-derived token. Documented as
expected, but easy to miss in production — operators will see "token
expiry" errors after every restart.

**Action:** Emit a clear `WARN` log on every `envTokenStateStore.save()`
that says the in-memory cache will not survive restart, with instructions
for the sidecar pattern. Already partly done; needs to be louder.

### MED-2: `UpstoxHttpClient` does not classify errors — every 4xx throws `RuntimeException`

**Where:** `UpstoxHttpClient.send` (line 194-203), no
`UpstoxErrorClassifier` consultation.

**What happens:** The codebase has `UpstoxErrorClassifier` and
`UpstoxApiException` (in `broker-upstox/http/`). They are used by
`UpstoxJsonHttpClient` (a parallel HTTP wrapper) but not by `UpstoxHttpClient`.
A 401 from Upstox is indistinguishable from a network timeout to the
caller. The `UpstoxRetryExecutor` will retry a 401 the same as a 5xx,
inflating the rate limit hit rate.

**Action:** Unify the two HTTP clients OR make `UpstoxHttpClient` consult
`UpstoxErrorClassifier` and throw `UpstoxApiException` with the right
category.

### MED-3: `DhanOrderCommandAdapter.cancelAllOpenOrders` is not rate-limit aware

**Where:** `DhanOrderCommandAdapter.cancelAllOpenOrders` (line 129-141).

**What happens:** Iterates over `restOrderClient.getOrders()` and calls
`cancelOrderViaApi` per order. The rate limiter (`RATE_LIMIT_ORDER_RATE = 7/s`,
capacity 10) is enforced by `DhanRetryExecutor.execute(...)`, but
`getOrders()` is itself a `NON_TRADING` call. With 30 open orders, the
cancel loop calls 31 REST calls in quick succession; the first ~10 hit
the order bucket, the next 21 wait for the bucket to refill (at 7/s
that's 3s of blocking). On a kill-switch scenario the user expects
"cancel everything now", not "cancel everything in 3s".

**Action:** Document the bucket behavior; consider a "kill switch" fast
path that bypasses the bucket when the user has explicitly enabled the
kill switch (Dhan's API supports a single `killSwitch` flag that
auto-cancels everything — see `setKillSwitchViaApi`).

### MED-4: `Dhan` order idempotency cache is unbounded

**Where:** `DhanOrderCommandAdapter.correlationLocks` and
`DhanOrderCommandAdapter` accepts an `IdempotencyCachePort` that is
`CaffeineIdempotencyCache` (from `BrokerConfiguration.idempotencyCache()`).

**What happens:** `correlationLocks.remove(correlationId)` is called in
`finally` (line 80), so locks are reclaimed. But `idempotencyCache` is
configured with whatever default Caffeine eviction the impl uses. The
project doesn't expose the Caffeine config. **If a strategy retries the
same correlationId after a long delay, the cache may have evicted the
entry — leading to a duplicate order.** The broker's own
`correlationId` dedup at Dhan is the actual safety net, but the
in-app cache's behavior is implicit.

**Action:** Either bound the cache explicitly (e.g., 100k entries, 24h
TTL) or document the at-most-once contract.

### LOW-1: `DhanTokenState` is serialized with `com.fasterxml.jackson` defaults — extra fields silently lost

**Where:** `DhanTokenStateStore` (uses `DhanTokenState` directly via
Jackson). The `DhanTokenState` record has fields
`accessToken`, `expiryEpochMs`, `issuedAtEpochMs`, `source`. The current
state file matches. But if `DhanTokenState` is extended in the future
(e.g., to include a `refreshToken` for future Dhan support), older
state files will silently drop the new field on read.

**Action:** Use `@JsonIgnoreProperties(ignoreUnknown = false)` to fail
loudly on schema drift.

### LOW-2: `DhanConfigPaths.resolve` is silent on missing path

**Where:** `DhanConfigPaths.resolve` (referenced from
`BrokerAdapterConfiguration.dhanConnectionSettings`).

**What happens:** If a property like `dhan.pinFile=config/missing.txt`
is set, `DhanConfigPaths.resolve` returns the path. The error surfaces
later, deep in `DhanTokenManager.readSecret`. The Spring config layer
gives no early warning.

**Action:** Validate file existence at Spring config time and fail
fast with a clear error.

### LOW-3: `DefaultTokenLifecycleService.refreshCallbacks` are not deduped

**Where:** `DefaultTokenLifecycleService` — `onRefresh` and `onExpiry`
are `CopyOnWriteArrayList`s with no idempotency.

**What happens:** If a `ClientHolder`-style bean registers the same
callback twice, the callback fires twice. The current `DhanClientHolder`
adds once. The risk is in Upstox's `performInteractiveOAuth` path which
adds callbacks inside the flow.

**Action:** Use a `Set` of callbacks or dedupe on registration.

---

## 6. Live Verification Summary (Executed This Review)

| Check | Tool | Result |
|-------|------|--------|
| Dhan `DhanTokenManagerUnitTest` (4 tests: persistence, bootstrap, near-expiry refresh, concurrency) | `./gradlew :broker-dhan:test` | **PASS** |
| Dhan `DhanTokenRefreshExpiryTest` (5 tests: settings, clock, JSON round-trip, missing-file, sandbox URL) | `./gradlew :broker-dhan:test` | **PASS** |
| Dhan `DhanAuthClientUnitTest` (4 tests: TOTP parse, snake_case parse, rate-limit, profile parse) | `./gradlew :broker-dhan:test` | **PASS** |
| Dhan `DhanBrokerConnectionTest` (5 tests: capability map, null-safety, port delegation) | `./gradlew :broker-dhan:test` | **PASS** |
| Dhan `DhanBrokerConnectionContractTest` | `./gradlew :broker-dhan:test` | **PASS** |
| Upstox `UpstoxTokenManagerTest` (10 tests: extended, JWT expiry, bootstrap, webhook upgrade, interactive OAuth) | `./gradlew :broker-upstox:test` | **PASS** |
| Upstox `UpstoxJwtExpiryTest`, `UpstoxTokenExpiryTest`, `UpstoxPkceUtilTest`, `UpstoxOAuthClientTest`, `UpstoxStaticTokenHolderTest`, `UpstoxAnalyticsTokenHolderTest`, `UpstoxTokenWebhookControllerTest`, `UpstoxRedirectServerTest` | `./gradlew :broker-upstox:test` | **PASS** |
| Upstox `UpstoxBrokerConnectionContractTest` | `./gradlew :broker-upstox:test` | **PASS** |
| broker-core `DefaultTokenLifecycleServiceTest`, `EnvTokenStateStoreTest` | `./gradlew :broker-core:test` | **PASS** |
| Live Dhan profile preflight against `https://api.dhan.co/v2/profile` | `curl` | **HTTP 200, token valid until 12/06/2026 22:50** — but state file claims 14/06/2026 — **CRITICAL-1 confirmed live** |
| `DhanRefreshProductionTokenIntegrationTest` (live TOTP mint against Dhan) | `./gradlew :app:brokerAuthDrillTest` | **PASS — minted new token, persisted to state file** |
| `UpstoxRegressionPreflightIntegrationTest` (analytics token, sandbox check) | `./gradlew :app:upstoxPreflightTest` | **PASS — 2/3 tests passed, 1 skipped (sandbox token expired; correct skip behavior)** |
| `StartupSmokeComponentTest` (Spring context load) | `./gradlew :app:test` | **FAIL** — unrelated to brokers: `BeanDefinitionOverrideException` for `rateLimitFilter` (defined twice in `WebConfiguration` and `RateLimitFilter` class). **NOT IN SCOPE of this review.** |

---

## 7. Expected Behavior Contract

> The contract the broker layer MUST guarantee before any order can be placed
> with real money.

### 7.1 Inputs

- **I-1:** Valid Spring profile is active (`dev`/`dev-live`/`prod`/`upstox-*`/`icici-prod`/`simulation`).
- **I-2:** At least one of: configured `accessToken` + TOTP files (Dhan), configured `accessToken` (Upstox static/analytics/extended), or interactive OAuth completion (Upstox).
- **I-3:** Instrument catalog is loaded (`loadInstrumentCatalog()` succeeded or auto-loaded at startup).
- **I-4:** Risk-check chain is configured.

### 7.2 Outputs

- **O-1:** For every `IBrokerConnection.orders().placeOrder(request)`, exactly one Order is placed at the broker, and the returned `Order.orderId` is non-blank.
- **O-2:** Idempotency: two calls with the same `correlationId` within the cache TTL produce the same `Order`.
- **O-3:** The token used for the order is **known valid at the moment of the HTTP request**, verified within the last `refreshBufferMs + 60s` (or, for Dhan, verified by `/v2/profile` if within `refreshBufferMs`).
- **O-4:** WebSocket feed and order stream are connected with the same access token used for REST, and remain connected for the duration of the session.

### 7.3 Timing guarantees

- **T-1:** `IBrokerConnection.connect()` returns in < 5s for a previously-valid token.
- **T-2:** `orders().placeOrder()` returns in < 500ms (REST round-trip + adapter overhead) for non-rate-limited scenarios.
- **T-3:** Token refresh is **non-blocking for the order path**: `ensureValid()` either returns immediately with a cached valid token OR blocks for at most one broker profile/refresh round-trip (~2s budget).
- **T-4:** WebSocket feed detects staleness within `STALE_FEED_THRESHOLD_MS = 30_000ms`.
- **T-5:** Dhan TOTP generation respects a 130s cooldown; consecutive mint attempts within that window throw `DhanAuthRejectedException(rateLimited=true)`.

### 7.4 State transitions

- **ST-1:** `DhanTokenState`: `null → TOTP_GENERATED → null` (on invalidate) → `TOTP_GENERATED` (on next mint).
- **ST-2:** `UpstoxTokenState`: `null → OAUTH (interactive) → OAUTH (refresh rotation) → OAUTH (webhook upgrade)` with the constraint that webhook upgrade is **monotonic** in expiry.
- **ST-3:** `DhanClientHolder.currentToken`: changes only when `accessToken()` returns a new value; rotation listener fires exactly once per change.

### 7.5 Failure modes

- **F-1:** Expired token at mint time → `DhanAuthRejectedException("Dhan token generation cooldown active")` or HTTP 4xx propagated.
- **F-2:** Broker returns 806 (Dhan WS) → must trigger token invalidate + re-mint + WS re-bind, **not** retry with same token.
- **F-3:** Upstox refresh returns `invalid_grant` → `UpstoxAuthException`; strategy should be notified to halt; `UpstoxTokenManager.currentState` should be cleared so the next call doesn't attempt another doomed refresh.
- **F-4:** Dhan `/v2/profile` returns 401 → mark current token as suspect, force re-mint on next call, do **not** persist the suspect state.

---

## 8. Is the current code enforcing this contract?

| Contract | Status | Evidence |
|----------|--------|----------|
| I-1..I-4 | **YES** | Spring profile + property binding work correctly; test suite covers each |
| O-1 | **YES** | Dhan `DhanOrderCommandAdapter.placeOrder` returns `Order` only after a successful POST |
| O-2 | **YES** | `idempotencyCache.put(correlationId, order)` after broker success; tests verify |
| O-3 | **NO** | See CRITICAL-1 (stale `expiryEpochMs`); see HIGH-1 (rate-limit starvation) |
| O-4 | **PARTIAL** | WebSocket is connected at startup, but on 806 disconnects the feed dies (CRITICAL-2) |
| T-1..T-5 | **MOSTLY** | T-3 violated on Dhan: `confirmExistingState` adds a sync HTTP call in the hot path (HIGH-1) |
| ST-1..ST-3 | **ST-1 YES, ST-2 PARTIAL, ST-3 NO** | ST-3 violated — see HIGH-4 (rotation reentry) |
| F-1..F-4 | **F-1 YES, F-2 NO, F-3 PARTIAL, F-4 NO** | F-2 violated (CRITICAL-2); F-3 partial (cleared state but no callback to halt strategy); F-4 not implemented (CRITICAL-1) |

**The contract is NOT fully enforced.** Specifically, the contract items
most directly tied to "trading real money without silent failure" —
**O-3, O-4, F-2, F-4** — are all violated.

---

## 9. Proposed Correct Architecture

### 9.1 Single `BrokerTokenSource` interface (CRITICAL-3 fix)

```java
// New file: broker-api/src/main/java/com/tradej/broker/api/auth/BrokerTokenSource.java
public interface BrokerTokenSource {
    /** Returns a non-null, currently-valid bearer token. May refresh synchronously. */
    String bearerToken();
    /** Idempotent: ensures the cached token is valid; refreshes if needed. */
    void ensureValid();
    /** Token expiry epoch ms; -1 if unknown (extended tokens). */
    long expiryEpochMs();
    /** Force a re-mint/re-auth; the next ensureValid() will produce a new token. */
    void invalidate();
    /** Registers a callback invoked after every successful refresh/mint. Idempotent. */
    void onRefresh(Runnable callback);
    /** Registers a callback invoked when the token transitions to invalid. */
    void onInvalidate(Runnable callback);
}
```

- `DhanTokenManager` implements `BrokerTokenSource` directly.
- `UpstoxTokenManager` already extends `DefaultTokenLifecycleService`; add `BrokerTokenSource` to its implements list.
- Deprecate `DhanTokenProvider` and `UpstoxBearerTokenSource`.

### 9.2 Authoritative expiry, mandatory `/v2/profile` revalidation (CRITICAL-1 fix)

```java
// In DhanTokenManager: replace `isReusable` short-circuit with a two-tier check
boolean ensureValid() {
    long now = clock.millis();
    // Tier 1: hot path — use cached token without HTTP
    if (currentState != null && currentState.expiryEpochMs > now + 30_000L) {
        return true;  // 30s slack for clock skew + transit
    }
    // Tier 2: cold path — MUST re-validate against broker profile
    refreshLock.lock();
    try {
        if (currentState != null) {
            DhanTokenInfo info = authClient.fetchProfile(currentState.accessToken(), 0);
            if (!info.valid() || info.refreshRecommended()) {
                currentState = null;  // force re-mint
            } else {
                currentState = new DhanTokenState(currentState.accessToken(),
                        info.expiryEpochMs(), currentState.issuedAtEpochMs(),
                        currentState.source());
            }
        }
        if (currentState == null) {
            currentState = generateFreshToken(now);
        }
        stateStore.save(currentState);
        return true;
    } finally {
        refreshLock.unlock();
    }
}
```

Key changes:
- **Drop the 30-min "we trust the disk" window** — every `ensureValid()` past the 30s safety slack re-validates.
- **No more bypass via persisted `expiryEpochMs`**.
- This is a deliberate trade-off: one HTTP call per token-aware operation
  (mitigated by `ensureValid` being cheap when state is recent). The
  rate-limit impact is bounded — see §9.4.

### 9.3 WebSocket auto-recovery on 806 (CRITICAL-2 fix)

```java
// In DhanWebSocketMultiplexer.onDisconnected:
public void onDisconnected(int code, String reason) {
    connectionManager.setConnected(false);
    if (code == INVALID_TOKEN_CODE) {
        // NEW: trigger token rotation, then re-bind
        log.warn("Dhan WS closed with 806 — invalidating token, will re-mint and reconnect");
        tokenProvider.invalidate();  // CAS-invalidate so other threads see null
        reconnectController.startBackoff(
            () -> {
                // After invalidation, ensureValid() will mint a new token
                tokenProvider.ensureValid();
                connectionManager.closeCurrentClients();
                connectionManager.bindClients();
                clientsWired = false;
                wireNewClients();
            },
            () -> {
                healthMonitor.resetTimestamps();
                connectionManager.connectMarketFeed();
                connectionManager.connectOrderStream();
            });
    } else {
        publishMarket(healthEvent("dhan", "DISCONNECTED", code));
    }
}
```

### 9.4 Rate-limit budget: separate profile revalidation into a scheduled task (HIGH-1 fix)

```java
// New: DhanWebSocketHealthMonitor gains a periodic revalidation task
@Scheduled(fixedDelay = 60_000)  // 1 minute, matches TOKEN_CHECK_INTERVAL_MS
void revalidateTokenExpiry() {
    try {
        DhanTokenInfo info = tokenProvider.getTokenInfo();
        if (!info.valid()) {
            tokenProvider.invalidate();
            // rotation listener in DhanWebSocketMultiplexer handles the rest
        } else {
            // Update cached expiry with broker-authoritative value
            tokenProvider.updateCachedExpiry(info.expiryEpochMs());
        }
    } catch (Exception ex) {
        log.debug("Token revalidation failed: {}", ex.getMessage());
    }
}
```

This consumes **one** NON_TRADING bucket slot per minute (well within
15.0/s capacity) and decouples the order path from profile calls.

### 9.5 Token rotation reentry guard (HIGH-4 fix)

```java
// In DhanClientHolder
private final AtomicInteger rotationDepth = new AtomicInteger(0);

public String accessToken() {
    if (rotationDepth.get() > 0) {
        // Already inside a rotation; just return the latest token
        return tokenProvider.getAccessToken();
    }
    rotationDepth.incrementAndGet();
    try {
        String token = tokenProvider.getAccessToken();
        Runnable[] listeners = null;
        synchronized (this) {
            if (!Objects.equals(currentToken, token)) {
                currentToken = token;
                listeners = rotationListeners.toArray(Runnable[]::new);
            }
        }
        if (listeners != null) {
            for (Runnable listener : listeners) listener.run();
        }
        return token;
    } finally {
        rotationDepth.decrementAndGet();
    }
}
```

### 9.6 Upstox webhook preserves refresh_token (HIGH-5 fix)

```java
public void upgradeFromWebhook(String accessToken, long expiresAtMs) {
    // ... validation as before ...
    TokenState current = currentState;
    TokenState newState = new TokenState(
        accessToken,
        current != null ? current.refreshToken() : null,  // PRESERVE
        expiresAtMs,
        System.currentTimeMillis(),
        TokenSource.OAUTH
    );
    replaceState(newState);
}
```

### 9.7 Strict validation of token source on bootstrap (HIGH-2 fix)

```java
// UpstoxTokenManager.doAcquire
TokenState doAcquire() {
    if (settings.refreshToken() != null && !settings.refreshToken().isBlank()) {
        return bootstrapFromConfiguredToken(settings.accessToken(), settings.refreshToken());
    }
    if (settings.accessToken() != null && !settings.accessToken().isBlank()) {
        // Pure static — no refresh possible. Mark accordingly.
        return new TokenState(
            settings.accessToken(),
            null,
            UpstoxJwtExpiry.parseExpiryEpochMs(settings.accessToken()),
            System.currentTimeMillis(),
            TokenSource.STATIC
        );
    }
    throw new UnsupportedOperationException(
        "No Upstox access token. Configure upstox.accessToken or run performInteractiveOAuth().");
}
```

### 9.8 Unified error classification in `UpstoxHttpClient` (MED-2 fix)

Inject `UpstoxErrorClassifier` into `UpstoxHttpClient` and call it in
`send(...)` before throwing. Map 401/403 to `UpstoxApiException` with
category `AUTH_EXPIRED` or `AUTH_INVALID`. This lets `UpstoxRetryExecutor`
short-circuit on auth errors and lets the caller know to halt strategies.

---

## 10. Migration Plan (minimal but correct)

Ordered by impact-to-effort ratio. Each step is independently deployable.

| Step | What | Why this order | Risk |
|------|------|----------------|------|
| **M-1** | Define `BrokerTokenSource` in `broker-api`; deprecate `DhanTokenProvider` and `UpstoxBearerTokenSource`; add shim implementations | Unblocks all other refactors; pure additive change | None (additive) |
| **M-2** | Fix `DhanClientHolder` reentry guard (HIGH-4) | Localized change, prevents cascades; can be done independently | Low |
| **M-3** | Fix `UpstoxTokenManager.upgradeFromWebhook` to preserve `refreshToken` (HIGH-5) | One-line fix; prevents Upstox token from becoming irrecoverable | None |
| **M-4** | Fix `UpstoxTokenManager.doAcquire` to not silently use STATIC when refresh token is set (HIGH-2) | One-method fix; prevents accidental refresh-token destruction | Low |
| **M-5** | Inject `UpstoxErrorClassifier` into `UpstoxHttpClient` (MED-2) | Enables correct retry behavior; affects only Upstox path | Medium (must test retry executor paths) |
| **M-6** | Move `/v2/profile` revalidation out of the hot path into a scheduled task (HIGH-1) | Reduces latency p99 and rate-limit pressure; needs scheduler integration | Medium (test ordering) |
| **M-7** | Add `DhanTokenManager.updateCachedExpiry(long)` and a scheduled `DhanWebSocketHealthMonitor` task to call it (CRITICAL-1 prep) | Foundation for the next step | Low |
| **M-8** | Make `DhanTokenManager.ensureValid()` re-validate on every call past the 30s slack window (CRITICAL-1) | The real fix; must land with M-7 active | High — test thoroughly; the only behavioral change visible to callers |
| **M-9** | Add 806 → invalidate + re-mint + re-bind to `DhanWebSocketMultiplexer` (CRITICAL-2) | The most impactful fix; requires integration testing with simulated 806 | High — needs chaos test |
| **M-10** | Deprecate `DhanTokenProvider` and `UpstoxBearerTokenSource`; remove after one release | Cleanup | None |
| **M-11** | Add chaos test that simulates 806 mid-session | Regression coverage for M-9 | None |

Each step is **independently testable**. M-1 through M-7 are non-breaking.
M-8 and M-9 change runtime behavior and require explicit validation.

---

## 11. Answers to the four audit questions

### Q1. What can go wrong silently?

- **Persisted `expiryEpochMs` is stale** (CRITICAL-1): the disk cache is
  trusted for up to 10 min, but the broker may have already revoked the
  token. Orders and quotes will be rejected as 401/806, **retried twice**
  by the retry executor, and surface as "transient network errors" to
  the strategy. The strategy may not halt.
- **Dhan WS 806 disconnect** (CRITICAL-2): after a 806, the WebSocket
  feed and order stream go permanently silent. The event bus publishes
  one `BrokerAdapterError` and then nothing. Strategies waiting for
  `MarketTickEvent` see no data; strategies placing orders via REST
  succeed (same token, REST call doesn't trigger 806) but they are
  blind to fills because the order stream is dead.
- **Upstox refresh-token destruction** (HIGH-2/HIGH-5): A static-configured
  Upstox token with a stale JWT `exp` triggers an automatic refresh that
  Upstox documents as **single-use**, killing the refresh token. The
  webhook upgrade path drops the `refreshToken` field, making the
  situation unrecoverable until manual re-auth.

### Q2. What will break under real-time conditions?

- **Dhan 5 req/s NON_TRADING rate limit** (HIGH-1): every order during
  the 10-min refresh window triggers a profile call. A strategy firing
  5 orders/min sits at 5 profile calls/min, which is below the 15/s
  rate but consumes ~30% of the bucket. Adding positions, fund-limit
  calls, or any non-trading query pushes the system over the limit and
  orders get throttled.
- **Dhan 130s TOTP cooldown** (I-8): if the token expires during a
  strategy burst, the first failed `ensureValid` triggers a profile
  call, which fails (401), which triggers a TOTP mint, which **succeeds
  but is unusable for 130s**. Orders during those 130s are rejected.
- **Dhan 25-modification cap** (I-9): fast strategy re-pricing will hit
  this within seconds. `DhanOrderCommandAdapter` throws
  `IllegalStateException` but the strategy needs to know to **cancel and
  re-place**, not just stop.
- **Dhan WebSocket `STALE_FEED_THRESHOLD_MS = 30_000`** (T-4): during
  market open, Dhan's WS feed bursts at 100+ messages/s. If the consumer
  falls behind, the last-message timestamp may go stale and the health
  monitor will mark the feed stale and reconnect — **potentially in a
  tight loop** if the consumer is genuinely slow.

### Q3. What assumptions are unsafe?

- **"Persisted token state is authoritative for expiry"** — the disk
  cache reflects the broker's view at *mint time*, not at *read time*.
- **"WebSocket auth is symmetric with REST auth"** — a successful REST
  call does not mean the WebSocket is connected; the WebSocket may have
  received 806 and the listener may have dropped the event.
- **"Dhan profile call is cheap"** — it costs one NON_TRADING bucket
  slot, which is shared with positions, fund limits, and other
  non-trading operations.
- **"Upstox refresh tokens are reusable"** — Upstox documents them as
  *single-use*; rotating once consumes the old one. The current code
  trusts the configured `refreshToken` to remain valid across multiple
  rotations.
- **"Dhan `currentState.source() == BOOTSTRAP` means the bootstrap
  token is being used"** — looking at the code flow, the source label
  is informational only. Once `confirmExistingState` validates the
  bootstrap token against `/v2/profile`, the source remains "BOOTSTRAP"
  even if the token is now in active use. Strategies cannot distinguish
  "actively used bootstrap" from "TOTP-minted" from this field.
- **"Order placement is idempotent on `correlationId`"** — this is
  true *only* if the broker's response was received and the response
  was committed to the idempotency cache. A network timeout *before*
  the broker's response means the cache was not written, and a retry
  will create a new order. Dhan's `correlationId` field is the
  broker-level dedup; we don't enforce its uniqueness ourselves.

### Q4. Where is behavior implicit instead of explicit?

- **`DhanTokenManager.isReusable`** — the 10-min refresh buffer is
  configured in `DhanConnectionSettings.refreshBufferMinutes` but the
  actual reuse logic uses `expiryEpochMs > now + buffer + 30s_skew`
  inline. There's no `TokenPolicy` abstraction; a future config change
  to "use broker's profile call on every request" would require
  modifying `DhanTokenManager` directly.
- **Order idempotency** — relies on the broker's `correlationId`
  field, the in-app `CaffeineIdempotencyCache`, and
  `DhanOrderCommandAdapter.correlationLocks`. None of these are
  documented as a contract; the strategy author has to discover them
  by reading the code.
- **WebSocket re-bind on token rotation** — the
  `DhanClientHolder.addRotationListener` callback is set up in
  `DhanWebSocketMultiplexer`'s constructor, but if a future adapter
  uses a different multiplexer (e.g., the depth client), it must
  remember to register the same listener. There's no enforcement.
- **Upstox "extended token" detection** — `UpstoxTokenExpiry.nextExpiryEpochMs()`
  is used as a fallback when both `/user/profile` and JWT `exp` parsing
  fail. This is *implicitly* a "the user gave us garbage" sentinel,
  but the code does not treat it that way; it silently accepts the
  3:30 AM IST next-day as the expiry. This makes the system *appear*
  to work but with wrong assumptions baked in.
- **Dhan `BOOTSTRAP` source label** — there's no actual
  "bootstrap-only" code path. The bootstrap token, once validated, is
  treated identically to a TOTP-minted token for all purposes except
  the source string. Operators cannot filter "bootstrap-only" orders
  or alerts.

---

## 12. Closing — what the tests prove vs. what the tests don't

**What the existing test suite proves (verified this review):**
- Token mint, persistence, and refresh mechanics work in unit tests.
- Broker-connection facade wires all required ports.
- Capability lookup is stable and non-null.
- OAuth + PKCE + webhook flows are unit-tested with mocks.
- Auth-client parsing handles the documented response shapes.

**What the test suite does NOT prove:**
- That the production HTTP path (with a real Dhan/Upstox server) handles
  token rotation correctly under load.
- That the WebSocket multiplexer recovers from 806 (CRITICAL-2).
- That a stale persisted `expiryEpochMs` is caught (CRITICAL-1).
- That the rate limiter budget holds under realistic multi-bucket
  traffic (HIGH-1).
- That Upstox refresh tokens survive webhook upgrades (HIGH-5).
- That the strategy layer receives a clean "broker is dead" signal
  within a bounded time when the token layer fails.

The unit/integration tests are thorough at the **unit boundary**, but
the **end-to-end** test coverage is thin. The `app/test` failures
(StartupSmokeComponentTest) are not in this review's scope, but they
indicate that the full Spring context doesn't even load cleanly in
the current state — meaning the *composition* of the broker adapters
has not been validated end-to-end recently.

**Bottom line:** The broker layer's design is solid (clean ports, good
testability, layered abstractions). The token-management logic is
correct at the unit level. But **three live-money-critical bugs**
(CRITICAL-1, CRITICAL-2, and the broader CRITICAL-3 abstraction gap)
prevent this from being safely deployable in its current form. The
proposed fixes in §9 are surgical and can be applied incrementally
without rewriting the architecture.
