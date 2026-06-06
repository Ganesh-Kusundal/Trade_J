# P1 Plugin & Market Utils Review
**Date:** 2026-06-06
**Scope:** Hidden coupling, untestable complexity, and SPI registration bugs in the market-utility code (`core/domain/instrument/`, `core/domain/value/`) and the plugin layer (`broker-gateway/explorer/`, `broker-gateway/certification/`, `broker-gateway/query/`, `META-INF/services`).
**Author persona:** Principal quant engineer — opinionated, file:line specific, brutally honest.

---

## TL;DR (verdict)

The market-utility layer has **two real correctness bugs** that will misroute orders (`Instruments.bankNifty()` returns `"NIFTY BANK"` not `"BANKNIFTY"`) and **one static helper class that hardcodes NSE index names** (`Instruments.java:8-19`). The plugin layer has **the right idea** (a `ServiceLoader`-based SPI for broker providers, a `BrokerDescriptor` for capability advertisement) but **the implementation is split across three sources of truth** (the descriptor, the explorer's hard-coded name list, the certification's hard-coded list) that will drift the first time someone adds a port.

The certification and exploration classes (`BrokerCertification`, `DefaultBrokerInspector`, `BrokerExplorer`) are **smoke-test harnesses** that make 22+ live HTTP calls to the broker, in sequence, with a hard-coded ATM strike of 25000. They are useful but fragile, and they have **no parallel execution, no rate limiting, and no way to skip a probe that requires a market-open symbol**. They will break the moment a CI cron runs them against a closed market.

The "plugin" half of the SPI story is well-designed; the "registry + descriptor" half is not. The two `DuckDbQueryEngine` and `HistoricalRangeService` connections to the same DB file are **a latent write-lock conflict**. And the `META-INF/services` file lists four providers — including `IciciBrokerProvider` which the architecture review flagged as "nearly dead". The loader pays the cost for all four.

Six P0s and four P1s below.

---

## P0 — Fix this sprint

### P0-1 — `Instruments.bankNifty()` returns `"NIFTY BANK"` but Dhan's symbol is `"BANKNIFTY"`

**Where:** `core/src/main/java/com/tradej/core/domain/instrument/Instruments.java:8-19`

```java
public static InstrumentKey nifty()        { return InstrumentKey.of("NIFTY", ExchangeSegment.IDX_I); }
public static InstrumentKey bankNifty()    { return InstrumentKey.of("NIFTY BANK", ExchangeSegment.IDX_I); }   // <-- wrong
public static InstrumentKey finNifty()     { return InstrumentKey.of("NIFTY FIN SERVICE", ExchangeSegment.IDX_I); }
public static InstrumentKey midcpNifty()   { return InstrumentKey.of("NIFTY MID SELECT", ExchangeSegment.IDX_I); }
public static InstrumentKey niftyFuture()  { return InstrumentKey.of("NIFTY", ExchangeSegment.NSE_FNO); }
public static InstrumentKey bankNiftyFuture() { return InstrumentKey.of("NIFTY BANK", ExchangeSegment.NSE_FNO); }  // <-- wrong
```

Dhan's instrument catalog uses `"BANKNIFTY"` (one word, all caps) for the spot index and `"BANKNIFTY"` for the future. NSE's own display name is "NIFTY BANK" but the **security identifier** is "BANKNIFTY". When `OrderRequest` is built from `Instruments.bankNifty()`, the symbol `"NIFTY BANK"` is sent to `BrokerConnection.placeOrder`, which looks up the security ID via `InstrumentResolver.resolveNormalized("NIFTY BANK", IDX_I)`. Depending on the resolver's tolerance, the result is one of:

- Symbol not found → `IllegalArgumentException` → order rejected
- Symbol silently mapped to BANKNIFTY → order goes through, but logs and reports show the wrong symbol

The other indices have a similar problem:
- `"FIN SERVICE"` — Dhan uses `"FINNIFTY"`
- `"MID SELECT"` — Dhan uses `"MIDCPNIFTY"`

