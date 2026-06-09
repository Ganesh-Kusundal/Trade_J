# Risk Register
**Generated:** 2026-06-08  
**Severity:** CRITICAL / HIGH / MEDIUM / LOW  
**Likelihood:** CERTAIN / LIKELY / POSSIBLE / UNLIKELY  

| ID | Risk | Broker | Severity | Likelihood | Evidence | Mitigation |
|----|------|--------|----------|------------|----------|------------|
| R-01 | ICICI resubscribe failure on reconnect | ICICI | CRITICAL | CERTAIN | `BreezeWebSocketMultiplexer`: reconnectRegistry not wired; firstConnect path only calls resubscribeAll once | Wire SubscriptionCoordinator to reconnectRegistry; add resubscribe on every EVENT_CONNECT |
| R-02 | ICICI OHLC streaming not implemented | ICICI | CRITICAL | CERTAIN | No WebSocket client connects to LIVE_OHLC_STREAM_URL | Implement BreezeOhlcWebSocketClient or add OHLC fields to handleQuote |
| R-03 | ICICI exchange segment hardcoded to NSE_EQ | ICICI | HIGH | CERTAIN | `ExchangeSegment.NSE_EQ` hardcoded in handleQuote() line 273 | Resolve segment from instrument definition |
| R-04 | Dhan OI frame type 5 silently dropped | Dhan | HIGH | LIKELY | DhanMarketFeedBinaryParser default branch treats type 5 as error | Add FEED_RESPONSE_OI = 5 case to parser |
| R-05 | Upstox parser unverified against real broker output | Upstox | HIGH | LIKELY | Javadoc: "must be verified against real sandbox output" | Capture and validate against live broker fixtures |
| R-06 | Reconnect storm exceeds broker limits | All | HIGH | POSSIBLE | ICICI: no app-level backoff; Dhan: fixed 30s circuit; Upstox: reasonable backoff | Implement unified reconnect manager with exponential backoff across all brokers |
| R-07 | Shared JDK HttpClient thread pool across brokers | All | MEDIUM | LIKELY | `HttpClient.newHttpClient()` singleton shared | Use per-broker HttpClient with named executors |
| R-08 | Dhan reconnectScheduler thread leak | Dhan | MEDIUM | LIKELY | Executor created in constructor, never shutdown in disconnect() | Add shutdown hook in disconnect() |
| R-09 | Dhan depth client lacks heartbeat/reconnect | Dhan | MEDIUM | LIKELY | DhanTwentyDepthWebSocketClient has no health monitor | Add DhanWebSocketHealthMonitor to depth client |
| R-10 | Upstox findKey() linear scan bottleneck | Upstox | MEDIUM | LIKELY | subscriptions.keySet().stream().filter() per frame at 1,000+ symbols | Replace with Map<instrumentToken, MarketSubscriptionRequest> |
| R-11 | ICICI scriptCodes() blocking resolution on subscribe | ICICI | MEDIUM | LIKELY | requireBreezeDefinition called synchronously in emitJoin | Pre-resolve + cache; async emit |
| R-12 | No WebSocket emit rate limiter | All | MEDIUM | LIKELY | subscribe() can burst 5,000 instruments instantly | Add per-broker emit rate limiter |
| R-13 | Slow consumer blocks entire market feed | All | MEDIUM | POSSIBLE | Direct listener dispatch in onBinary/onText with no timeout | Offload to bounded queue with drop-oldest policy |
| R-14 | ICICI session expires at midnight IST without refresh | ICICI | LOW | CERTAIN | BreezeSessionExchange sets expiry to next midnight IST | Add session refresh trigger before midnight |
| R-15 | Upstox subscribe/unsubscribe commands absent | Upstox | MEDIUM | POSSIBLE | No broker subscribe message sent — relies on implicit push | Confirm broker behavior; add explicit subscribe if needed |
| R-16 | No market tick deduplication | All | MEDIUM | LOSSY | No dedup map for market ticks (only for orders) | Add tick-level dedup keyed by (instrument, sequence) |
| R-17 | Dhan index PriceMath.toPaisa NPE risk | Dhan | MEDIUM | POSSIBLE | indexValue() may be null if broker sends malformed frame | Add null guard in normalizeFeedPacket |
| R-18 | ICICI live feeds URL not used | ICICI | LOW | CERTAIN | LIVE_FEEDS_URL ("https://livefeeds.icicidirect.com") defined but unused | Verify if this is intended for orders only |
| R-19 | No schema evolution handling for JSON payloads | ICICI/Upstox (portfolio) | LOW | POSSIBLE | optDouble/optString fallbacks handle missing fields but no version check | Add payload version field if broker supports it |

### Risk Summary by Broker

| Broker | CRITICAL | HIGH | MEDIUM | LOW |
|--------|----------|------|--------|-----|
| Dhan | 0 | 2 | 6 | 0 |
| Upstox | 0 | 2 | 4 | 1 |
| ICICI | 2 | 2 | 4 | 1 |

### Top 5 Risks for Production Hardening
1. **R-01** — ICICI resubscribe: total subscription loss on every Socket.IO reconnect
2. **R-04** — Dhan OI frame loss: option strategies silently missing OI updates
3. **R-05** — Upstox parser unverified: potential mass tick corruption on broker API change
4. **R-02** — ICICI OHLC absent: cannot use ICICI WebSocket for algo trading
5. **R-06** — Reconnect storm: single reconnect event could throttle all three brokers
