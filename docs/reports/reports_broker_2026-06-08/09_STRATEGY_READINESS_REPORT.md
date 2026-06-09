# Strategy Readiness Report
**Generated:** 2026-06-08  
**Source:** Code analysis of market data WebSocket events and domain model mapping

## Strategy Data Completeness

### Indicators Requiring Tick Data

| Indicator | Required Fields | Dhan | Upstox | ICICI |
|-----------|----------------|------|--------|-------|
| RSI | Close, timestamp | PASS | PASS | FAIL* |
| EMA | Close, timestamp | PASS | PASS | FAIL* |
| SMA | Close, timestamp | PASS | PASS | FAIL* |
| VWAP | Price×Volume, Volume, timestamp | PASS | PASS | FAIL* |
| ATR | High, Low, Close, timestamp | PASS | PASS | FAIL* |
| MACD | Close (EMA calc), timestamp | PASS | PASS | FAIL* |
| OBV | Volume, Close direction, timestamp | PASS | PASS | FAIL* |
| SuperTrend | Close, High, Low, ATR, timestamp | PASS | PASS | FAIL* |

*ICICI WebSocket only delivers LTP + ltq + ttq. OHLC must come from REST polls.

### Option Analytics

| Feature | Required | Dhan | Upstox | ICICI |
|---------|----------|------|--------|-------|
| PCR (Put-Call Ratio) | OI data per strike | PARTIAL | PASS | FAIL |
| OI Analysis | Open Interest per instrument | PARTIAL | PASS | FAIL |
| Max Pain | Full option chain OI | PARTIAL | PASS | FAIL |

- **Dhan**: OI available in `FEED_RESPONSE_OI=5` frames BUT `DhanMarketFeedBinaryParser` does NOT handle type 5. OI data is silently lost unless obtained via REST.
- **Upstox**: OI extracted in `parseOi()` and in `parseQuote()` / `parseFull()` — has per-frame OI
- **ICICI**: No OI in WebSocket feed

### Volume Analytics
| Feature | Dhan | Upstox | ICICI |
|---------|------|--------|-------|
| Cumulative Volume | PASS | PASS | PARTIAL |
| Volume Profile | PASS | PASS | FAIL |

### Timestamp Consistency

| Broker | Exchange Timestamp | Feed Timestamp | Assessment |
|--------|--------------------|----------------|------------|
| Dhan | `ltt` from binary (epoch seconds or µs via `toEpochMs()`) | `System.currentTimeMillis()` in normalizer Index path | PASS — explicit conversion |
| Upstox | `exchangeTimestampMs` from parser | `System.currentTimeMillis()` in normalizer | PASS — broker timestamp is authoritative |
| ICICI | `System.currentTimeMillis()` | `System.currentTimeMillis()` | PARTIAL — no exchange timestamp in WebSocket payload |

### Latency Suitability

| Broker | Latency Profile | Suitable for Algo Trading |
|--------|----------------|--------------------------|
| Dhan | Binary protocol, native JDK WebSocket, <5ms typical | YES |
| Upstox | Binary protocol, authorize-then-connect pattern adds ~200ms per reconnect | YES (after first connect) |
| ICICI | Socket.IO JSON, ~20-50ms overhead, re-subscribe failure on reconnect | NO for ICICI WebSocket |

### Aggregation Suitability

| Candle Interval | Dhan | Upstox | ICICI |
|-----------------|------|--------|-------|
| 1 Second | PASS | PASS | PARTIAL |
| 5 Second | PASS | PASS | PARTIAL |
| 15 Second | PASS | PASS | PARTIAL |
| 1 Minute | PASS | PASS | PASS |
| 3+ Minute | PASS | PASS | PASS |

- Dhan and Upstox deliver exchange timestamps — candle boundaries align to exchange time
- ICICI uses `System.currentTimeMillis()` — aligned to wall clock, drift possible

## Verdict Summary

### Can Dhan reliably support RSI/VWAP/MACD/OBV/SuperTrend?
**PASS** — All required fields delivered with exchange timestamps. OI for option strategies requires REST fallback or patching `DhanMarketFeedBinaryParser` to handle type 5.

### Can Upstox V3 feed support the same?
**PARTIAL** — Fields are present in parser but: (1) parser unverified against real broker output, (2) `findKey()` linear scan degrades at scale, (3) explicit subscribe/unsubscribe commands absent — confirm broker uses implicit push model.

### Can ICICI OHLC streaming be used directly for strategy execution?
**FAIL** — OHLC is NOT available via WebSocket. Must poll REST. ICICI WebSocket suitable for LTP monitoring only.