I have not read every broker's symbol catalog to confirm. I am reading the most common Dhan conventions. The fix is to **discover the symbols from each broker's catalog at startup**, not hardcode them in `Instruments.java`.

**Required fix:**
- Replace the static `Instruments.bankNifty()` etc. with a `WellKnownIndices` enum that lists the **display name** (for UI), the **broker symbol** (per-broker), and the **exchange segment** (per-broker).
- At startup, query each broker's `InstrumentResolver` for the broker-specific security ID and store the mapping.
- For now, fix the obvious mistakes: `"NIFTY BANK"` → `"BANKNIFTY"`, `"NIFTY FIN SERVICE"` → `"FINNIFTY"`, `"NIFTY MID SELECT"` → `"MIDCPNIFTY"`.
- Add a unit test that asserts `Instruments.bankNifty().symbol()` matches the security ID Dhan uses. This test will fail until the symbols are aligned.

### P0-2 — `ContractSymbolNormalizer.normalize` returns uppercase input unchanged when no pattern matches — silent error swallow

**Where:** `core/src/main/java/com/tradej/core/domain/instrument/ContractSymbolNormalizer.java:68-80`

```java
public static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
        return "";
    }
    ParsedContract parsed = parse(raw);
    if (parsed == null) {
        return raw.trim().toUpperCase(Locale.ENGLISH);   // <-- silent pass-through
    }
    ...
}
```

If a user passes `"NIFTYY"` (typo, extra Y) or `"BANKNIFT 30 JUN FUT"` (one space short), the function does not detect the typo — it just uppercases the input and returns. The caller cannot tell the difference between a normalized symbol and a raw input that failed to parse.

The `parse` method (line 39-62) returns `null` for unrecognized input, but **nobody checks**. The only caller in the codebase that checks the return of `parse` is `extractFutureUnderlying` (line 130-139), which uses it for regex fallback.

This means: an order for `"SBINEQ"` (typo for "SBIN") becomes an order for security "SBINEQ", which doesn't exist, which the broker rejects. The rejection is correct; the lack of a `Logger.warn("Could not normalize symbol: " + raw)` is the bug.

**Required fix:** add a `parse` and a `tryNormalize` (returns `Optional<String>`) variant. Use the optional variant at order-entry boundaries. The non-optional `normalize` can stay for compatibility, but should log a WARN at the call site for unrecognized input.

### P0-3 — `META-INF/services/com.tradej.brokergateway.spi.BrokerProvider` lists 4 providers; `IciciBrokerProvider` is nearly dead

**Where:** `broker-gateway/src/main/resources/META-INF/services/com.tradej.brokergateway.spi.BrokerProvider`

```
com.tradej.brokergateway.spi.impl.DhanBrokerProvider
com.tradej.brokergateway.spi.impl.UpstoxBrokerProvider
com.tradej.brokergateway.spi.impl.IciciBrokerProvider
com.tradej.brokergateway.simulation.SimulationBrokerProvider
```

The `IciciBrokerProvider` was flagged in the original architecture review as "nearly dead — two files, near-zero callers." Yet it is **always loaded** at JVM startup by `ServiceLoader.load(BrokerProvider.class)`. The cost:

1. `ServiceLoader` instantiates the provider class (no-arg constructor + `isEnabled()` check).
2. `DefaultBrokerRegistry.register(provider)` is called, adding an entry to a `LinkedHashMap`.
3. If `isEnabled()` returns `false`, the provider is **not** registered. But the class is still loaded.

Classloading for an unused provider is cheap (the class is small). But the **principle** is wrong: dead code on the classpath is technical debt, and SPI discovery means you can disable a provider with a class change but you can't disable it from configuration.

