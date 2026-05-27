# Trade-J — Comprehensive Project Analysis

> **Date:** May 26, 2026  
> **Project:** Trade-J — Algorithmic Trading Platform  
> **Stack:** Java 21 / Spring Boot trading engine, with in-repo Python broker code retained as reference material only

---

## 1. Overview

Trade-J is a **Java-based algorithmic trading platform** that integrates with the **Dhan** brokerage API (India markets). The repository also contains a Python `brokers/` implementation that should be treated as **reference material for design comparison and parity checks**, not as a required runtime component of the deployed system.

| Component | Language | Role |
|-----------|----------|------|
| `brokers/` | Python 3.12+ | Reference broker implementation used for comparison, parity ideas, and historical design context |
| `trade-*` (8 subprojects) | Java 21 / Spring Boot | Actual trading engine and broker integration runtime |

The Python `brokers` package is useful as a **reference gateway design** for the Dhan broker, especially for normalization rules, adapter boundaries, and API coverage ideas. The Java side provides the **actual trading infrastructure** (domain models, event bus, execution, strategy, persistence, live runtime) with its own `IBrokerConnection` and Dhan integration. Any wording below that compares Java to Python should be read as **reference comparison**, not as proof that both stacks are intended to run together in production.

---

## 2. Python `brokers` Package — Reference Architecture

This section documents the Python package because it informed several architectural decisions, but it is **not the primary implementation target** for this project.

### 2.1 Directory Structure

```
brokers/
├── gateway.py                 # BrokerGateway facade — unified entry point
├── factory.py                 # BrokerFactory — single creation point
├── config.py                  # BrokerConfig — environment-based configuration
├── domain/
│   ├── models.py              # Core domain models (Quote, Order, Instrument, etc.)
│   └── __init__.py             # Re-exports all domain types
├── ports/
│   ├── connection.py          # IBrokerConnection protocol
│   └── broker_ports.py        # IMarketDataService, IOrderService, IPortfolioService, IWebSocketService
├── dhan/
│   ├── connection.py          # DhanConnection — IBrokerConnection implementation
│   ├── adapters/
│   │   ├── base/
│   │   │   ├── rest_adapter.py       # BaseRestAdapter — shared resolve+segment+price logic
│   │   │   ├── stream_adapter.py     # BaseStreamAdapter — shared async generator pattern
│   │   │   └── resolver_mixin.py     # ResolverMixin — _resolve() / _resolve_and_segment()
│   │   ├── segment_mapper.py         # get_segment_for() — exchange → segment code
│   │   ├── market/data_adapter.py    # LTP, Quote, Depth, OHLC
│   │   ├── historical/data_adapter.py # Historical candlestick data
│   │   ├── orders/
│   │   │   ├── adapter.py            # OrderAdapter — facade over command + query
│   │   │   ├── command_adapter.py    # Order placement, modification, cancellation
│   │   │   ├── query_adapter.py      # Order status, trade book, enriched details
│   │   │   ├── super_adapter.py      # Bracket orders (OCO/BO)
│   │   │   ├── forever_adapter.py    # GTT trigger orders
│   │   │   ├── shared.py             # Shared order validation
│   │   │   └── idempotency.py        # Idempotency cache (in-memory / Redis)
│   │   ├── portfolio/adapter.py      # Positions, holdings, balance
│   │   ├── futures/adapter.py        # Futures contract resolution
│   │   ├── options/adapter.py        # Option chain/expiry/price discovery
│   │   ├── margin/adapter.py         # Margin calculation
│   │   ├── alerts/adapter.py         # Conditional triggers/alerts
│   │   ├── instruments/
│   │   │   ├── resolver.py           # SymbolResolver — O(1) instrument lookup
│   │   │   └── loader.py             # InstrumentLoader — CSV + daily caching
│   │   └── websocket/
│   │       ├── adapter.py            # Main WebSocket adapter (market feed)
│   │       ├── multiplexer.py        # WebSocketMultiplexer — multi-shard management
│   │       ├── binary_parser.py      # Dhan binary protocol decoder
│   │       ├── depth20.py            # 20-level depth WebSocket
│   │       ├── depth200.py           # 200-level depth WebSocket
│   │       └── base_adapter.py       # Shared WS adapter logic
│   ├── normalization/
│   │   ├── symbol.py                 # CentralizedNormalizationEngine
│   │   ├── dtos.py                   # DhanDTOParser — status/segment reversal
│   │   └── errors.py                 # handle_errors decorator
│   ├── config/endpoints.py           # DhanEndpoints, TimeoutConfig, ResilienceConfig, etc.
│   ├── constants/
│   │   ├── segments.py               # Exchange↔Segment mapping
│   │   └── protocol.py               # Binary protocol constants (FeedCode, PacketSize, etc.)
│   ├── ports/
│   │   ├── order_port.py             # IOrderCommand / IOrderQuery (CQRS)
│   │   ├── market_data_port.py       # IMarketDataProvider
│   │   ├── instrument_port.py        # IInstrumentResolver
│   │   └── portfolio_port.py         # IPortfolioProvider
│   ├── exceptions/__init__.py        # Rich exception hierarchy (16 exception types)
│   ├── infrastructure/
│   │   ├── http/
│   │   │   ├── client.py             # DhanAsyncClient — HTTP client with retry + circuit breaker
│   │   │   └── interceptor.py        # BrokerHTTPInterceptor — retry + CB (extracted for testability)
│   │   ├── rate_limiter.py           # MultiBucketRateLimiter — per-category token bucket
│   │   ├── metrics.py                # MetricsRegistry — Prometheus-style counters
│   │   └── websocket/slot_pool.py    # WebSocket shard slot management
│   └── utils/decimal.py              # safe_decimal helper
├── scripts/
│   ├── check_dhan_health.py          # Health check for Dhan connection
│   ├── run_live_validation.py        # Run all live validation tests
│   ├── run_regression.py             # Regression test runner
│   ├── get_nifty_options.py          # NIFTY option chain fetcher
│   ├── get_crudeoil_mcx.py           # MCX CRUDEOIL fetcher
│   ├── option_scanner.py             # NSE index option scanner
│   ├── mcx_scanner.py                # MCX commodity scanner
│   └── base_scanner.py               # Base scanner class
├── tests/
│   ├── unit/brokers/dhan/            # ~25+ test files, 407+ unit tests
│   ├── integration/brokers/dhan/     # Live smoke tests
│   ├── conftest.py                   # Shared test fixtures
│   └── fakes/                        # Fake implementations for testing
└── data/instruments/                 # Cached instrument master CSVs
```

