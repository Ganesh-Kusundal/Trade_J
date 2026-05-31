# Contract naming (single source of truth)

All options and futures symbols are normalized through **`ContractSymbolNormalizer`** in `trade-core`:

| Kind | Canonical format | Example |
|------|------------------|---------|
| Option | `{UNDERLYING} {dd} {MMM} {STRIKE} CALL\|PUT` | `BANKNIFTY 30 JUN 30000 CALL` |
| Future | `{UNDERLYING} {dd} {MMM} FUT` | `NIFTY 30 JUN FUT` |
| Equity / index | Uppercase symbol | `SBIN`, `NIFTY` |

## Accepted inputs (aliases)

- Spaced CE/PE: `NIFTY 26 MAY 30750 CE`
- Compact: `NIFTY26MAY30750CE`
- Broker trading symbol: `BANKNIFTY-Jun2026-30000-CE` (via catalog resolve)

## Where canonical is enforced

| Layer | Behavior |
|-------|----------|
| Dhan catalog load | `canonicalSymbol` built at ingest |
| Broker ticks/orders/positions | `canonicalSymbol` on domain models |
| OMS `PositionRiskHandler` | `OrderRequest.symbol` = `instrument.canonicalSymbol()` |
| `OrderManagementService` | Normalizes symbol before SIM or live place |
| `InstrumentKey.of(symbol, segment)` | Normalizes key before resolve |
| CLI market commands | `InstrumentKey.of(...)` before quotes |

## Rolling option series (historical warehouse)

Internal series key — **not** a tradable contract symbol:

| Field | Type | Example |
|-------|------|---------|
| `underlying` | `ContractSymbolNormalizer.normalize` | `NIFTY` |
| `expiry` | `RollingExpiryRoll(WEEK\|MONTH, code 1..3)` | `WEEK:1` |
| `strikeOffset` | int -10..+10 (0 = ATM) | `0`, `+3`, `-2` |
| `optionType` | `OptionType.CALL` \| `OptionType.PUT` | `CALL` |
| `intervalMinutes` | 1 \| 5 \| 15 \| 25 \| 60 | `5` |

Canonical type: `RollingOptionSeriesKey` in `trade-core`.

Broker resolution (Dhan only today) happens at the adapter boundary via `DhanRollingOptionWireMapper`:

| Canonical | Dhan wire |
|-----------|-----------|
| `StrikeOffset(0)` | `"ATM"` |
| `StrikeOffset(+3)` | `"ATM+3"` |
| `OptionType.CALL` | `drvOptionType: "CALL"`, response side `ce` |
| `RollingExpiryRoll(WEEK, 1)` | `expiryFlag: "WEEK", expiryCode: 1` |

Nothing above `broker-*` may contain `ATM`, `ATM+1`, `ce`/`pe`, or Dhan `expiryFlag` strings.

## Expired option contracts (Upstox Plus)

Contract-native identity for **expired** options — distinct from rolling-offset series keys:

| Field | Type | Example |
|-------|------|---------|
| `underlying` | `ContractSymbolNormalizer.normalize` | `NIFTY` |
| `segment` | `ExchangeSegment` | `IDX_I` |
| `expiry` | `LocalDate` | `2024-11-27` |
| `strikePaisa` | long | `2250000` (₹22500) |
| `optionType` | `OptionType.CALL` \| `OptionType.PUT` | `CALL` |
| `brokerInstrumentKey` | Upstox wire id | `NSE_FO\|47983\|17-04-2025` |

Canonical types: `ExpiredOptionContractKey`, `ExpiredOptionBar` in `trade-core`.

Upstox resolves domain symbols to wire keys via the instrument catalog (`NIFTY` → `NSE_INDEX|Nifty 50`). Use `/api/v1/market/expired-options/*` (Spring API) — not rolling `RollingOptionSeriesKey` semantics.

Dhan rolling expired history (`RollingOptionHistoryRequest`) remains Dhan-only; Upstox `getExpiredOptionHistory` throws `UnsupportedOperationException`.

## API

```java
ContractSymbolNormalizer.normalize("NIFTY 26 MAY 30750 CE");
ContractSymbolNormalizer.canonicalOption("BANKNIFTY", expiry, strikePaisa, OptionType.CALL);
InstrumentKey.of("NIFTY26MAY30750CE", ExchangeSegment.NSE_FNO);
instrumentResolver.toCanonicalSymbol(symbol, segment);
```