**Required fix:**
- Delete `IciciBrokerProvider` and its `META-INF/services` entry. If a future user needs ICICI, they can re-add the provider.
- Add a runtime config flag `-Dtradej.broker.providers=dhan,upstox,simulation` that the `ServiceLoaderBrokerRegistry` consults. The flag, if set, is authoritative; if unset, all providers are loaded.
- Log the loaded providers at startup with `INFO` so an operator can verify.

### P0-4 — `DhanBrokerProvider.connect` is eager: the gateway constructor makes a network call

**Where:** `broker-gateway/src/main/java/com/tradej/brokergateway/spi/impl/DhanBrokerProvider.java:58-65`

```java
@Override
public IBrokerConnection connect(BrokerProfile profile) {
    if (profile.dhan() == null) {
        throw new IllegalArgumentException("Dhan configuration is required");
    }
    BrokerProfile dhanProfile = new BrokerProfile(...);
    return BrokerComposition.create(dhanProfile).brokerConnection();
}
```

`BrokerComposition.create(dhanProfile).brokerConnection()` opens an HTTP connection to Dhan's API to validate the access token (see `AUDIT_REPORT.md`'s note on token validation). The result is a connected `IBrokerConnection`. So:

```java
BrokerGateway gateway = BrokerGateway.dhan(dhanConfig);   // <-- makes a network call
```

The constructor makes a network call. This is a **hidden side effect** that:

- Breaks any test that doesn't expect a network round-trip in the constructor.
- Makes Spring's bean instantiation order fragile: a misconfigured Dhan profile will fail at startup with a network error, not a config validation error.
- Means the "I have a gateway" semantic is "I have a gateway AND I'm authenticated AND I have a live HTTP connection to Dhan".

This is a **capability leak**: the SPI's contract is "I know how to create a connection", not "I have created one for you eagerly."

**Required fix:** make `connect(BrokerProfile)` return a **lazy** `IBrokerConnection` proxy that defers the network call until the first method invocation. Or — simpler — change the SPI to a two-phase init: `BrokerProvider.createProfile(BrokerProfile)` returns a configured `IBrokerConnection` that does not connect, and `IBrokerConnection.connect()` is called explicitly by the gateway during startup.

The lazy-proxy option is cleaner; the explicit-connect option matches Spring's lifecycle. Either is fine. **Stop calling the network from a constructor.**

### P0-5 — `DuckDbQueryEngine.registerDatasource` leaves the engine in a half-initialized state if the SQL throws

**Where:** `broker-gateway/src/main/java/com/tradej/brokergateway/query/DuckDbQueryEngine.java:67-71`

```java
public void registerDatasource(String name, MarketDatasource ds) {
    ds.register(connection, name);   // <-- executes SQL, can throw
    datasources.put(name, ds);       // <-- only on success
    log.debug("Registered datasource: {}", name);
}
```

If `ds.register(connection, name)` throws (e.g. the SQL has a syntax error, the underlying parquet file is missing, the schema is wrong), the exception propagates. But **the connection is still open** and **the datasources map is missing this entry**. A caller doing `registerDatasource("a", ...); registerDatasource("b", ...)` where "a" throws will get:

- Connection open
- DataSource "b" registered (because we never get to the `datasources.put` for "a" before the exception; but for "b", the throw is *inside* its `ds.register`, so it never gets to its put either)

Actually wait — the exception from "a" propagates and "b" is never reached. So the engine has 0 datasources, which is the correct state for a failed init. But the `MarketDatasource.register` for "a" may have done partial work: created a temporary table, then failed on a CREATE VIEW referencing it. The connection has a half-built state.

**Required fix:** wrap `ds.register` in a try/catch that:
1. Logs the failure with the datasource name and SQL.
2. Rolls back the partial state (DROP the temp table if it was created).
3. Rethrows as a typed `DatasourceRegistrationException`.

Or — better — make `MarketDatasource.register` transactional. DuckDB supports `BEGIN` / `COMMIT` / `ROLLBACK`. Use them.

### P0-6 — `BrokerExplorer.formatReport` has a logic bug that mis-classifies capabilities