### 2.2 Key Design Patterns

#### 2.2.1 Facade Pattern — `BrokerGateway`

```python
gw = BrokerGateway(connection)
ltp = await gw.get_ltp("RELIANCE", "NSE")
order = await gw.place_order(symbol="RELIANCE", ...)
```

The `BrokerGateway` class is the **single unified entry point** for all broker operations. It delegates to sub-services via the `IBrokerConnection` protocol.

#### 2.2.2 Factory Pattern — `BrokerFactory`

```python
gw = BrokerFactory.create(
    client_id="...",
    access_token="...",
    mode="paper",
    load_instruments=True,
)
```

`BrokerFactory` is the **sole creation point** for `BrokerGateway` instances. It reads from `BrokerConfig` (which in turn loads from `.env` / environment variables) and wires up the full dependency graph.

#### 2.2.3 Port/Adapter (Hexagonal Architecture)

The `ports/` directory defines **abstract interfaces** (protocols and ABCs):
- `IBrokerConnection` — top-level connection contract
- `IOrderCommand` / `IOrderQuery` — CQRS separation for orders
- `IMarketDataProvider` — market data retrieval
- `IPortfolioProvider` — portfolio data
- `IInstrumentResolver` — instrument resolution
- `IWebSocketService` — streaming data

Concrete Dhan implementations live in `dhan/adapters/`.

#### 2.2.4 CQRS — Command/Query Separation

Orders are split into **command** (`place`, `modify`, `cancel`) and **query** (`get_order`, `get_orderbook`, `get_trade_book`) interfaces. This enables:
- Independent scaling of read vs. write paths
- Clearer testing boundaries
- Different rate limits for orders vs. reads

#### 2.2.5 Resilience Patterns

The `DhanAsyncClient` implements three resilience layers:

