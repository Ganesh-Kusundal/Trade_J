# Architecture backlog

Extracted from archived reviews (`docs/archive/`). Track fixes as GitHub issues or PRs; IDs match the original review symbols.

## P0 — fix before live capital

| ID | Module | Issue | Status |
|----|--------|-------|--------|
| N-01 | trade-execution | `EventSourcedNetPositionProvider` removes symbol on close — wrong for multi-trade symbols | **Fixed** — `tradeContributions` map; verify via regression |
| E-01 | trade-execution | Execution queue capacity 50 is too small — increase and make configurable | **Fixed** — capacity 1000; verify via `ExecutionHandlerUnitTest` / full regression |
| RP-098 | broker-gateway | `BrokerHandle.invoke("getOptionGreeks", ...)` and `extras().optionGreeks(key)` were missing — `BrokerExtras` had no options method, `invoke` switch had no case, three broker Extras classes did not expose greeks | **Fixed** — added `optionsProvider()` + `optionGreeks(InstrumentKey)` on `BrokerExtras` (defaults), `DhanExtras`/`UpstoxExtras`/`IciciExtras` overrides, plus `"optionGreeks"`/`"getOptionGreeks"` cases in `BrokerHandle.invoke` and each `Extras.invoke`; verify via `BrokerHandleInvokeTest` (14 unit tests) and `BrokerGatewayLiveConnectionTest` (9 live Dhan tests) |
| RP-099 | core / broker-gateway / broker-dhan | Instrument-mapping abstraction leaks: (a) `BrokerHandle.defaultSegment` and `DefaultBrokerInspector` used legacy aliases `BANKNIFTY`/`FINNIFTY`/`MIDCPNIFTY` instead of canonical NSE names; (b) 14 Dhan sites used `exchangeSegment().name()` directly as wire code (DhanOptionChainClient, DhanRollingOptionClient, DhanConditionalAlertProvider, DhanMarginProvider, DhanMarketDataProvider x4, DhanHistoricalDataClient x2, DhanRestOrderClient, DhanOptionsAdapter cache key, DhanMarketFeedWebSocketClient, DhanInstrumentCatalog); (c) `InstrumentResolver.resolveBySecurityId` / `resolvePayload` exposed broker-specific concepts in the public SPI | **Fixed** — created `IndexSymbols` constants class with canonical NSE names + alias map; `BrokerHandle.defaultSegment` + `DefaultBrokerInspector` route through `IndexSymbols.isIndexUnderlying`; `SimulatedMarketDataProvider` + `PaperBrokerConnection` use canonical keys (with `canonicalize()` lookup); added `DhanSegmentMapper.toWireValue(ExchangeSegment)` and routed all 14 Dhan sites through it; created `@BrokerInternal` annotation and tagged the two SPI methods; verify via `IndexSymbolsTest` (14 unit tests), `DhanSegmentMapperTest` (5 unit tests), `BrokerHandleTest` (4 new tests for canonical/alias/contract/stock-option detection) |
| RP-100 | broker-dhan (loader) | After RP-099 canonicalized the inbound `IndexSymbols` surface, the Dhan instrument loader for INDEX rows still set `definition.canonicalSymbol = sem_trading_symbol` verbatim. This meant callers using the canonical NSE name `"NIFTY BANK"` could not resolve through the catalog — only the broker alias `"BANKNIFTY"` worked. The standardization intent (canonical names win on the public surface) was therefore not actually wired into the catalog lookup path. | **Fixed** — `DhanInstrumentLoader` INDEX rows now run `IndexSymbols.canonicalize(symbol)` to derive `canonicalSymbol`; the catalog indexes both `definition.symbol()` (broker alias) and `definition.canonicalSymbol()` (canonical NSE name), so `getBySymbol` resolves either form to the same `securityId`. Verify: `DhanInstrumentLoaderTest.catalogIndexesBothBrokerAliasAndCanonicalName` (5/5 unit tests pass) and 4 new end-to-end live tests in `BrokerGatewayLiveConnectionTest` against real Dhan (13/13 pass): `ltpByCanonicalIndexNameResolvesViaDhanSecurityId`, `ltpByBrokerAliasResolvesToSameDhanSecurityId`, `quoteByCanonicalNiftyIndexResolves`, `ohlcByCanonicalFinNiftyIndexResolves`. Both alias and canonical paths return comparable LTPs (within 1%), proving they reach the same Dhan securityId. |


## P1 — high / medium

| ID | Module | Issue |
|----|--------|-------|
| RP-01 | trade-persistence | Chronicle replay deserializes all events as same type |
| AD-02 | trade-app | Replay does not reset state — mixed live/replay corruption risk |
| PE-02 | trade-strategy | Fragile correlationId coupling between risk handler and portfolio engine |
| ST-02 | trade-app | Broad `DomainEvent` subscriptions — unnecessary dispatch overhead |
| UB-03 | trade-broker-upstox | Unchecked cast to `UpstoxInstrumentResolver` |
| DW-03 | trade-broker-dhan | Order stream drops `OrderPartiallyFilled` |
| GB-01 | trade-gateway | WebSocket bridge broadcasts all ticks to all clients |
| FS-01 | trade-feature-store | JDBC connection check on every event |
| SC-01 | trade-scanner | Batch-only scan — no incremental evaluation |

## P2 — lower priority

| ID | Module | Issue |
|----|--------|-------|
| ME-01 | trade-simulation | No slippage model |
| ME-02 | trade-simulation | No partial fills / queue position |
| UB-01 | trade-broker-upstox | Unsupported port sentinels untested |
| GC-01 | trade-hotpath | High `CandleDeveloping` allocation rate |
| ST-01 | trade-app | Large startup ApplicationRunner / orchestrator (partially extracted) |

See archived [CODE_LEVEL_REVIEW.md](archive/CODE_LEVEL_REVIEW.md) for full context.

See [CODE_EXTRACTION.md](CODE_EXTRACTION.md) for post-move code placement follow-ups.

See [ARCHITECTURE_REPORT.md](ARCHITECTURE_REPORT.md) for flows, test pyramid, and module map.