**Where:** `broker-gateway/src/main/java/com/tradej/brokergateway/explorer/BrokerExplorer.java:83-110`

```java
public static String formatReport(BrokerInspectionReport report) {
    StringBuilder sb = new StringBuilder();
    sb.append("\n=== Broker Inspector: ").append(report.source()).append(" ===\n\n");
    sb.append("  Port Interfaces:\n");
    int portCount = 0;
    for (Map.Entry<String, Boolean> entry : report.capabilities().entrySet()) {
        if (portCount >= 15) break;
        String icon = entry.getValue() ? "✓" : "✗";
        sb.append(String.format("    %s %s%n", icon, entry.getKey()));
        portCount++;
    }
    sb.append("\n  Capability Markers:\n");
    int markerCount = 0;
    for (Map.Entry<String, Boolean> entry : report.capabilities().entrySet()) {
        if (markerCount < 15) { markerCount++; continue; }      // <-- skip first 15
        String icon = entry.getValue() ? "✓" : "✗";
        sb.append(String.format("    %s %s%n", icon, entry.getKey()));
        markerCount++;
    }
    ...
}
```

The intent is "first 15 entries are Port Interfaces, next 6 are Capability Markers." But the `BrokerInspectionReport.capabilities` is a `LinkedHashMap` populated by `BrokerExplorer.inspect` (line 41-64), which inserts the **port interfaces** first (15 of them) and the **capability markers** second (6 of them). So this should work in principle.

**The bug:** the cutoff is hard-coded to `15`. The current `inspect` happens to insert exactly 15 ports + 6 markers, so it works. But:

- If a new port is added to `BrokerExplorer.inspect` (e.g. line 41-56 list 16 ports), the cutoff is wrong; the new port is printed under "Capability Markers" and a real marker is dropped from the report.
- If a port is removed, the first marker is printed under "Port Interfaces" and the report has 14 ports + 7 markers — visually misleading but correct in count.
- The `capabilities` map is exposed on the report and consumed by the `formatReport` method in the same class, but the **same map is also used by the descriptor** (which is created by `BrokerProvider.descriptor()` in a different module). Two different producers of the same data structure, with a magic number in between.

**Required fix:** change `BrokerInspectionReport` to have **two distinct maps** — `portCapabilities: Map<String, Boolean>` and `markerCapabilities: Map<String, Boolean>` — set explicitly by the producer. The formatter just iterates each. No magic number. No implicit ordering.

---

## P1 — Fix in the next two sprints

### P1-1 — `ContractSymbolNormalizer` has 4 regexes for 2 contract types; will break on a new exchange

`core/src/main/java/com/tradej/core/domain/instrument/ContractSymbolNormalizer.java:19-34` defines:

- `SPACED_OPTION_PATTERN` (e.g. `"BANKNIFTY 30 JUN 30000 CALL"`)
- `COMPACT_OPTION_PATTERN` (e.g. `"BANKNIFTY30JUN30000CE"`)
- `SPACED_FUTURE_PATTERN` (e.g. `"NIFTY 30 JUN FUT"`)
- `COMPACT_FUTURE_PATTERN` (e.g. `"NIFTY30JUNFUT"`)

Each pattern is a separate regex, and each regex has the same `(?<underlying>...) (?<day>...) (?<month>...) ...` structure. If MCX or BSE adds a new contract format (e.g. `"GOLD 5 OCT 2024 FUT"`, with a 4-digit year), the patterns need to be updated **and** the parsing logic needs to know which exchange. There is no concept of "the exchange this symbol is for" inside `ContractSymbolNormalizer`. The class is exchange-blind.

The `BrokerHandle.defaultSegment` method (`broker-gateway/.../BrokerHandle.java:371-378`) **does** check for NIFTY/BANKNIFTY/FINNIFTY/etc. and assigns `IDX_I`. It is also exchange-blind. The two methods together encode the same "this is an index" knowledge in two places.