1. **Retry Policy** (`RetryPolicy`):
   - GET/HEAD/DELETE: retry up to 4 times with exponential backoff + jitter
   - POST/PUT: retry only once on timeout/network error (no double-spend risk)
   - Rate limit (429): always retry with longer backoff

2. **Circuit Breaker** (`CircuitBreaker`):
   - 3 states: CLOSED → OPEN → HALF_OPEN → CLOSED
   - OPEN state immediately rejects requests (fail-fast)
   - HALF_OPEN allows probe requests after recovery timeout

3. **Rate Limiter** (`MultiBucketRateLimiter`):
   - Per-category token buckets (order, data, quote, non-trading, option_chain, historical)
   - 70% of Dhan's official limits as safety buffer
   - Async-aware with sleep-wait for token replenishment

### 2.3 Domain Models

All key trading entities are defined as **immutable dataclasses** in `domain/models.py`:

| Model | Fields | Notes |
|-------|--------|-------|
| `Quote` | symbol, ltp, open, high, low, close, volume, change, timestamp | Immutable, IST timezone |
| `MarketDepth` | symbol, bids (5), asks (5), timestamp | 5-level order book |
| `Depth20Snapshot` | symbol, side, levels (up to 20), exchange_segment | From depth20 WebSocket feed |
| `Candle` | symbol, timeframe, OHLC, volume, timestamp, open_interest | Historical data unit |
| `Instrument` | symbol, exchange, security_id, instrument_type, lot_size, tick_size, option fields | Central lookup object |
| `Order` | order_id, symbol, exchange, side, quantity, order_type, price, trigger, status | Lifecycle tracking |
| `Position` | symbol, exchange, quantity, avg_price, pnl, last_price, side | Current holdings |
| `Trade` | trade_id, order_id, symbol, exchange, side, quantity, price, timestamp | Executed fills |
| `Balance` | available_balance, sod_limit, collateral, utilized, withdrawable | Account funds |
| `SuperOrder` | order_id + entry/target/stop legs, trailing_jump | Bracket/OCO orders |

Enums: `Exchange`, `InstrumentType`, `OptionType`, `OrderSide`, `OrderType`, `OrderStatus`

### 2.4 Symbol Normalization Engine

`CentralizedNormalizationEngine` in `dhan/normalization/symbol.py` provides the **reference normalization model** that the Java implementation can compare against for parsing and formatting option/futures symbols across format boundaries:

- **Canonical format:** `NIFTY 25 DEC 22000 CALL` (spaced, CALL/PUT)
- **Dhan CSV format:** `NIFTY25DEC25000CE` (compact, CE/PE)
- **Spaced CE format:** `NIFTY 25 DEC 25000 CE`
- **Hyphenated:** `nifty-25-dec-22000-call`

**Patterns matched:**
- `SPACED_OPTION_PATTERN` — e.g., `NIFTY 25 DEC 22000 CALL`
- `COMPACT_OPTION_PATTERN` — e.g., `NIFTY24MAY22000CE`
- `SPACED_FUTURE_PATTERN` — e.g., `NIFTY 29 MAY FUT`
- `COMPACT_FUTURE_PATTERN` — e.g., `NIFTY24MAYFUT`

### 2.5 Exception Hierarchy

16 exception types in a clean hierarchy:

```
DhanError (base)
├── AuthenticationError        — Invalid/token expired
├── RateLimitError             — HTTP 429
├── NetworkError               — Connection/transport
├── TimeoutError               — Request timeout
├── OrderError (base)
│   ├── OrderRejectedError
│   │   └── InsufficientFundsError
│   └── OrderNotFound
├── InstrumentError (base)
│   ├── InstrumentNotFoundError
│   └── InvalidSymbolError
├── MarketDataError
├── MarketClosedError
├── PositionError
├── MarginError
├── AlertError
└── ConfigurationError         — Missing credentials, etc.
```

### 2.6 Binary Protocol Constants

`dhan/constants/protocol.py` consolidates all **magic numbers** from Dhan's binary WebSocket protocol into typed enums:

