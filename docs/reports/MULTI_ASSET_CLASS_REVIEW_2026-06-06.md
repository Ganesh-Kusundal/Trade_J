# P2 Multi-Asset-Class Future-Proofing Review
**Date:** 2026-06-06
**Scope:** Hidden coupling, untestable complexity, and "soft" asset-class boundaries in the domain model, the simulation layer, and the order validation. Evaluated against the Indian market reality (Equity NSE/BSE, F&O NSE/BSE, Currency NSE/BSE, Commodity MCX) and against a future multi-region / multi-broker expansion (US, EU, APAC ex-India).
**Author persona:** Principal quant engineer — opinionated, file:line specific, brutally honest.

---

## TL;DR (verdict)

The codebase is **structurally capable of multi-asset-class** — `ExchangeSegment` and `Exchange` enums exist with `NSE_EQ`, `NSE_FNO`, `NSE_CURRENCY`, `BSE_CURRENCY`, `MCX_COMM`, `IDX_I`, and the `Order` / `OrderRequest` / `Trade` records carry `exchangeSegment` everywhere. But the layer that sits **on top of the enums** is **hard-coded to NSE cash + MCX commodity hours** and **NSE F&O conventions**:

- `SessionSchedule` is a binary decision (`MCX_COMM` vs everything else)
- `MatchingEngine` uses a hard-coded 5 paisa tick for **all** exchanges
- `ProductType` is Indian-specific (`INTRADAY`, `CNC`, `MARGIN`, `CARRY_FORWARD`) and a US/EU broker would need a new enum or a translation layer
- `DhanOrderValidator.ALLOWED_PRODUCT_TYPES` is per-broker (Dhan's matrix), not a generic asset-class rule
- `Instrument.instrumentType` is a `String` checked with `.startsWith("FUT")` — type-unsafe

Adding a new asset class (US equity via IBKR) would require touching **9 modules and 14 files**. Adding a new broker that supports the same asset classes (Upstox already exists, ICICI is loaded) is harder than it should be because each broker has its own ad-hoc validation.

The good news: the right shape exists. The bad news: the implementation is a thin layer over NSE conventions, and refactoring it into a generic shape is a 4-6 week project.

Five P0s and seven P1s below.

---

## P0 — Fix before adding the next asset class

### P0-1 — `MatchingEngine` uses a hard-coded 5 paisa tick for every exchange

**Where:** `trading/simulation/src/main/java/com/tradej/simulation/MatchingEngine.java:192-202`

```java
// Apply standard Indian options discrete tick-rounding (5 paisa / 0.05 Rs grid)
long tickSizePaisa = 5;
long remainder = rawFillPrice % tickSizePaisa;
if (remainder != 0) {
    if (request.side() == Side.BUY || request.side() == Side.SHORT) {
        return rawFillPrice + (tickSizePaisa - remainder); // Round up for buy slippage
    } else {
        return rawFillPrice - remainder; // Round down for sell slippage
    }
}
return rawFillPrice;
```

The 5 paisa tick is **NSE F&O specific** (it's the price increment for all NSE options, NIFTY/BANKNIFTY futures, and most F&O contracts). For:

- **NSE equity**: 1 paisa tick (sub-paisa forbidden)
- **BSE equity**: 1 paisa tick
- **MCX futures** (gold, silver, crude): 1 rupee tick (100 paisa)
- **MCX options** on gold: 1 rupee tick
- **CDS (currency)**: 1 paisa tick for most pairs
- **US equity (future)**: 1 cent tick = ~83 paisa
- **Crypto (future)**: variable, often 1 cent or 0.01 USD

The current code rounds every fill to the nearest 5 paisa. A gold MCX order at `30_000_00L` paisa (₹30,000) with a 1 rupee tick would be **silently mispriced** — the 5 paisa rounding produces a price that the exchange will reject, or worse, that the exchange will accept and execute at the wrong level.

**Required fix:** take the tick size from the `Instrument` model. `Instrument.tickSizePaisa` already exists (`core/.../Instrument.java:11`). Inject the instrument into `MatchingEngine.match()` and use `instrument.tickSizePaisa()` instead of the hard-coded `5`. If the instrument is null (e.g. for a backtest with no catalog), throw an explicit error rather than silently misround.

### P0-2 — `SessionSchedule` is a binary MCX-vs-everything-else decision

**Where:** `core/src/main/java/com/tradej/core/domain/value/SessionSchedule.java:35-44`

```java
public static LocalTime sessionOpen(ExchangeSegment exchangeSegment) {
    return exchangeSegment == ExchangeSegment.MCX_COMM ? MCX_OPEN : CASH_OPEN;
}

public static LocalTime sessionClose(ExchangeSegment exchangeSegment) {
    return exchangeSegment == ExchangeSegment.MCX_COMM ? MCX_CLOSE : CASH_CLOSE;
}
```

This treats **NSE FNO, BSE FNO, NSE Currency, BSE Currency, NSE Equity, BSE Equity, and the Index segment** as having the same hours (09:15 - 15:30 IST). That's wrong:

- **NSE Currency (CDS)**: 09:00 - 17:00 IST (USDINR, EURINR, GBPINR, JPYINR)
- **BSE Currency**: 09:00 - 17:00 IST
- **NSE FNO**: 09:15 - 15:30 IST for equity derivatives; 09:00 - 17:00 IST for currency derivatives (which are NSE FNO, not NSE Currency)
- **MCX**: 09:00 - 23:30 IST (with a 30-min break 17:00-17:30 for some commodities)

The current code would report a NIFTY option as "in session" at 16:00 IST when it is actually closed. A currency option would be reported as closed at 16:00 when it is actually open. Both directions break roll-forward logic (`isPastClosingWindow` line 54-67).

**Required fix:** make `SessionSchedule` a `Map<ExchangeSegment, SessionHours>` lookup, where `SessionHours` is `(open, close, eveningClose)` to support MCX's split session. The `isPastClosingWindow` already takes an `ExchangeSegment` argument — it just calls `sessionClose(segment)` which returns the wrong value. The structure is right; the data is wrong.

### P0-3 — `ProductType` is Indian-specific and the comment in `BacktestServiceImpl` is wrong

**Where:** `core/src/main/java/com/tradej/core/domain/value/ProductType.java:1-8`

```java
public enum ProductType {
    INTRADAY,
    CNC,
    MARGIN,
    CARRY_FORWARD
}
```

These are **NSE/BSE product types**:

- `INTRADAY` — squared off at end of day (MIS in Zerodha, BO/CO in some brokers)
- `CNC` — Cash and Carry, delivery-based, no leverage (CNC in Zerodha)
- `MARGIN` — Normal margin product, can be carried overnight (NRML in Zerodha)
- `CARRY_FORWARD` — for currency derivatives, carry forward positions

For a US broker (Alpaca, IBKR, Tradier):
- `CASH` — equity, no borrowing
- `MARGIN` — equity with borrowing
- `SHORT` — short selling
- `FUTURE` — futures contract
- `OPTION` — options contract

For a crypto exchange:
- `SPOT`
- `MARGIN`
- `FUTURES_PERP`
- `OPTIONS`

The current enum has 4 values, all Indian. Adding a US broker means either:
- Expanding the enum to include US values (cluttering the type)
- Creating a per-broker translation layer (e.g. `AlpacaProductMapper`)
- Replacing the enum with a string-typed `ProductType` (loss of type safety)

The right answer is **a per-broker translation layer** that maps the broker's product codes to a smaller set of abstract categories. Define `ProductClass { LEVERAGED, DELIVERY, FUTURES, OPTIONS, CARRY }` and have each broker map its native codes to `ProductClass` values.

Also, **`CARRY_FORWARD` is a Dhan-specific term**, not a standard NSE term. NSE uses "T+1 settlement" or "Forward" for currency carry-forward. The enum has a Dhan-named value. A new broker (Zerodha Kite, for example) would map its `NRML` to Dhan's `MARGIN` or `CARRY_FORWARD`? Unclear.

**Required fix:** introduce `ProductClass` as the canonical category. `ProductType` becomes a per-broker enum. Each `BrokerProvider` declares its own `ProductType` enum (or constant) and a `ProductClass` mapping.

### P0-4 — `Instrument.instrumentType` is a `String` checked with `.startsWith("FUT")`

**Where:** `core/src/main/java/com/tradej/core/domain/model/Instrument.java:6-22`

```java
public record Instrument(
        String symbol,
        String canonicalSymbol,
        Exchange exchange,
        ExchangeSegment exchangeSegment,
        String instrumentType,         // <-- free-form String
        String underlying,
        ...
) {
    public boolean isOption() {
        return optionType != null && optionType != OptionType.UNKNOWN;
    }

    public boolean isFuture() {
        return instrumentType != null && instrumentType.toUpperCase().startsWith("FUT");   // <-- brittle
    }
}
```

The `instrumentType` is supposed to be a category like `"EQUITY"`, `"FUTURE"`, `"OPTION"`, `"CURRENCY_FUTURE"`, `"COMMODITY_FUTURE"`, `"COMMODITY_OPTION"`, `"INDEX"`, `"BOND"`, `"MUTUAL_FUND"`. The check is `startsWith("FUT")`, which means:

- `"FUT"` matches → `true` (intended)
- `"FUTSTOCK"` matches → `true` (probably a typo, but ok)
- `"future"` (lowercase) matches → `true` (after `.toUpperCase()`)
- `"FUTURE"` matches → `true` (intended)
- `"FUTURES"` matches → `true` (intended)
- `"F"` matches → `false` (intended)
- `"FUT0"` matches → `true` (**NOT intended — typo, but a valid string**)
- `null` matches → `false` (intended)

There's no enum. There's no `InstrumentType` value type. There's no contract on what the catalog loader is supposed to write into this field. The Dhan catalog might write `"FUT"`, the Upstox catalog might write `"FUTURE"`, the MCX catalog might write `"FUTCOM"`. They all match the check by accident.

**Required fix:** introduce `InstrumentType` as an enum:

```java
public enum InstrumentType {
    EQUITY, FUTURE, OPTION, CURRENCY_FUTURE, CURRENCY_OPTION,
    COMMODITY_FUTURE, COMMODITY_OPTION, INDEX, BOND, MUTUAL_FUND, UNKNOWN
}
```

`Instrument.instrumentType` becomes `InstrumentType`. `isFuture()` becomes `instrumentType == InstrumentType.FUTURE || instrumentType == InstrumentType.CURRENCY_FUTURE || instrumentType == InstrumentType.COMMODITY_FUTURE`. The catalog loaders map their raw strings to the enum. New asset classes (bonds, mutual funds) get a new enum value, not a new `startsWith` check.

### P0-5 — `OrderRequest` has no `lotSize`, no validation that quantity is a lot multiple

**Where:** `core/src/main/java/com/tradej/core/domain/model/OrderRequest.java` (referenced; not opened)

The `Instrument` record carries `lotSize` (`core/.../Instrument.java:10`), but the `OrderRequest` doesn't carry or validate the lot. NSE FNO orders **must be in multiples of the lot size**:
- NIFTY lot = 25 (current)
- BANKNIFTY lot = 15
- Stock options vary (e.g. RELIANCE lot = 250)
- MCX GOLD lot = 100 grams (1 contract)

The order validation in `DhanOrderValidator` checks segment × product type compatibility, but not lot size. An order for `quantity = 13` NIFTY options would be accepted by validation and rejected by the exchange. The current code lets the broker (Dhan) reject it, but a pre-flight check would be better.

**Required fix:** add `LotSizeValidator` that:
1. Looks up the instrument by `symbol + exchangeSegment`.
2. Checks `quantity % instrument.lotSize() == 0`.
3. Returns a `ValidationIssue` with severity `ERROR` if the check fails.

This belongs in the generic validation layer (not Dhan-specific), since lot sizes are exchange-mandated and apply to all brokers.

---

## P1 — Fix in the next two sprints

### P1-1 — `DhanOrderValidator.ALLOWED_PRODUCT_TYPES` is Dhan-specific; Upstox and ICICI have their own matrices

**Where:** `broker/dhan/src/main/java/com/tradej/broker/dhan/validator/DhanOrderValidator.java:44-53`

```java
private static final Map<ExchangeSegment, Set<ProductType>> ALLOWED_PRODUCT_TYPES = Map.of(
        ExchangeSegment.NSE_EQ, Set.of(ProductType.INTRADAY, ProductType.CNC, ProductType.MARGIN),
        ExchangeSegment.BSE_EQ, Set.of(ProductType.INTRADAY, ProductType.CNC, ProductType.MARGIN),
        ExchangeSegment.NSE_FNO, Set.of(ProductType.INTRADAY, ProductType.MARGIN),
        ExchangeSegment.BSE_FNO, Set.of(ProductType.INTRADAY, ProductType.MARGIN),
        ExchangeSegment.MCX_COMM, Set.of(ProductType.INTRADAY, ProductType.MARGIN),
        ExchangeSegment.NSE_CURRENCY, Set.of(ProductType.INTRADAY, ProductType.MARGIN),
        ExchangeSegment.BSE_CURRENCY, Set.of(ProductType.INTRADAY, ProductType.MARGIN),
        ExchangeSegment.IDX_I, Set.of(ProductType.INTRADAY, ProductType.MARGIN)
);
```

This matrix is correct for Dhan's product model. Upstox has a different matrix (Upstox's `D` is delivery, `I` is intraday, `M` is margin, but with different segment restrictions). The current code lives in `broker-dhan` and is not reusable.

**Required fix:** move the **exchange-mandated** product restrictions (which are the same across brokers) to `core/.../validation/ExchangeProductMatrix.java` (a new class in `core`). The Dhan-specific additions (e.g. Dhan doesn't allow `MARGIN` on BSE_EQ) stay in `DhanOrderValidator`. New brokers (Upstox, ICICI, IBKR) extend the base matrix with their own restrictions.

### P1-2 — `NSE_CURRENCY` and `BSE_CURRENCY` both map to `Exchange.CDS`, losing the BSE/NSE distinction

**Where:** `core/src/main/java/com/tradej/core/domain/value/ExchangeSegment.java:10-11`

```java
NSE_CURRENCY(Exchange.CDS),
BSE_CURRENCY(Exchange.CDS),
```

The NSE and BSE currency derivative segments are **different exchanges with different instruments and different hours**. Mapping both to `Exchange.CDS` means:
- A query for "all CDS orders" can't distinguish NSE currency from BSE currency.
- An audit log showing `exchange = CDS` is ambiguous.
- A routing decision based on `exchange` cannot pick the right broker (BSE-only broker can't handle NSE currency).

**Required fix:** either:
- Add `NCDS` and `BCDS` to the `Exchange` enum, or
- Make the `venueExchange` method on `ExchangeSegment` return a more specific exchange (e.g. `NSE_CURRENCY.venueExchange() = NCDS`).

The second option is less invasive. The `Exchange` enum becomes a "macro category" (NSE family, BSE family, MCX, INDEX, UNKNOWN) and the segment carries the specific exchange.

### P1-3 — `Instruments.commodity(String)` and `Instruments.currency(String)` are plumbing for the future, never wired

**Where:** `core/src/main/java/com/tradej/core/domain/instrument/Instruments.java:16-17`

```java
public static InstrumentKey commodity(String symbol) { return InstrumentKey.of(symbol, ExchangeSegment.MCX_COMM); }
public static InstrumentKey currency(String symbol) { return InstrumentKey.of(symbol, ExchangeSegment.NSE_CURRENCY); }
```

I searched for callers of `Instruments.commodity` and `Instruments.currency` — there are none. The methods exist but are dead. This is a "we'll wire it later" stub that signals intent but doesn't fulfill it.

**Required fix:** either:
- Add a unit test that calls `commodity("GOLD")` and asserts the symbol resolves to an MCX instrument in the catalog (a contract test), or
- Delete the methods until they're actually used.

I'd take the first option. The methods are useful, but the absence of a test means they could be silently wrong (e.g. mapping to NSE_CURRENCY when MCX is the right answer for "GOLD").

### P1-4 — `BrokerHandle.defaultSegment` checks for index names, not for the actual exchange

**Where:** `broker-gateway/src/main/java/com/tradej/brokergateway/BrokerHandle.java:371-378`

```java
private ExchangeSegment defaultSegment(String symbol) {
    if (symbol != null && (symbol.startsWith("NIFTY") || symbol.startsWith("BANKNIFTY")
            || symbol.startsWith("FINNIFTY") || symbol.startsWith("MIDCPNIFTY")
            || symbol.startsWith("SENSEX") || symbol.startsWith("BANKEX"))) {
        return ExchangeSegment.IDX_I;
    }
    return ExchangeSegment.NSE_EQ;
}
```

This is the **same issue** flagged in `PLUGIN_MARKET_UTILS_REVIEW_2026-06-06.md` (P0-1), restated in the multi-asset context:
- `SENSEX` and `BANKEX` are BSE indices, not NSE. Mapping them to `IDX_I` (which is `Exchange.INDEX`) loses the BSE/NSE distinction.
- The function returns `NSE_EQ` for anything that doesn't start with an index prefix. A user calling `defaultSegment("GOLD")` gets `NSE_EQ`, but GOLD is on MCX. The user has to override with `defaultSegment("GOLD", MCX_COMM)` explicitly. There's no error.
- The function is duplicated in `PluginMarketUtils` (see `PLUGIN_MARKET_UTILS_REVIEW_2026-06-06.md` P1-1).

**Required fix:** the segment should come from the **instrument catalog**, not from string prefix matching. When a user passes a symbol, the broker should resolve the instrument and return its `exchangeSegment`. The `defaultSegment` helper is a workaround for the case where the user wants the LTP of an index without specifying the segment — and even then, the helper should call the broker's index resolution endpoint, not pattern-match on the string.

### P1-5 — `DhanSegmentMapper` uses hardcoded integer codes per exchange

**Where:** `broker/dhan/src/main/java/com/tradej/broker/dhan/instrument/DhanSegmentMapper.java` and `broker/dhan/src/main/java/com/tradej/broker/dhan/depth/DhanExchangeSegmentCodes.java`

These files contain `Map.entry(5, ExchangeSegment.MCX_COMM)`, `Map.entry("MCX", ExchangeSegment.MCX_COMM)`, etc. The integer codes are Dhan's wire protocol. Each broker has its own wire protocol with its own codes. There's no central registry of "this is the meaning of segment code 5 at Dhan."

The current code is in `broker-dhan` (good — broker-specific), but the codes are duplicated across the segment mapper and the depth parser. If Dhan changes a code (e.g. adds a new segment), both files need updating.

**Required fix:** introduce a `DhanProtocolConstants.SEGMENT_CODES` map in `broker/dhan/src/main/java/com/tradej/broker/dhan/constants/DhanProtocolConstants.java` (which exists per `DhanOrderValidator.java:6`). Reference it from both the segment mapper and the depth parser. Add a unit test that asserts every `ExchangeSegment` has a Dhan code; this will fail when a new segment is added without a code.

### P1-6 — `Exchange` enum has both `INDEX` and `IDX` — same meaning, two names

**Where:** `core/src/main/java/com/tradej/core/domain/value/Exchange.java:3-13`

```java
public enum Exchange {
    NSE,
    BSE,
    NFO,
    BFO,
    MCX,
    CDS,
    INDEX,    // <-- one of these is redundant
    IDX,      // <--
    UNKNOWN
}
```

`INDEX` and `IDX` are both abbreviations for the same concept. The codebase is split: `ExchangeSegment.IDX_I` uses the macro category `INDEX` (line 8 of `ExchangeSegment.java`), but no other code uses `IDX` (per my search). The `IDX` constant is **dead code** or **a typo waiting to bite**.

**Required fix:** delete `IDX`. If a future code path needs it, it can be re-added with a clear contract. The current redundancy is a maintenance hazard.

### P1-7 — No abstraction for "this segment supports options" / "this segment supports futures" / etc.

Several places in the codebase have the same logic hard-coded:

- `Instrument.isOption()` checks `optionType != null && optionType != OptionType.UNKNOWN` (line 19 of `Instrument.java`)
- `Instrument.isFuture()` checks `instrumentType.startsWith("FUT")` (line 22) — type-unsafe per P0-4
- `DhanOrderValidator` has `FNO_COMMODITY_CURRENCY_SEGMENTS = Set.of(NSE_FNO, BSE_FNO, MCX_COMM, NSE_CURRENCY, BSE_CURRENCY, IDX_I)` (line 56-63 of `DhanOrderValidator.java`)
- `Instruments` static helpers hardcode the segment per asset class (NSE_FNO for futures, IDX_I for indices, NSE_EQ for equity, etc.)
- `GatewayEventBridge` and other code paths hardcode "this is a market data event" vs "this is a position event"

A "this segment is F&O" check appears in at least 4 places. A "this segment is an index" check appears in 2-3 places. A "this segment requires the matching engine" check appears in 1-2 places.

**Required fix:** add capability methods to `ExchangeSegment`:

```java
public boolean supportsOptions() { return this == NSE_FNO || this == BSE_FNO || this == MCX_COMM || this == NSE_CURRENCY || this == BSE_CURRENCY; }
public boolean supportsFutures() { return /* same */ }
public boolean isIndex() { return this == IDX_I; }
public boolean isCashEquity() { return this == NSE_EQ || this == BSE_EQ; }
public boolean isCurrency() { return this == NSE_CURRENCY || this == BSE_CURRENCY; }
public boolean isCommodity() { return this == MCX_COMM; }
public boolean requiresLotSize() { return supportsOptions() || supportsFutures() && this != IDX_I; }
public long defaultTickSizePaisa() { return isCommodity() ? 100L : supportsOptions() ? 5L : 1L; }
```

Replace the hard-coded checks with calls to these methods. The `MatchingEngine` P0-1 fix becomes `instrument.tickSizePaisa() == null ? segment.defaultTickSizePaisa() : instrument.tickSizePaisa()`.

---

## P2 — Fix in the next quarter

### P2-1 — `Core` has no concept of "broker-native product type" → `ProductType` mapping

`ProductType` is a single enum. A US broker (Alpaca) would need to map `CASH`, `MARGIN`, `SHORT` to `INTRADAY`/`CNC`/`MARGIN`/`CARRY_FORWARD`. The mapping is per-broker. There's no `BrokerProductMapper` interface or per-broker implementation. The codebase implicitly assumes "we are NSE/BSE, INTRADAY/CNC/MARGIN are the universal types."

**Required fix:** introduce `BrokerProductMapper` (one per broker) that maps `BrokerNativeProductCode → ProductClass`. The `OrderRequest` carries a `ProductClass` (or a per-broker `String`). The `BrokerProductMapper` translates at submission time.

### P2-2 — `TickSizePaisa` and `LotSize` are per-broker-catalog values; no source of truth

`Instrument.tickSizePaisa` and `Instrument.lotSize` come from the broker's instrument catalog. Dhan publishes its catalog as a CSV. Upstox publishes as JSON. MCX publishes as a separate file. The values are correct for the broker that loaded them, but if a different broker's catalog is loaded later, the values change.

A backtest that uses Dhan's catalog today and Upstox's catalog tomorrow will have **different lot sizes** for the same symbol. The backtest is not reproducible.

**Required fix:** keep a normalized `LotSizeRegistry` and `TickSizeRegistry` in `core/.../marketdata/`. The catalogs are loaded into this registry at startup. The `Instrument` is populated from the registry, not from the broker's catalog directly. The registry is keyed by `(exchange, segment, symbol)`.

### P2-3 — `DhanBinaryParser` has hard-coded exchange segment codes

`broker/dhan/src/main/java/com/tradej/broker/dhan/websocket/DhanBinaryParser.java:188` has a `case 6 -> ExchangeSegment.MCX_COMM;` in a switch. The `6` is Dhan's wire code for MCX. If Dhan adds a new code (or renames), this switch misses it. The default case (if any) likely falls through to `UNKNOWN`.

**Required fix:** move the segment code → `ExchangeSegment` mapping to a single lookup table (`DhanProtocolConstants.SEGMENT_CODES`). The parser does a map lookup. A missing code throws or logs WARN.

### P2-4 — `Order` and `Trade` records do not carry the lot size

`core/.../domain/model/Order.java` (not opened) and `core/.../domain/model/Trade.java` (not opened) carry `quantity` (the number of shares / contracts). The `quantity` is the **post-lot quantity** (e.g. 25 NIFTY options = 1 lot). The `lotSize` is not persisted. A historical query for "all NIFTY option orders in 2024" cannot compute the number of lots without re-loading the catalog.

**Required fix:** add `lotSize` to the `Order` and `Trade` records. Persist it. On replay, the lot size is reconstructed from the catalog at the time the order was placed (which requires historical catalog snapshots, a much bigger problem).

This is a "nice to have" for the current 1-broker setup. For multi-broker, it's required: a Dhan order and an Upstox order for the same NIFTY option may have different lot sizes if NSE changed the lot between the two orders.

### P2-5 — No support for Bonds / G-Secs

`Exchange` and `ExchangeSegment` have no entry for bonds. NSE has a wholesale debt segment (WDM), BSE has a debt segment. The `Instrument` model has no `coupon`, `maturityDate`, `faceValue` fields.

Adding bond support requires:
- New `ExchangeSegment.BOND_WDM` and `BOND_BSE`
- New `InstrumentType.BOND` (per P0-4)
- New `OrderRequest` fields for `priceType` (clean/dirty), `yield` (YTM)
- New `Order` and `Trade` fields for the same
- New `DhanOrderValidator` rules for bonds (lot size = 1, no short selling, etc.)

**Required fix:** leave as a separate project. The current model is **not bond-aware** and shouldn't pretend to be.

### P2-6 — No support for Mutual Funds

Same as bonds. NSE has MF segments. The `OrderRequest` doesn't have a `folioNumber` or `amount` (MFs are orderless — you specify an amount, not a quantity). The `DhanOrderValidator` doesn't know about MF-specific rules.

**Required fix:** leave as a separate project. Note in the `ProductType` Javadoc that MFs are not supported.

### P2-7 — `Instruments.bankNifty()` returns `"NIFTY BANK"` — flagged in `PLUGIN_MARKET_UTILS_REVIEW_2026-06-06.md` P0-1, restated in multi-asset context

The "wrong symbol" issue isn't just an `Instruments` bug. The same kind of bug exists for any **multi-exchange asset class**:
- `commodity("GOLD")` returns an `InstrumentKey` with `MCX_COMM` — but the symbol `"GOLD"` on MCX is the **futures** root, not the spot or the options. The catalog has `"GOLD"`, `"GOLDM"`, `"GOLDP"`, `"GOLDGUINEA"`, etc. `commodity("GOLD")` doesn't disambiguate.
- `currency("USDINR")` returns `NSE_CURRENCY` — but the symbol is shared between NSE Currency and BSE Currency. The instrument resolver picks one, the other is ignored.
- `equity("RELIANCE")` returns `NSE_EQ` — but RELIANCE is also on BSE. The user has no way to disambiguate.

The deeper problem is that the helper methods take a single `String` for an asset that is **not uniquely identified by its display name**. The right API is one that takes `(ExchangeSegment, String symbol)` or one that queries the catalog for matches.

**Required fix:** the helper methods should accept an `ExchangeSegment` parameter. If the user doesn't supply one, call the broker's instrument resolver to find the match and return the first one. If multiple matches, return all of them and let the caller disambiguate.

---

## Recommendations (concrete, ordered)

| # | Action | Module | Effort | Impact |
|---|---|---|---|---|
| 1 | Use `Instrument.tickSizePaisa()` in `MatchingEngine` (P0-1) | `trading/simulation`, `core` | 0.5d | High — MCX/Crypto correct |
| 2 | Make `SessionSchedule` a per-segment map (P0-2) | `core` | 0.5d | High — CDS hours correct |
| 3 | Replace `ProductType` with `ProductClass` + per-broker enum (P0-3) | `core`, `broker-dhan`, `broker-upstox` | 1w | Medium — multi-broker ready |
| 4 | Introduce `InstrumentType` enum (P0-4) | `core` | 0.5d | High — type safety |
| 5 | Add `LotSizeValidator` (P0-5) | `core` | 0.5d | High — pre-flight check |
| 6 | Move exchange-mandated product matrix to `core` (P1-1) | `core`, `broker-dhan` | 0.5d | Medium — reusable validation |
| 7 | Add capability methods to `ExchangeSegment` (P1-7) | `core` | 0.5d | High — DRY |
| 8 | Test `Instruments.commodity` and `Instruments.currency` (P1-3) | `core` | 0.25d | Low — contract test |
| 9 | Add `DhanProtocolConstants.SEGMENT_CODES` (P1-5) | `broker-dhan` | 0.5d | Medium — single source |
| 10 | Delete `Exchange.IDX` (P1-6) | `core` | 0.05d | Low — dead code |

Total: **~3-4 person-weeks** to go from "works for NSE F&O, half-works for MCX, doesn't work for CDS or US" to "works for all Indian segments and is ready for a new broker."

---

## Closing thought

The codebase has the **right shape** for multi-asset-class — the `Exchange` and `ExchangeSegment` enums exist, the `Order` and `Trade` records carry the segment everywhere, the `Instrument` model has `tickSizePaisa` and `lotSize`. The **wrong implementation** is in the thin layer on top: hard-coded ticks, hard-coded hours, hard-coded product type names.

The single highest-leverage change is **P1-7** — add capability methods to `ExchangeSegment`. That one change replaces 4-5 hard-coded segment checks across the codebase, makes `MatchingEngine` and `SessionSchedule` correct, and makes it obvious where to add new asset classes (a new method on the enum, a new enum value, done).

Beyond that, the multi-region / multi-broker story is a 4-6 week refactor that requires:
- Per-broker product type mapping (P0-3, P1-1)
- Normalized lot/tick registries (P2-2)
- Asset-class-specific order fields (P2-4)

None of these are hard individually. The hard part is that **no single review can cover all of them** — they're a sequence of small changes that need to land together. Start with P1-7 (the leverage point), then P0-1 + P0-2 (the correctness bugs), then the rest as a quarter-long project.

See also:
- `docs/reports/PLUGIN_MARKET_UTILS_REVIEW_2026-06-06.md` — P0-1 (`Instruments.bankNifty` returns wrong symbol) and P1-1 (per-exchange parser) are direct dependencies of this review
- `docs/reports/SIMULATION_REPLAY_BACKTEST_REVIEW_2026-06-06.md` — P1-6 (`MatchingEngine.onTick` variance is not real variance) is a P0-1 sibling
- `docs/reports/ARCHITECTURE_REVIEW_2026-06-06.md` — P0-2 (dedup) applies to multi-broker event streams the same way it applies to the current single-broker one