**Required fix:** introduce a `ContractSymbolParser` interface with implementations per exchange:
- `NseContractSymbolParser`
- `BseContractSymbolParser`
- `McxContractSymbolParser`

Each parser knows its own date format, its own strike notation, its own option type codes. The `ContractSymbolNormalizer` becomes a dispatcher that picks the right parser based on the symbol prefix or an explicit exchange parameter. The `BrokerHandle.defaultSegment` is folded into the same logic.

### P1-2 — `DefaultBrokerInspector.inspect` is sequential and makes 22+ live calls

**Where:** `broker-gateway/src/main/java/com/tradej/brokergateway/explorer/DefaultBrokerInspector.java:56-117`

Each probe is a sequential network call:
- 5 market data probes (ltp, quote, depth, ohlc, candles-5m)
- 5 options probes (expiries, chain, contracts, select-strike, greeks)
- 3 portfolio probes (balance, positions, holdings)
- 2 order probes (order-book, trade-book)
- 1 margin probe
- 1 futures probe
- 4 capability checks
- 1 websocket probe
- 1 catalog probe

At 200-500ms per call (typical for Dhan), this is **4.5-11 seconds** per inspection. The first failure of any probe marks the whole report as `FAIL` (in `computeOverall`). The probes cannot be parallelized without changing the call signature; the `BrokerHandle` API is synchronous.

Worse, **`probeSelectStrike` uses a hard-coded `spot=25000_00L`** (line 188-192). For a BANKNIFTY probe, the strike 25000 is far OTM. The probe will fail. For a NIFTY probe on a day when NIFTY is at 19000, 25000 is OTM. The probe is **only correct** when NIFTY's spot is near 25000 — a narrow window.

**Required fix:**
- Make the probe `BrokerInspector.inspectAsync()` that returns a `CompletableFuture<BrokerInspectionReport>`. Run all probes in parallel bounded by a `Semaphore(N)`.
- For the strike probe, fetch the LTP first, then use it as the spot. Or accept the spot as a parameter.
- For the WebSocket probe, do not block on `isConnected()`; instead subscribe to the connection-status event and timeout after 5 seconds.
- Add a per-probe timeout (e.g. 10s). Probes that exceed the timeout are marked `TIMEOUT` in the report.

### P1-3 — `DefaultBrokerInspector.probeOptionGreeks` has a control-flow bug

`broker-gateway/src/main/java/com/tradej/brokergateway/explorer/DefaultBrokerInspector.java:194-206`:

```java
private CapabilityProbe probeOptionGreeks(BrokerHandle broker, String underlying, ExchangeSegment segment) {
    return timedProbe("option-greeks", () -> {
        GatewayResult<OptionChainSnapshot> chainResult = broker.optionChain(underlying);
        if (chainResult.data() == null || chainResult.data().strikes().isEmpty()) {
            return "no chain for greeks";
        }
        var firstStrike = chainResult.data().strikes().getFirst();
        if (firstStrike.call() != null && firstStrike.call().greeks() != null) {
            return "delta=" + firstStrike.call().greeks().delta();
        }
        return "greeks not available in chain";
    });
}
```

This always checks `firstStrike.call().greeks()` — it never checks `firstStrike.put()`. If the option chain's first strike has a `null` call (which can happen on chains sorted by put side, or chains with sparse data), the probe returns "greeks not available" even when the put has greeks. The probe is asymmetric.

**Required fix:** check both `call().greeks()` and `put().greeks()`, return the first non-null value.

### P1-4 — `BrokerCertification.runFull` and `DefaultBrokerInspector.inspect` duplicate the probe list

`BrokerCertification` (line 47-55) and `DefaultBrokerInspector` (line 65-107) both call:

- `broker.ltp`
- `broker.quote`
- `broker.depth`
- `broker.ohlc`
- `broker.historical(..., "5m", ...)`
- `broker.expiries`
- `broker.optionChain`
- `broker.optionContracts`
- `broker.balance`
- `broker.positions`
- `broker.holdings`
- `broker.orders`
- `broker.trades`
- `broker.estimateMargin`
- `broker.futuresContracts`
- `broker.isWebSocketConnected`
- `broker.instrumentCount`

