# Instrument Naming Contract

## Domain identity

| Field | Meaning |
|-------|---------|
| `canonicalSymbol` | Domain identity used across app, strategies, warehouse, and UI |
| `symbol` | Display symbol; equals `canonicalSymbol` unless a broker exposes a distinct display form |
| Wire identity | Broker-specific code (ICICI ShortName, Dhan securityId, Upstox instrument_key); never required by app code |

## Equity (NSE)

- **External input:** standard NSE trading symbols (`RELIANCE`, `SBIN`, `TCS`)
- **External output:** same standard symbols in `canonicalSymbol`
- **ICICI internal:** Breeze ShortName (`RELIND`) mapped from SecurityMaster; used only in Breeze API payloads

## F&O

- **Canonical format:** `ContractSymbolNormalizer` output (e.g. `NIFTY 30 JUN FUT`, `BANKNIFTY 30 JUN 30000 CALL`)
- **Broker wire:** broker-specific identifiers; resolved via `InstrumentResolver`
- ICICI F&O canonical mapping from SecurityMaster is partial; equity alias resolution is fully supported

## Resolution rules

1. All HTTP and service entry points must resolve symbols via `InstrumentResolver.resolveNormalized(symbol, segment)`
2. Prefer `InstrumentKey.of(symbol, segment)` over `new InstrumentKey(...)` when normalization is required
3. Gateway and WebSocket events must emit `canonicalSymbol` as the stable identity field
4. Broker adapters translate canonical → wire identity at the adapter boundary only

## Broker mapping summary

| Broker | Canonical (EQ) | Wire identity |
|--------|----------------|---------------|
| Dhan | NSE symbol / ContractSymbolNormalizer for F&O | securityId |
| Upstox | trading_symbol | instrument_key |
| ICICI | NSE exchange code from SecurityMaster | breezeStockCode (ShortName) |

## Historical intervals (ICICI)

Breeze `historicalcharts` v1 accepts: `minute`, `5minute`, `30minute`, `day`. Sub-second historical is not exposed on this endpoint; use live tick aggregation for `1s`.
