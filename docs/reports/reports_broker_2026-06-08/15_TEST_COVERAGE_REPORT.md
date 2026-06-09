# Test Coverage Report
**Generated:** 2026-06-08  
**Source:** Test file inventory from codebase

## Test Inventory by Broker

### Dhan Tests

| Test Type | File | Coverage |
|-----------|------|----------|
| Integration | `DhanMarketFeedWebSocketIntegrationTest` | 1 ticker smoke test (TCS, market-hours gated) |
| Integration | `DhanMarketFeedWebSocketFullIntegrationTest` | 1 FULL feed smoke test |
| Integration | `DhanMarketFeedWebSocketQuoteIntegrationTest` | 1 QUOTE feed smoke test |
| Unit | `DhanMarketDataProviderMergeTest` | Candle merge deduplication only |
| **Missing** | `DhanMarketFeedBinaryParser` | **No unit tests** |
| **Missing** | `DhanTwentyDepthBinaryParser` | **No unit tests** |
| **Missing** | `DhanWebSocketMultiplexer` reconnect/resubscribe path | **No unit tests** |
| **Missing** | `DhanWebSocketHealthMonitor` | **No unit tests** |
| **Missing** | `DhanPayloadNormalizer` | **No unit tests** |

### Upstox Tests

| Test Type | File | Coverage |
|-----------|------|----------|
| Integration | `UpstoxMarketFeedIntegrationTest` | 1 SBIN ticker smoke test (market-hours gated, analytics token) |
| Unit | `UpstoxBinaryParserTest` | Tick, Depth, Heartbeat, truncated, short, unknown frame types |
| Unit | `UpstoxWebSocketReconnectTest` | ReconnectManager backoff, storm, reset |
| Unit | `UpstoxWebSocketDedupTest` | Order dedup (isDuplicate, extractOrderId, extractStatus) |
| Unit | `UpstoxFeedAuthorizerTest` | AuthorizedFeed expiry checks |
| **Missing** | `UpstoxStreamNormalizer` | **No unit tests** |
| **Missing** | Broker-validated binary fixtures | **Parser Javadoc warns 'must be verified against real sandbox output'** |
| **Missing** | Full/Diff/Quote frame validation | **No real payload fixtures** |

### ICICI Tests

| Test Type | File | Coverage |
|-----------|------|----------|
| Integration | `IciciMarketFeedIntegrationTest` | 1 RELIANCE smoke test (connects + polls for any event, or asserts non-n) |
| Unit | `BreezeWebSocketReconnectTest` | ReconnectManager backoff, storm, reset |
| Unit | `BreezeWebSocketDedupTest` | Order dedup + extract helpers |
| **Missing** | `BreezeWebSocketMultiplexer` market data | **No unit tests** |
| **Missing** | `handleQuote` parsing | **No unit tests** |
| **Missing** | `BreezeDomainMapper.toQuote/toDepth` | **No tests (REST mapper only)** |
| **Missing** | `BreezeSessionExchange` | **No unit tests** |
| **Missing** | Resubscribe on reconnect | **No test** |

### Core/Shared Tests

| Test Type | File | Coverage |
|-----------|------|----------|
| Unit | `ReconnectManagerTest` | Backoff, storm, success/failure paths |
| Unit | `ReconnectCertificationTest` | Certification-level reconnect scenarios |
| Unit | `SubscriptionCertificationTest` | Subscribe/unsubscribe/resubscribe patterns (CONCURRENT HASHMAP ONLY) |
| Unit | `MultiBucketRateLimiterTest` | Token bucket behavior |
| Unit | `EventBusDepthBridgeTest` | Depth event propagation |
| Unit | `OrderBookEngineTest` | Book updates |
| Unit | `BrokerChaosScenariosTest` | Chaos testing scenarios |

## Coverage Gap Analysis

| Test Category | Dhan | Upstox | ICICI |
|--------------|------|--------|-------|
| Parser unit tests | ❌ None | ⚠️ Synthesized frames only | ❌ None |
| Parser broker-validated fixtures | ❌ | ❌ Explicitly unverified | ❌ |
| WebSocket multiplexer tests | ❌ | ❌ | ❌ |
| Reconnect + resubscribe integration | ❌ | ⚠️ ReconnectManager only | ❌ ReconnectManager only |
| Depth integration | ❌ Present (app tests) | ❌ | ❌ |
| OHLC streaming | ❌ Not needed (via FULL) | ❌ (uses Quote frame) | ❌ Not implemented |
| Resubscribe after reconnect | ❌ Not tested | ❌ Not tested | ❌ FAIL — no re-subscribe |
| Stale feed detection | ❌ Not tested | ❌ Not tested | ❌ Not tested |
| Circuit breaker | ❌ Not tested | ❌ Not applicable | ❌ Not applicable |
| Message throughput / soak | ❌ | ❌ | ❌ |
| Multi-broker isolation | ❌ | ❌ | ❌ |
| Failure recovery | ❌ | ❌ | ❌ |
| Load / stress | ❌ | ❌ | ❌ |

## Test Maturity Matrix

| Broker | Unit Tests | Integration Tests | Production Ready |
|--------|-----------|-------------------|-----------------|
| Dhan | Low (1 trivial unit test) | Medium (3 market hours-gated) | **PARTIAL** |
| Upstox | Medium (5 test files) | Low (1 analytics-gated) | **PARTIAL** |
| ICICI | Low (2 test files) | Low (1 weak assertion) | **NOT READY** |

## Critical Missing Tests

1. **Dhan `DhanMarketFeedBinaryParser`:** OI frame type (5) handling — currently silently errors  
2. **Upstox parser:** Real broker-validated fixture tests — parser is best-effort with documented risk  
3. **ICICI `BreezeWebSocketMultiplexer` resubscribe:** No test exists; code has a confirmed bug  
4. **All brokers: reconnect-then-resubscribe integration test:** No test verifies subscriptions survive a full disconnect/reconnect cycle  
5. **All brokers: backpressure/slow-consumer test:** No test verifies behavior when a listener blocks  
6. **All brokers: memory leak under repeated connect/disconnect:** No soak test