That's **17 probes** in common. The two classes have **the same intent** (validate a broker) with **different output schemas** (`CertificationReport` vs `BrokerInspectionReport`). The two classes are not used by the same code path (one is for the certification CLI, the other for the inspector CLI), but they are **redundant code**.

**Required fix:** pick one. If `BrokerInspectionReport` is the more detailed one (which it is — it has per-probe latency), delete `BrokerCertification` and have the certification CLI call `DefaultBrokerInspector.inspect(...)` with a `symbol` and `segment`. If `BrokerCertification` is the more correct one (it has a `CertificationStatus` enum), delete `DefaultBrokerInspector`.

I lean toward keeping `BrokerInspectionReport` and deleting `BrokerCertification`, because the per-probe latency is more useful for debugging.

---

## P2 — Fix in the next quarter

### P2-1 — `ExchangeSegment.fromCode` is O(n); should be a `Map<String, ExchangeSegment>`

`core/src/main/java/com/tradej/core/domain/value/ExchangeSegment.java:28-35`:

```java
public static ExchangeSegment fromCode(String code) {
    for (ExchangeSegment segment : values()) {
        if (segment.name().equalsIgnoreCase(code)) {
            return segment;
        }
    }
    return UNKNOWN;
}
```

Linear scan over a 9-element enum called from hot paths. Trivial cost individually, but the function is called in `HistoricalRangeService.resolveExchangeSegment` (`data/persistence/.../HistoricalRangeService.java:579-588`) and in `GatewayEventBridge` payload building. With 10k ticks/sec, that's 90k string comparisons per second. Replace with a `static final Map<String, ExchangeSegment> BY_CODE = ...` built once in a static initializer.

### P2-2 — `Symbol` record does not normalize; equality is case-sensitive

`core/src/main/java/com/tradej/core/domain/value/Symbol.java:3-14`:

```java
public record Symbol(String value) {
    public Symbol {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Symbol must not be null or blank");
        }
    }
    ...
}
```

`new Symbol("reliance")` and `new Symbol("RELIANCE")` are different records. The instrument catalog probably uses uppercase, so `instruments.resolve("reliance")` will fail. The fix is either:

- Make `Symbol` always store the uppercase form: `value = value.trim().toUpperCase(Locale.ENGLISH)`. This makes the record value-type but breaks any test that asserts the raw input.
- Add `Symbol relaxed(String raw)` that uppercases. Keep `Symbol strict(String uppercase)` for the trusted path.

### P2-3 — `Instruments.java` is a static helper that hardcodes NSE index names; will break on index rename

`core/src/main/java/com/tradej/core/domain/instrument/Instruments.java:6-19` (already flagged in P0-1). Even after P0-1 is fixed, the class is a `static` helper with no way to override a symbol. If NSE renames `BANKNIFTY` to `NIFTY BANK` (unlikely but possible), you have to recompile every module. Replace with a configuration-driven `WellKnownIndices` registry.

### P2-4 — `BrokerDescriptor.capabilities` is `Map<String, Boolean>`; type-unsafe

`broker-gateway/src/main/java/com/tradej/brokergateway/spi/BrokerDescriptor.java:24-30`:

```java
public record BrokerDescriptor(
        BrokerSource source,
        String displayName,
        Map<String, Boolean> capabilities,
        ...
) { ... }
```

The capability keys are `String`s. A typo `"MarketDataProvideer"` compiles and silently always evaluates `false` in `BrokerDescriptor.supports()`. The capability keys are defined as `Class<?>` references elsewhere (in `BrokerExplorer.inspect`, line 41-64). Use the class name as the key consistently, or define an enum:

```java
public enum BrokerCapability {
    MARKET_DATA, OPTIONS, ORDER_COMMAND, ORDER_QUERY, PORTFOLIO, MARGIN,
    INSTRUMENT_RESOLVER, WEBSOCKET_MULTIPLEXER, FUTURES, BRACKET_ORDERS,
    GTT_ORDERS, SLICE_ORDERS, SESSION_RISK, CONDITIONAL_ALERTS, NEWS,
    OPTIONS_CAPABLE, FUTURES_CAPABLE, MARGIN_CAPABLE, ALERT_CAPABLE,
    ADVANCED_ORDER_CAPABLE, NEWS_CAPABLE;
}
```

The descriptor is `Map<BrokerCapability, Boolean>`. The inspect method is `Map<BrokerCapability, Boolean>`. The `BrokerHandle.supports(Class<?>)` can convert the class to the enum key.

### P2-5 — `DuckDbQueryEngine` opens its own `Connection`; duplicates `HistoricalRangeService` connection

`broker-gateway/.../DuckDbQueryEngine.java:54-61` and `data/persistence/.../HistoricalRangeService.java:49-55` both do:

```java
this.connection = DriverManager.getConnection("jdbc:duckdb:" + path);
```

Two different modules open a connection to the same DuckDB file. DuckDB allows **multiple read-only connections** to the same file, but only **one writer** at a time. If `DuckDbQueryEngine` is opened for read while `HistoricalRangeService` is open for write (e.g. a backtest is running and a user opens the inspector), one of them blocks or errors.

**Required fix:** introduce a `DuckDbConnectionFactory` in `data/persistence` (or a new `duckdb-jdbc` module) that:
- Provides `Connection openReadOnly(Path)` and `Connection openReadWrite(Path)`.
- Tracks open connections and refuses a second `openReadWrite` to the same path.
- Routes all `DuckDbQueryEngine` and `HistoricalRangeService` connections through it.

### P2-6 — `DhanBrokerProvider.descriptor()` is hand-maintained; adding a port requires editing every provider

`broker-gateway/.../spi/impl/DhanBrokerProvider.java:31-56` has a `Map.ofEntries(Map.entry("MarketDataProvider", true), ...)` with 15 capabilities. The same 15 capabilities are listed in `BrokerExplorer.inspect` (`broker-gateway/.../explorer/BrokerExplorer.java:42-56`). And the same 15 are listed in `BrokerCertification` (`broker-gateway/.../certification/BrokerCertification.java:47-55`).

If a new port is added to `IBrokerConnection`, three files need updating, in two different files in two different modules, and one of them (`BrokerCertification`) won't even know about it — the certification will silently omit the new port.

**Required fix:** derive the capability list from a `Map<Class<?>, Boolean>` at the descriptor level. Each port interface has a `static final Class<?>...` list. The descriptor is a `Map<Class<?>, Boolean>`. The inspector iterates the list. The certification does the same. Adding a port means adding to the list, and the descriptor / inspector / certification all update automatically.

### P2-7 — `PriceMath.toPaisa` silently rounds sub-paisa inputs to zero

`core/src/main/java/com/tradej/core/domain/value/PriceMath.java:12-17`:

```java
public static long toPaisa(BigDecimal price) {
    if (price == null) {
        return 0L;
    }
    return price.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).longValueExact();
}
```

For `price = new BigDecimal("0.004")`, `price.multiply(100) = 0.4`, `setScale(0, HALF_UP) = 0`. The caller gets `0L` for a price that should round to 0 paisa. That's actually correct. But for `price = new BigDecimal("0.005")`, the result is `1L` paisa — rounded up. For Indian markets, the smallest tradable price increment is **5 paisa** (0.05 INR), so 1 paisa is not a valid price. The function doesn't enforce the tick.

**Required fix:** add a `long toPaisa(BigDecimal price, long tickPaisa)` variant that rounds to the nearest tick. Default to 5 paisa for NSE/BSE, document the choice.

### P2-8 — `MarketDatasource.register` is a free-form SQL injection waiting to happen