| Enum | Purpose |
|------|---------|
| `FeedCode` | Message types (TICKER=2, DEPTH=3, QUOTE=4, OI=5, FULL=8, etc.) |
| `DisconnectReason` | Disconnect codes (MAX_CONNECTIONS=805, INVALID_TOKEN=806, etc.) |
| `Depth20Code` | Depth feed codes (BID=41, ASK=51) |
| `RequestCode` | Subscription request codes (SUBSCRIBE_TICK=15, etc.) |
| `PacketSize` | Per-message byte payload sizes |
| `Depth20Header` | Header/level sizes for depth20 protocol |
| `Depth200Header` | Header/level sizes for depth200 protocol |
| `MarketFeedMode` | Subscription mode codes |

### 2.7 Segment Mapping

`dhan/constants/segments.py` provides bidirectional mappings between **canonical exchange names** and **Dhan segment codes**:

| Exchange | Dhan Segment | Numeric Code |
|----------|-------------|--------------|
| NSE | NSE_EQ | 1 |
| BSE | BSE_EQ | 4 |
| NFO | NSE_FNO | 2 |
| BFO | BSE_FNO | 8 |
| MCX | MCX_COMM | 5 |
| CDS | NSE_CURRENCY | 3 |
| INDEX | IDX_I | 0 |

### 2.8 Infrastructure Configuration

All endpoints, timeouts, and resilience settings are centralized in `config/endpoints.py`:

```python
DhanEndpoints.REST_BASE         # "https://api.dhan.co/v2/"
DhanEndpoints.WS_FEED           # "wss://api-feed.dhan.co"
DhanEndpoints.WS_DEPTH20        # "wss://depth-api-feed.dhan.co/twentydepth"
DhanEndpoints.INSTRUMENT_CSV    # "https://images.dhan.co/api-data/api-scrip-master.csv"

TimeoutConfig.HTTP_TOTAL              # 30s
TimeoutConfig.WS_RECONNECT_MAX_ATTEMPTS  # 10
TimeoutConfig.WS_RECONNECT_BASE       # 1s (exponential backoff)

ResilienceConfig.RETRY_MAX_ATTEMPTS    # 4
ResilienceConfig.CIRCUIT_RECOVERY_TIMEOUT  # 30s
ResilienceConfig.CIRCUIT_FAILURE_THRESHOLD  # 5

RateLimitConfig.ORDER_RATE       # 7/s (70% of official 10/s)
RateLimitConfig.DATA_RATE        # 3.5/s
RateLimitConfig.QUOTE_RATE       # 0.7/s
RateLimitConfig.NON_TRADING_RATE # 15/s

IdempotencyConfig.REDIS_TTL_SECONDS  # 86,400 (24h)
```

### 2.9 Test Coverage

- **407 unit tests** passing (fast, no credentials required)
- **14+ integration tests** (live API, manually run with credentials)
- **5 smoke tests** for quick sanity checks
- **3 scripts** for live validation, health checks, and regression
- Tests organized by adapter (test_market_data.py, test_orders.py, test_historical.py, etc.)

Test markers configured in `pytest.ini`:
- `unit` — Fast, no dependencies
- `integration` — Live Dhan API (requires credentials)
- `smoke` — Quick sanity checks
- `regression` — Full simulation
- `asyncio` — Async tests

---

## 3. Java Side — Architecture

### 3.1 Module Structure (Gradle Multi-Project)

```
settings.gradle
├── trade-core          — Domain models, value objects, event bus
├── trade-broker-api    — Broker API interfaces / ports
├── trade-broker-dhan   — Dhan broker implementation (uses Dhan SDK)
├── trade-strategy      — Strategy framework & indicators
├── trade-execution     — Order management & execution services
├── trade-disruptor     — Disruptor-based event pipeline
├── trade-persistence   — Data persistence (Chronicle Queue, DuckDB)
└── trade-app           — Spring Boot application entry point
```

### 3.2 Technology Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 21 | Language (toolchain) |
| Spring Boot | 3.4.13 | Application framework |
| Gradle | 8.x | Build system |
| JUnit 5 | 5.12.2 | Testing framework |
| Disruptor | 4.0.0 | High-performance inter-thread messaging |
| Caffeine | 3.2.4 | In-memory caching |
| Chronicle Queue | 2026.2 | Persisted low-latency queue |
| DuckDB | 1.5.3.0 | Embedded analytical database |
| Dhan SDK | 2.2.0 | Official Dhan Java SDK |

### 3.3 Domain Models

