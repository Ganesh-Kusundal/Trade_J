# Capability Matrix
**Generated:** 2026-06-08  
**Source:** Actual Java implementation analysis

## Broker-by-Broker Capability Matrix

| Capability | Dhan | Upstox | ICICI |
|------------|------|--------|-------|
| LTP | PASS | PASS | PARTIAL |
| Quote | PASS | PASS | FAIL |
| Full Quote (OHLC+Depth) | PASS | PASS | FAIL |
| OHLC Streaming | PASS | PASS | FAIL |
| Volume | PASS | PASS | PARTIAL |
| Open Interest | PARTIAL | PASS | FAIL |
| 5-Level Depth | PASS | PASS | FAIL |
| 20-Level Depth | PASS | PARTIAL | FAIL |
| 200-Level Depth | FAIL | PARTIAL | FAIL |
| Equity Feed | PASS | PASS | FAIL |
| Futures Feed | PASS | PASS | FAIL |
| Options Feed | PASS | PASS | FAIL |
| Index Feed | PASS | PASS | FAIL |
| Subscribe | PASS | PARTIAL | PARTIAL |
| Unsubscribe | PASS | PARTIAL | PARTIAL |
| Dynamic Add | PASS | PARTIAL | PARTIAL |
| Dynamic Remove | PASS | PARTIAL | PARTIAL |
| Bulk Subscribe | PASS | PARTIAL | PARTIAL |
| Bulk Unsubscribe | PASS | PARTIAL | PARTIAL |
| Reconnect | PASS | PASS | PARTIAL |
| Resubscribe on Reconnect | PASS | PASS | FAIL |
| Rotation Recovery | PASS | PARTIAL | PARTIAL |
| Circuit Breaker | PASS | PASS | FAIL |
| Rate Limit Protection (REST) | PASS | PASS | PASS |
| Rate Limit Protection (WS) | FAIL | FAIL | FAIL |
| 100 Symbol Sustain | PASS | PARTIAL | PARTIAL |
| 500 Symbol Sustain | PASS | PARTIAL | PARTIAL |
| 1,000 Symbol Sustain | PASS | PARTIAL | PARTIAL |
| 2,500 Symbol Sustain | PASS | PARTIAL | FAIL |
| 5,000 Symbol Sustain | PASS | FAIL | FAIL |

## Legend

- **PASS** — Implemented and verified in code (unit/integration tests or explicit production logic)
- **PARTIAL** — Partially implemented or bounded by unverified assumptions
- **FAIL** — Not implemented, incorrectly implemented, or blocking bug found