`broker-gateway/.../query/DuckDbQueryEngine.java:67-71` accepts a `MarketDatasource` whose `register(connection, name)` is a black box. If a future datasource implementation takes the `name` and concatenates it into a SQL string, the engine is a SQL injection target. The `name` is currently internal (the caller controls it), but the contract is loose.

**Required fix:** define a stricter `MarketDatasource` contract: the `name` is a Java identifier (alphanumeric + underscore), validated at registration. The `register` method is `void` (no return value) and the implementation is expected to use only `name` as a SQL identifier (not a value).

### P2-9 — `BrokerProvider.isEnabled()` is a `default` method; no way to disable at runtime

`broker-gateway/.../spi/BrokerProvider.java:48-50`:

```java
default boolean isEnabled() {
    return true;
}
```

To disable a provider, you have to either:
- Edit the provider source to return `false` and recompile.
- Edit the `META-INF/services` file to comment out the line (but the file format doesn't support comments — you have to delete the line, which means re-adding it to re-enable).

There's no `-D` flag, no env var, no Spring property. The P0-3 fix should add this. Once it's in, this is a P2 cleanup: remove the `isEnabled()` method (always enabled) and rely on the config flag.

---

## Recommendations (concrete, ordered)

| # | Action | Module | Effort | Impact |
|---|---|---|---|---|
| 1 | Fix `Instruments.bankNifty()` to return `"BANKNIFTY"` (P0-1) | `core/domain/instrument` | 0.25d | High — order routing correctness |
| 2 | Add `tryNormalize` and a parser to `ContractSymbolNormalizer` (P0-2) | `core/domain/instrument` | 0.5d | High — silent-error prevention |
| 3 | Delete `IciciBrokerProvider` and its SPI entry (P0-3) | `broker-gateway` | 0.1d | Low — cleanup |
| 4 | Make `BrokerProvider.connect` lazy / two-phase (P0-4) | `broker-gateway` | 1d | High — startup correctness |
| 5 | Add transactional `MarketDatasource.register` (P0-5) | `broker-gateway` | 0.5d | Medium — init correctness |
| 6 | Split `BrokerInspectionReport.capabilities` into ports + markers (P0-6) | `broker-gateway` | 0.25d | Low — display correctness |
| 7 | Per-exchange `ContractSymbolParser` (P1-1) | `core/domain/instrument` | 2d | Medium — future-proofing |
| 8 | Parallelize + time-budget the inspector (P1-2) | `broker-gateway` | 2d | Medium — CI cron |
| 9 | Delete `BrokerCertification` (P1-4) | `broker-gateway` | 0.5d | Low — code dedup |
| 10 | Centralize capability list (P2-4, P2-6) | `broker-gateway` | 1d | Medium — drift prevention |

Total: **~8 person-days** for the P0 + P1 set.

---

## Closing thought

The market-utility layer is mostly honest code with **one bad symbol mapping** (`Instruments.bankNifty`) that will misroute real orders. Fix that today. The plugin layer is structurally sound but **redundantly coded**: three places list the same capabilities, two of them hard-code the count. Fix the duplication in the next refactor, and the SPI will start to look like a real extension point instead of a fashion statement.

The "explorer + certification + query engine" sub-modules in `broker-gateway` are **smoke-test tooling masquerading as a runtime surface**. They make sense as a CLI tool. They don't make sense as a hot path. If a future task says "make the gateway startup faster" or "make the broker test pass in CI", these are the classes that will be in the way.

See also:
- `docs/reports/ARCHITECTURE_REVIEW_2026-06-06.md` — P0-2 / dedup is relevant to `GatewayEventBridge` (parallel layer)
- `docs/reports/REACTIVE_ADOPTION_REVIEW_2026-06-06.md` — R-1 (GatewayEventBridge) is the right first step
- `docs/reports/BROKER_GATEWAY_ARCHITECTURE_REVIEW_2026-06-06.md` — `LoadBalancedBrokerGateway` is the parallel capability-detection source