Core domain models live in `trade-core`:

```java
// trade-core/.../Order.java
public record Order(
    String orderId,
    String correlationId,
    String symbol,
    ExchangeSegment exchangeSegment,
    Side side,
    ProductType productType,
    OrderType orderType,
    OrderStatus status,
    long quantity,
    long filledQuantity,
    long pricePaisa,          // Paise-based pricing (no floating point)
    long triggerPricePaisa,
    long exchangeTimeMs,
    String rejectionReason
) {}

// trade-core/.../Instrument.java
public record Instrument(
    String symbol,
    String canonicalSymbol,
    Exchange exchange,
    ExchangeSegment exchangeSegment,
    String instrumentType,
    String underlying,
    LocalDate expiry,
    Long strikePricePaisa,
    OptionType optionType,
    long lotSize,
    long tickSizePaisa
) {
    public InstrumentKey key() { ... }
    public boolean isOption() { ... }
    public boolean isFuture() { ... }
}
```

Note the use of **paise-based integers** for all monetary values — no floating-point arithmetic in the core engine.

### 3.4 Architecture Pattern

The Java side adopts a similar port/adapter pattern, but it should be treated as the primary implementation rather than a mirror that depends on the Python runtime:

```
IBrokerConnection (trade-broker-api)
    └── DhanBrokerConnection (trade-broker-dhan)
            └── Uses Dhan SDK (io.github.sonicalgo.dhan)
```

#### 3.4.1 Config Factory — `DhanConnectionSettings.withDefaults()`

Analogous to Python's `BrokerConfig.from_env()`, this static factory provides sensible defaults for non-mandatory fields:

```java
DhanConnectionSettings settings = DhanConnectionSettings.withDefaults(
    "client_id",     // Mandatory
    "access_token"   // Mandatory
);
// loggingEnabled=false, rateLimitRetries=3, maxReconnectAttempts=10,
// autoReconnectEnabled=true, autoResubscribeEnabled=true
```

Located in `trade-broker-dhan/src/main/java/.../config/DhanConnectionSettings.java`.

#### 3.4.2 Connection Factory — `DhanBrokerConnection.create()`

Convenience factory that auto-creates a default `MultiBucketRateLimiter`, mirroring Python's `BrokerGateway.from_config()`:

```java
DhanBrokerConnection conn = DhanBrokerConnection.create(
    settings,                            // DhanConnectionSettings
    new CaffeineIdempotencyCache()       // IdempotencyCachePort
);
```

For Spring-managed environments where custom beans are injected (e.g., the `multiBucketRateLimiter` singleton in `TradingRuntimeConfiguration`), the full constructor should be used instead. The `create()` factory is primarily intended for test setup and standalone usage.

Located in `trade-broker-dhan/src/main/java/.../DhanBrokerConnection.java`.

Key ports defined in `trade-broker-api`:
- `IBrokerConnection` — Top-level broker contract
- `MarketDataProvider` — Market data
- `OrderCommand` / `OrderQuery` — CQRS for orders
- `PortfolioProvider` — Portfolio
- `InstrumentResolver` — Instrument resolution
- `FuturesProvider` — Futures
- `OptionsProvider` — Options
- `WebSocketMultiplexer` — Streaming data

### 3.5 Testing Pyramid (Gradle)

The `build.gradle` defines the active Java test pyramid:

| Task | Tags | Purpose |
|------|------|---------|
| `test` | (excludes integration) | All unit tests |
| `unitTest` | `unit` | Fast, deterministic |
| `componentTest` | `component` | Module composition, no mocks |
| `integrationTest` | `integration` | Live Dhan connectivity |
| `brokerRestTest` | `broker-rest` | Live broker REST endpoints |
| `brokerWsTest` | `broker-ws` | Live broker WebSocket |
| `brokerOrderTest` | `broker-order` | Sandbox order lifecycle (REST) |
| `regressionPreflightTest` | `regression-preflight` | Live + sandbox credential preflight |
| `crossLayerRegressionTest` | `cross-layer` | OMS + execution + sandbox broker |
| `fullRegressionTest` | (root) | Full-stack regression gate |
| `runtimeE2eTest` | `runtime-e2e` | Full end-to-end |

