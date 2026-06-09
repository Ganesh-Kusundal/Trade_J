# Observability Report
**Generated:** 2026-06-08  
**Source:** Code analysis of metrics, events, and logging

## Metrics Implemented

### REST Layer (ObservableMarketDataProvider)
| Metric | Name Pattern | Present |
|--------|-------------|---------|
| Call count per method | `{prefix}.marketdata.calls{method=...}` | ✅ |
| Latency per method | `{prefix}.marketdata.latency{method=...}` | ✅ |

### WebSocket Events Published

| Event | Dhan | Upstox | ICICI |
|-------|------|--------|-------|
| StreamHealthChanged (CONNECTED) | ✅ | ✅ | ✅ (via EVENT_CONNECT log) |
| StreamHealthChanged (DISCONNECTED) | ✅ | ✅ | ❌ |
| StreamHealthChanged (STALE) | ✅ | ✅ | ❌ |
| StreamHealthChanged (CIRCUIT_OPEN) | ✅ | ❌ | ❌ |
| BrokerAdapterError | ✅ | ✅ | ✅ |
| OrderAccepted/Filled/Rejected etc. | ✅ | ✅ | ✅ |

## Missing Metrics by Broker

### Dhan
| Missing Metric | Impact |
|----------------|--------|
| Active WebSocket connection count | Cannot monitor connection uptime |
| Reconnect count | Cannot track reconnect frequency |
| Subscription count | Cannot verify subscription health |
| Message throughput (ticks/sec) | Cannot benchmark feed rate |
| Dropped messages | No explicit counter |
| Parse errors | Handled via errorHandler → BrokerAdapterError, but not counted |
| Queue depth | No internal queue to measure |
| Consumer lag | No timestamp delta measured |

### Upstox
| Missing Metric | Impact |
|----------------|--------|
| Active WebSocket connection count | Same as Dhan |
| Feed authorization expiry count | `AuthorizedFeed.isExpired()` not monitored |
| Reconnect count | `reconnectManager.attempts()` not exported |
| Subscription count | `subscriptions.size()` not exported |
| Message throughput | Not measured |
| Parse errors (UpstoxParserException) | Caught and skipped silently — no metric |
| Sequence ID collisions | No detection |

### ICICI
| Missing Metric | Impact |
|----------------|--------|
| Active Socket.IO connection | `quoteSocket.connected()` check only |
| Reconnect count | Not tracked |
| Resubscribe failures | Not tracked |
| Subscription count at broker | Not tracked |
| Emit failures (`join`/`leave`) | Not tracked |
| Message parsing errors | Logged at DEBUG only |

## Health Event Coverage

| Event | Dhan | Upstox | ICICI |
|-------|------|--------|-------|
| CONNECTED | ✅ (`StreamHealthChanged`) | ✅ (`StreamHealthChanged`) | ✅ (log only) |
| DISCONNECTED | ✅ | ✅ | ❌ |
| STALE | ✅ (30s threshold) | ✅ (30s threshold) | ❌ |
| ERROR | ✅ | ✅ | ❌ |
| RECONNECTING | ❌ | ✅ (`ConnectionState.RECONNECTING`) | ❌ |
| CIRCUIT_OPEN | ✅ | ❌ | ❌ |

## Feed Freshness Monitoring

| Broker | Mechanism | Interval | Threshold |
|--------|-----------|----------|-----------|
| Dhan | `DhanWebSocketHealthMonitor` | 5s | 30s → STALE |
| Upstox | `DefaultWebSocketSupervisor.checkStaleness()` | 5s | 30s → STALE |
| ICICI | None | N/A | N/A |

ICICI has NO staleness detection. A silent Socket.IO disconnect that doesn't trigger `EVENT_DISCONNECT` would go undetected indefinitely.

## Verdict: PARTIAL

Dhan and Upstox have good health event coverage with staleness detection. ICICI lacks:
1. Disconnect/error health events
2. Staleness detection
3. Any `StreamHealthChanged` publication for market feed
4. Metrics on WebSocket message rate

All brokers lack operational metrics for:
- Reconnect counts/rates
- Subscription counts
- Message throughput
- Consumer lag
- Parse error rates