Live testing is driven by Java-side configuration (`DHAN_*` variables or local config properties such as `config/dhan-local.properties`).

---

## 4. TDD Implementation Plan (TDD_PLAN.md)

The repository includes a **comprehensive 8-phase TDD plan** that is useful as guidance, but it should not be read as proof that the Python reference layer must be integrated into the Java runtime:

```
Phase 0: Prerequisites ✅ — 407 unit tests passing, venv verified
Phase 1: Pattern Unification ✅ — DhanConnectionSettings.withDefaults() + DhanBrokerConnection.create() factories aligned
Phase 2: Live Validation Tests 📋 — 14 live tests against real API
Phase 3: Historical Data Completeness 📋 — 30+ instrument type tests
Phase 4: Order Lifecycle Completeness 📋 — 20 tests, all order types
Phase 5: WebSocket & Real-Time Data 📋 — 15 tests, depth20/200
Phase 6: Portfolio & Risk Management 📋 — 12 tests, margin checks
Phase 7: Backend Integration & E2E 📋 — 10 backend integration tests
Phase 8: Regression & Production Readiness 📋 — 450+ tests, p99 < 200ms
```

**Key principles:**
- RED → GREEN → REFACTOR cycle for every feature
- Vertical slices (complete one instrument type before moving to the next)
- Never skip the RED phase
- Integration tests run manually (credential-gated)
- Production ready = 450+ tests + backend E2E + p99 latency < 200ms

---

## 5. Cross-Cutting Concerns

### 5.1 Resilience

| Layer | Python reference (`brokers`) | Java implementation (`trade-*`) |
|-------|-------------------|-------------------|
| Retry | `RetryPolicy` — exponential backoff + jitter | `DhanResilienceExecutor` |
| Circuit Breaker | `CircuitBreaker` — 3-state (CLOSED/OPEN/HALF_OPEN) | (via Dhan SDK) |
| Rate Limiting | `MultiBucketRateLimiter` — per-category token bucket | `MultiBucketRateLimiter` (Java) |
| Idempotency | `IdempotencyCachePort` — in-memory/Redis | `IdempotencyCachePort` (Java) |
| Timeout | `TimeoutConfig` — 30s HTTP, WS heartbeat | (via Dhan SDK config) |
| Metrics | `MetricsRegistry` — Prometheus-style counters | (not yet implemented) |

### 5.2 Data Flow

```
User/Strategy
    │
    ▼
IBrokerConnection / Spring runtime (Java)
    │
    ▼
DhanBrokerConnection
    │
    ▼
Dhan SDK + direct HTTP adapters
    │
    ▼
Dhan API (REST + WebSocket)
```

### 5.3 WebSocket Architecture

The active runtime WebSocket path is Java-based. The Python implementation remains useful as a comparison point for missing features and alternative design choices:

- **Python**: `WebSocketMultiplexer` manages up to 5 shards, ~1000 instruments per shard
- **Java**: `DhanWebSocketMultiplexer` manages market feed and order stream connections
- Support for depth20 and depth200 streaming exists in the Python reference package
- A binary protocol parser for Dhan's proprietary binary feed format exists in the Python reference package

### 5.4 Instrument Resolution

Both codebases use **in-memory caches** loaded from Dhan's CSV instrument master, but only the Java resolver is part of the live application:

- **Python**: `SymbolResolver` — O(1) dict-based lookup with multiple key strategies (raw, canonical, stripped, CE/PE alternatives)
- **Java**: `InMemoryInstrumentResolver` — loads from CSV via `DhanInstrumentLoader`
- Daily caching: downloads once per day, caches locally (`data/instruments/`)

---

## 6. Strengths

1. **Clean Hexagonal Architecture** — Port/adapter separation with clear interfaces and dependency inversion
2. **Comprehensive Error Handling** — 16 typed exception classes with proper hierarchy
3. **Production-Grade Resilience** — Retry, circuit breaker, rate limiting, and idempotency all present
4. **Clear Java Runtime Boundary** — The Java codebase now contains the actual trading and broker runtime surface
5. **Excellent Test Coverage** — 407 unit tests, TDD-driven development, 8-phase implementation plan
6. **CQRS for Orders** — Clean separation of command and query responsibilities
7. **Centralized Symbol Normalization** — Robust parsing of multiple symbol formats (compact, spaced, hyphenated, mixed case)
8. **Binary Protocol Handling** — Proper typed enums for Dhan's proprietary WebSocket binary protocol
9. **Config Centralization** — All endpoints, timeouts, and rate limits in single configuration files
10. **Metrics Infrastructure** — Prometheus-style counters ready for production monitoring

---

## 7. Areas for Improvement / Technical Debt

1. **Analysis Scope Confusion** — The repository contains both Java implementation code and Python reference code; documentation must keep those roles separate.
2. **Java Resilience / Broker Edge Cases** — The Java side still needs continued hardening around runtime failures, retries, and broker payload quirks.
3. **Plan Status Drift** — The TDD plan and narrative checkpoints should be validated against the current Java implementation state, not inferred from Python test counts.
4. **Missing Live_Validation_Tests.md** — Referenced in documentation but not present.
5. **Incomplete Instrument Coverage** — Not all instrument types (FUTSTK, OPTSTK, OPTCOM, FUTCOM) have been validated against live API data in the Java runtime.
6. **WebSocket Reconnection** — While basic reconnection exists, full reconnection scenarios (session recovery, missed ticks) still need explicit validation.
7. **No Production Deployment Config** — Docker/Kubernetes configs, health endpoint deployment wiring, and monitoring rollout are not yet visible.
8. **Reference Drift Risk** — If Python remains in-repo as reference only, parity notes can go stale unless explicitly maintained.
9. **Mixed Documentation Voice** — Some sections still speak about Python and Java as peers rather than implementation vs. reference.
10. **Naming / Terminology Consistency** — Terms like "gateway", "runtime", "adapter", and "reference" should be used consistently to avoid architectural confusion.

---

## 8. Key Dependencies

### Python Reference (`pyproject.toml`)

| Package | Purpose |
|---------|---------|
| `aiohttp` | Async HTTP client |
| `pandas` | Historical data processing, CSV loading |
| `python-dotenv` | Environment variable loading |
| `pytest` + `pytest-asyncio` | Testing framework |

### Java Runtime (`build.gradle`)

| Library | Purpose |
|---------|---------|
| Spring Boot 3.4.13 | Application framework |
| Dhan SDK 2.2.0 | Official Dhan brokerage SDK |
| LMAX Disruptor 4.0.0 | Inter-thread event pipeline |
| Caffeine 3.2.4 | In-memory caching |
| Chronicle Queue 2026.2 | Low-latency persistent queue |
| DuckDB 1.5.3.0 | Embedded OLAP database |
| JUnit 5.12.2 | Testing framework |

---

## 9. Quality Metrics

- **Python Unit Tests**: 407 passing ✅ (reference package only, not Java runtime proof)
- **Java Live Test Tasks**: `integrationTest`, `brokerRestTest`, `brokerWsTest`, `brokerOrderTest`, `runtimeE2eTest`
- **Test Types**: unit, component, integration, broker-rest, broker-ws, broker-order, runtime-e2e
- **Exception Types**: 16+ in the Python reference hierarchy; Java uses its own domain/error model
- **Instrument Resolution**: O(1)-style in-memory lookup patterns in both reference and Java implementations
- **Rate Limit Categories**: 6 in the Dhan reference/Java parity model (order, data, quote, non-trading, option_chain, historical)
- **Build Systems**: Java uses Gradle; Python tooling exists for the reference package only
- **Deployment**: Java runtime deployment not yet fully documented

---

## 10. Recommendations for Next Steps

1. **Continue with Phase 2 (Live Validation)** — Run the integration tests against the Dhan API to verify adapters work correctly with real data.
2. **Create Live_Validation_Tests.md** — The referenced file is missing.
3. **Add CI Pipeline** — GitHub Actions or similar for automated Java test runs, with credential-gated live jobs separated clearly.
4. **Continue Java Broker Hardening** — Expand live coverage for quote mapping, WS recovery, and venue-specific flows.
5. **Dockerize the Java Stack** — Create Dockerfiles and deployment assets for the actual runtime.
6. **Monitor Metrics** — Expose Java-side runtime metrics to a monitoring system.
7. **Run Gradle Build** — Compile to verify Phase 1 changes compile cleanly (requires JDK 21).
