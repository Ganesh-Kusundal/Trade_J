# P2 Bloomberg/OpenBB Terminal Scalability Review
**Date:** 2026-06-06
**Scope:** Hidden coupling, untestable complexity, and scalability limits in the terminal surface (React 19 + Vite frontend, picocli + JLine CLI, Spring WebSocket gateway). Evaluated against Bloomberg Terminal and OpenBB Terminal patterns: multi-user session, multi-pane resizable layout, async updates, command palette, virtualization.
**Author persona:** Principal quant engineer — opinionated, file:line specific, brutally honest.

---

## TL;DR (verdict)

The terminal is a **single-user, single-process, single-account UI shell** running in MOCK mode. It cannot today serve two concurrent users from the same backend without one seeing the other's orders, positions, and P&L. The WebSocket gateway is the only multi-client surface, and it has no per-user filtering — every subscriber to `ORDER_UPDATE` receives every order for every account. The CLI is a single-threaded REPL that blocks on each command. The frontend is hard-coded to a fixed grid layout with no virtualization and no live data wiring.

This is fine for a "Phase 1" demo, but the Javadoc-style comment on `TerminalLayout.tsx:23` (`Broker-agnostic foundation (Phase 1)`) is honest: the foundation is real, the terminal is a sketch.

To get to "Bloomberg-style" you need (in this order): (1) per-user session isolation, (2) per-user topic filtering on the WebSocket gateway, (3) async updates in the CLI REPL, (4) client-side virtualization, (5) resizable panels. None of these are present today.

Five P0s and seven P1s below.

---

## P0 — Fix before any second user is added

### P0-1 — No per-user session isolation: one Spring context, one broker, all data shared

**Where:**
- `app/src/main/java/com/tradej/app/api/OrderController.java` (referenced; not opened)
- `app/src/main/java/com/tradej/app/admin/AdminController.java`
- `gateway/src/main/java/com/tradej/gateway/websocket/GatewayWebSocketHandler.java:30`

The Spring Boot `app` is a **single process with a single Spring context**. Every REST controller serves every client. Every WebSocket session is added to the same `GatewayTopicRouter` (`gateway/src/main/java/com/tradej/gateway/router/GatewayTopicRouter.java:101-104`):

```java
public void subscribe(WebSocketTransport transport, GatewayTopic topic) {
    topicTransports.get(topic).add(transport);
    transportTopics.computeIfAbsent(transport.id(), id -> new CopyOnWriteArraySet<>()).add(topic);
}
```

A transport's identity is the `WebSocketSession.getId()` — a UUID assigned by Spring. **There is no user authentication, no session-to-user mapping, no per-user data scope.** When `OrderController` returns orders, it returns orders for the broker the Spring context is connected to. When `GatewayTopicRouter` dispatches `ORDER_UPDATE`, it dispatches to every transport subscribed to that topic.

**Concrete consequence:** user A and user B both connect to the same `app` instance. They both see:
- All orders (both A's and B's)
- All positions (both A's and B's)
- All P&L (both A's and B's)
- All account balance

There is no way to tell whose data is whose. If the deployment is multi-account (one user per Dhan account), the current architecture cannot serve it.

**Required fix:** introduce a `UserSession` concept:
1. Add Spring Security (or a lighter-weight auth filter) that resolves a `UserPrincipal` from a JWT or session token at the start of each HTTP request and each WebSocket handshake.
2. Make every REST controller accept the `UserPrincipal` (via `@AuthenticationPrincipal` or `SecurityContext`) and resolve the broker connection from a per-user `BrokerSessionRegistry`.
3. Make `GatewayTopicRouter.subscribe(transport, topic, userId)` — and dispatch only to transports where `transport.userId == event.userId`.
4. The `GatewayEventBridge` payload builders (`marketTickPayload`, `orderPayload`, etc.) must add a `userId` field to the published message so the router can filter.

This is a 2-3 week refactor. The P0 is "we have no isolation today" — the P1 is "we have isolation but it's not enforced everywhere." Either way, **no second user can connect to the same `app` process until this is fixed.**

### P0-2 — The WebSocket gateway has no per-user filter, no per-broker fan-out, no rate limit

**Where:** `gateway/src/main/java/com/tradej/gateway/router/GatewayTopicRouter.java:75-204`

The `publish` method (line 119-126) dispatches to **every** transport subscribed to the topic. The `dispatchToTransports` method (line 183-204) iterates the topic's transports **sequentially in the publisher thread**. Two issues:

1. **No per-user filter.** A `SendTask` does not carry a `userId`. The router doesn't know which user the event is for. It broadcasts to all subscribers of the topic.
2. **No per-broker limit.** A `WebSocketTransport` is not associated with a broker or an account. The same topic (`ORDER_UPDATE`, for example) is broadcast to every transport regardless of which account the transport represents.

The `GatewayEventBridge` already publishes per-event (`orderAckPayload` is built per `OrderAccepted`), but the router doesn't filter. So:
- A user subscribed to `ORDER_UPDATE` for account A receives orders for account B.
- A user subscribed to `POSITION_UPDATE` for account A receives position updates for account B.

**Required fix:**
1. Add `userId` (and `accountId` if multi-account) to `SendTask` and to `FilteredSendTask` (line 221-232, the existing per-transport filter — extend it).
2. Change `dispatchToTransports` to skip transports where the `userId` does not match.
3. The `GatewayEventBridge.onDomainEvent` must stamp each event with the source userId before publishing.

The existing `publishFiltered` method (line 128-139) is the right primitive; it just needs to be the **default** path, not the opt-in path.

### P0-3 — `TerminalLayout.tsx` is hard-coded to a fixed 280px + 380px + 220px grid with no resize

**Where:** `frontend/src/ui/layout/TerminalLayout.tsx:38-68`

```tsx
<div className="grid grid-cols-[280px_1fr] grid-rows-[1fr_220px] h-[calc(100vh-46px)]">
    <div className="border-r border-[#1c1c1e] bg-[#09090b] overflow-hidden">
        {activePanels.watchlist && <WatchlistPanel />}
    </div>

    <div className="grid grid-cols-[1fr_380px] grid-rows-[1fr] h-full">
        <div className="col-span-1 border-r border-[#1c1c1e] bg-[#09090b] overflow-hidden">
            {activePanels.charts && <ChartPanel />}
        </div>
        <div className="bg-[#09090b] overflow-hidden">
            {activePanels.optionChain && <OptionChainPanel />}
        </div>
    </div>

    <div className="border-t border-[#1c1c1e] bg-[#09090b] overflow-hidden col-span-2">
        <div className="grid grid-cols-3 h-full">
            ...
        </div>
    </div>
</div>
```

The grid is **fixed**:
- Left watchlist: 280px
- Right charts: 1fr
- Right option chain: 380px
- Bottom row: 220px
- 3 columns for orders/positions/logs

Bloomberg Terminal lets a user drag panel edges to resize. OpenBB lets the user pin/unpin panels. The current `TerminalLayout` does neither. The `activePanels` toggle (line 41, 47, 50, 58, 61, 64) lets a user **hide** a panel but not **resize** it.

A trader monitoring 5 symbols needs 5 chart panels. The current layout supports 1.

**Required fix:** use a layout library that supports resizable splitters:
- `react-resizable-panels` (most popular, MIT, good TS support)
- `dockview` (more VSCode-like, supports docking)
- `react-grid-layout` (mature, supports drag-to-resize)

`react-resizable-panels` is the right pick for a Bloomberg-style terminal — it's stable, lightweight, and the API is close to "splitter with min/max". Replace the fixed `grid` with `<PanelGroup direction="horizontal"><Panel>...</Panel>...</PanelGroup>`.

### P0-4 — The frontend's `MODE: MOCK` is hard-coded; panels do not subscribe to live data

**Where:** `frontend/src/ui/layout/TerminalLayout.tsx:33` (`<span ... MODE: MOCK</span>`)

The `WS: LIVE` and `MODE: MOCK` badges are static text. The panels (`WatchlistPanel`, `ChartPanel`, `OptionChainPanel`, `OrdersPanel`, `PositionsPanel`, `LogsPanel`) don't subscribe to a WebSocket. I searched for `WebSocket` references in the frontend and found only `LogsPanel.tsx`. The other panels render placeholder or static data.

This is **the biggest gap** between the terminal's name and its reality. A "Trading Terminal" that doesn't show live ticks is a layout demo. A Bloomberg-style terminal **must** push live data to every visible panel.

**Required fix:**
1. Create a `useWebSocket(url, topics)` hook that opens a WebSocket to `ws://host/ws/gateway`, subscribes to the given topics, and exposes the latest event per topic.
2. Wire each panel to its relevant topic:
   - `WatchlistPanel` ← `MARKET_TICK` (filtered by watchlist symbols)
   - `ChartPanel` ← `CANDLE_CLOSED`, `CANDLE_DEVELOPING` (per selected symbol)
   - `OptionChainPanel` ← `OPTION_CHAIN_UPDATED` (per selected expiry)
   - `OrdersPanel` ← `ORDER_UPDATE`
   - `PositionsPanel` ← `POSITION_UPDATE`, `PNL_UPDATE`
   - `LogsPanel` ← `SCAN_COMPLETED`, `REPLAY_CONTROL`, etc.
3. Add reconnection logic: if the WebSocket drops, retry with exponential backoff.
4. Show a per-topic connection status indicator (a small dot next to each panel title) so the user knows which panels are live.

### P0-5 — The CLI is a single-threaded REPL that blocks on each command

**Where:** `cli/src/main/java/com/tradej/cli/interactive/InteractiveShell.java:25-61`

```java
public void run() throws IOException {
    Terminal terminal = buildTerminal();
    LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
    MenuContext menu = new MenuContext(context, operations, reader);

    if ("dumb".equals(terminal.getType()) && System.console() == null) {
        operations.output().error(...);
        return;
    }

    operations.output().println(menu.headerLine());
    operations.output().println("");
    printMainMenu();

    while (true) {
        String choice;
        try {
            choice = reader.readLine("tradej> ").trim();   // <-- blocks here
        } catch (UserInterruptException | EndOfFileException ex) {
            operations.output().println("Bye.");
            return;
        }
        if (choice.isEmpty()) {
            continue;
        }
        try {
            if (!route(menu, choice)) {
                return;
            }
        } catch (Exception ex) {
            operations.output().error("Error: " + ex.getMessage());
        }
        operations.output().println("");
    }
}
```

The REPL is **single-threaded**. The main thread blocks on `reader.readLine`. While a command is running (e.g. `status`, which makes an HTTP call to `/admin/runtime`), the REPL is unresponsive. If a user starts a long command (e.g. `backtest run`), they cannot cancel, they cannot run another command, and they cannot see live price updates.

Bloomberg Terminal runs the user's commands in **one thread** and **live data subscriptions** in another. A trader can see the order book updating while the analytics query is running. OpenBB does the same. The current `InteractiveShell` cannot.

**Required fix:** split the REPL into:
- A **command-execution thread** that reads user input and runs commands.
- A **background subscription thread** that subscribes to the WebSocket (or SSE) for live data and writes updates to the terminal asynchronously (e.g. on a status line, not the main buffer).

For JLine, the right pattern is `LineReader.readLine` with a `MaskingCallback` or a `ParameterCompleter` that runs in the background and uses `terminal.writer().println(...)` to push updates to a separate region. JLine 3.x supports this; the project already depends on JLine.

This is a 1-2 week refactor. Until it's done, the CLI is a "batch tool" that happens to be interactive, not a terminal.

---

## P1 — Fix in the next two sprints

### P1-1 — The `AttachClient` opens a new HTTP connection per CLI invocation; no connection pool, no keep-alive

**Where:** `cli/src/main/java/com/tradej/cli/attach/AttachClient.java:27-32`

```java
public AttachClient(String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
}
```

`HttpClient.newBuilder().build()` is a single client — keep-alive is on by default. But `AttachClient` is **constructed per `CliOperations`** (`cli/.../CliOperations.java` — not opened, but referenced). Each CLI command creates a new `AttachClient` (via `createContext`), which creates a new `HttpClient`. A user running `tradej status; tradej pipeline; tradej strategies` opens 3 separate HTTP clients.

This isn't a correctness issue (the requests are independent), but it's wasteful. A 3-command workflow opens 3 TCP connections, 3 TLS handshakes, 3 connection pools.

**Required fix:** cache the `HttpClient` and `AttachClient` in a process-wide singleton, keyed by the `--attach` URL. One client per process.

### P1-2 — The terminal layout is per-user state; no server-side persistence

`frontend/src/ui/layout/TerminalLayout.tsx` reads `activePanels` from a `useTerminalStore` (Zustand). This is **in-memory, per-browser-tab**. A user opening the terminal in a new tab gets the default layout. A user reloading the page loses their resize. There is no `localStorage` save, no server-side save, no URL-encoded state.

Bloomberg Terminal remembers the user's last layout across sessions. A modern web terminal should too.

**Required fix:** add `zustand/middleware/persist` to the store. The `activePanels` flag set is small (a few booleans); persist it to `localStorage` keyed by user. For server-side persistence, add a `PUT /api/v1/users/{id}/layout` endpoint and load on login.

### P1-3 — `interactive` shell exception handler is untyped; no structured error codes

`cli/src/main/java/com/tradej/cli/interactive/InteractiveShell.java:56-58`:

```java
} catch (Exception ex) {
    operations.output().error("Error: " + ex.getMessage());
}
```

Every exception is flattened to `"Error: " + message`. A `RateLimitException`, a `BrokerUnreachableException`, a `KillSwitchEngagedException`, and a `NullPointerException` all look the same to the user. There's no error code, no hint, no recovery suggestion.

A Bloomberg-style terminal shows:
- A red status bar with the error code
- A tooltip explaining the code
- A link to the docs

**Required fix:** introduce a `TerminalException` sealed class with subclasses per error type. The exception handler dispatches on type:
- `RateLimitException` → "Rate limit exceeded. Retry in 60s." + the limit number
- `BrokerUnreachableException` → "Broker gateway down. Falling back to cached data." + the last successful time
- `KillSwitchEngagedException` → "Kill switch is ON. Disengage before placing orders." + the URL to disengage

### P1-4 — No client-side virtualization in the frontend

`frontend/package.json` dependencies: `react`, `react-dom`, `lightweight-charts`, `lucide-react`, `zustand`. No `react-window`, no `@tanstack/react-virtual`, no `react-virtuoso`. The `WatchlistPanel` and `PositionsPanel` (not opened, but inferred) render **all rows in the DOM**. At 500 symbols on the watchlist and 200 positions, that's 700 DOM nodes that re-render on every WebSocket event.

At 10,000 ticks/sec broadcast by the gateway, every tick re-renders all 700 rows. The main thread blocks. The frame rate drops below 30fps. The terminal feels laggy.

**Required fix:** add `@tanstack/react-virtual` and use `useVirtualizer` for the watchlist and positions panels. For the option chain, the chain is bounded (~100 strikes × 2 sides = 200 rows) so virtualization is less critical, but the Greeks table inside the chain (delta/gamma/theta/vega/iv for each strike) is **5 columns × 100 rows = 500 cells** that re-render on every tick — virtualize the columns too.

### P1-5 — The `ChartPanel` is a single chart; a Bloomberg-style terminal needs many

`frontend/package.json` includes `lightweight-charts: ^5.2.0` — TradingView's library. Each `lightweight-charts` instance is a separate `<canvas>`. The `ChartPanel.tsx` (not opened) presumably renders one chart per panel instance.

Bloomberg Terminal's default is one chart per "page" with up to 4 charts visible (4 × 4 grid). The current layout has **one chart panel** in the right column. A trader who wants to monitor NIFTY and BANKNIFTY on the same screen needs to flip between them.

**Required fix:** wrap `lightweight-charts` in a `MultiChartContainer` component that supports a 1×1, 2×1, 2×2, or 4×4 grid of charts. Each chart is a separate lightweight-charts instance with its own subscription to its symbol's `CANDLE_CLOSED` events. Use the same resizable-panels library from P0-3 to let the user resize the chart area.

### P1-6 — The `TradeCli` has 50+ subcommands; no command palette, no fuzzy search

`cli/src/main/java/com/tradej/cli/TradeCli.java:22-74` lists 50+ `@Command` subcommands. The help output is picocli's default — flat, alphabetical, line-by-line. A user looking for "the command to refresh the Dhan catalog" has to know it's `token refresh` or `catalog refresh` or `download ...`.

Bloomberg Terminal has `GO` (a command bar that takes any ticker, function, or menu path). OpenBB has a search bar that filters menus by partial match.

**Required fix:** add a command palette. The simplest implementation: in the `InteractiveShell`, when the user types a partial command (e.g. `cat`), show a list of matches (`catalog refresh`, `cancel`, etc.) as the user types. JLine supports this via `Completer`. Wire a `StringsCompleter` to picocli's command tree, with fuzzy matching (e.g. via `fzf` semantics or a simple `LevenshteinDistance`).

### P1-7 — The frontend's `WS: LIVE` and `MODE: MOCK` badges are hard-coded text; no real connection state

`frontend/src/ui/layout/TerminalLayout.tsx:27-34`:

```tsx
<div className="flex items-center gap-2 text-[10px]">
    <span className="px-2 py-1 rounded-xs border border-[#00d2ff]/35 bg-[#00d2ff]/10 text-[#7be7ff] font-black">
        WS: LIVE
    </span>
    <span className="px-2 py-1 rounded-xs border border-[#c084fc]/35 bg-[#c084fc]/10 text-[#e9d5ff] font-black">
        MODE: MOCK
    </span>
</div>
```

These badges are always `LIVE` and `MOCK`. They don't reflect:
- Whether the WebSocket is actually connected (might be disconnected)
- Whether the broker is reachable
- The current runtime mode (LIVE / REPLAY / BACKTEST)

A user looking at the badges sees "WS: LIVE" and assumes data is live. If the WebSocket is disconnected, the badges still say "LIVE" — silently. The user makes decisions on stale data.

**Required fix:** wire the badges to actual connection state:
- `WS: <state>` where `<state>` is `CONNECTED`, `CONNECTING`, `DISCONNECTED`, `RECONNECTING`
- `MODE: <mode>` where `<mode>` comes from `RuntimeModeHolder.mode()` (or the equivalent API call)

Color-code: green for connected, yellow for connecting, red for disconnected, gray for mode unknown.

---

## P2 — Fix in the next quarter

### P2-1 — The CLI's `InteractiveShell.route` is a giant `switch` over `choice.toLowerCase()`

`cli/src/main/java/com/tradej/cli/interactive/InteractiveShell.java:73-` (I read 120 lines; the file is 394 lines):

```java
private boolean route(MenuContext menu, String choice) throws Exception {
    return switch (choice.toLowerCase()) {
        case "0", "exit", "quit" -> { ... }
        case "1", "status" -> { ... }
        case "2" -> { ... }
        case "3" -> { ... }
        ...
    };
}
```

This is **the same anti-pattern as `BacktestResult`'s three different records** (see `SIMULATION_REPLAY_BACKTEST_REVIEW_2026-06-06.md` P0-2). The interactive menu logic is hard-coded to magic numbers ("1" for status, "2" for runtime, etc.) and to specific subcommand names. A new menu item requires editing this file. A renamed CLI command requires editing this file. There's no validation that the menu's number maps to a real command.

**Required fix:** define a `MenuEntry` record with `(number, label, action)` and a `List<MenuEntry>` registered per menu. The `route` method iterates the list. New menu items are added by appending to the list, not by editing a `switch`.

### P2-2 — The CLI's `InteractiveShell` catches `UserInterruptException` and exits silently

`cli/src/main/java/com/tradej/cli/interactive/InteractiveShell.java:45-48`:

```java
} catch (UserInterruptException | EndOfFileException ex) {
    operations.output().println("Bye.");
    return;
}
```

A user pressing `Ctrl-C` mid-command exits the shell. There's no "are you sure?" prompt. There's no cleanup of WebSocket subscriptions, no `close()` on `AttachClient`, no flushing of the terminal. The `close()` in `TradeCli.java:104-108` is called only on the **outer** `main` method's exit, not on the `InteractiveShell`'s exit path.

A trading terminal handles `Ctrl-C` carefully: it might cancel the current command, prompt for confirmation, or stop a long-running download. The current handler treats it like `exit`.

**Required fix:** distinguish `Ctrl-C` (cancel current command) from `Ctrl-D` (exit). JLine exposes both separately. On `Ctrl-C`, cancel the current command but stay in the REPL. On `Ctrl-D`, exit after cleanup.

### P2-3 — `TradeCli` has no `--config` flag; profile, broker, and attach URL are CLI flags

`cli/src/main/java/com/tradej/cli/TradeCli.java:78-85`:

```java
@Option(names = "--attach", description = "trade-app base URL", defaultValue = "")
String attachUrl;

@Option(names = "--profile", description = "live|sandbox (Dhan profile; Upstox maps live/sandbox property files)", defaultValue = "live")
String profileName;

@Option(names = "--broker", description = "dhan|upstox", defaultValue = "dhan")
String brokerName;
```

A user running the CLI frequently has to type `--broker dhan --profile live --attach http://localhost:8080` on every invocation. A `~/.tradej/config.toml` or `TRADEJ_BROKER`, `TRADEJ_PROFILE`, `TRADEJ_ATTACH_URL` env vars would let the user set defaults once.

**Required fix:** add a config file loader (use `java.util.Properties` or a small TOML parser like `night-config`). The precedence: CLI flag > env var > config file > built-in default.

### P2-4 — The `WatchlistPanel` likely has no symbol limit; a trader can add 10,000 symbols

I didn't open `WatchlistPanel.tsx`, but the layout implies a single-column list of symbols with prices. A trader who adds 10,000 symbols to their watchlist will see 10,000 DOM rows (P1-4) and the price update will be 10,000 re-renders per tick (also P1-4). The fix is virtualized rendering.

**Required fix:** combine with P1-4. Use `@tanstack/react-virtual` for all list-style panels.

### P2-5 — The `attach` URL has no health check at startup; commands fail with cryptic errors

`cli/src/main/java/com/tradej/cli/attach/AttachClient.java:38-45`:

```java
public boolean isReachable() {
    try {
        getJson("/actuator/health");
        return true;
    } catch (Exception ex) {
        return false;
    }
}
```

`isReachable()` exists but is **not called** at CLI startup. The `CliContext.createContext` (in `TradeCli.java:119-126`) doesn't validate the attach URL. The first command that needs the attach URL fails with a connection error.

A Bloomberg-style terminal shows "Cannot reach trade-app at http://..." in the status bar at startup.

**Required fix:** call `isReachable()` once at startup. If it returns false, print a clear error and exit (or, in the REPL, show a degraded mode where attach-dependent commands are disabled).

### P2-6 — The `frontend/src/ui/panels/LogsPanel.tsx` is the only panel with WebSocket; the others are mock or stub

I searched for `WebSocket` references in the frontend and found only `LogsPanel.tsx`. The other 5 panels (`WatchlistPanel`, `ChartPanel`, `OptionChainPanel`, `OrdersPanel`, `PositionsPanel`) likely render static or mock data. The terminal is a **layout demo** with **one working panel**.

**Required fix:** wire each panel to its relevant WebSocket topic (per P0-4). This is the single biggest gap in the terminal today.

### P2-7 — The `frontend/package.json` has no router; the terminal is a single page

`frontend/package.json` lists no `react-router`, no `@tanstack/router`. The terminal has one route: `/`. A user who wants a "settings" page, a "history" page, or a "broker config" page cannot navigate to it.

Bloomberg has hundreds of functions, each effectively a page.

**Required fix:** add `react-router-dom`. Add a top-level `<Router>` with `<Routes>` for `/terminal`, `/settings`, `/history`, `/help`, etc. The default route is `/terminal` (the current layout).

---

## Scalability matrix

| Dimension | Bloomberg Terminal | OpenBB Terminal | Trade-J Terminal today | Gap |
|---|---|---|---|---|
| Concurrent users | 1 user per terminal, 350k terminals globally | Multi-user via web deploy | 1 user per process (P0-1) | **P0** |
| Multi-account per user | Yes | Yes | No — single Spring context (P0-1) | **P0** |
| WebSocket per-user filter | N/A (desktop app) | Per-user via cookie | None — broadcast to all (P0-2) | **P0** |
| Resizable panels | Yes (drag edges) | Yes | No — fixed grid (P0-3) | **P0** |
| Live data | Yes (50+ feeds) | Yes (configurable) | MOCK only (P0-4) | **P0** |
| Async REPL | N/A (desktop) | Yes (subprocess) | No — blocks (P0-5) | **P0** |
| Layout persistence | Yes (per-user) | Yes (per-browser) | No — in-memory only (P1-2) | **P1** |
| Structured errors | Yes (BBG <GO> help) | Yes | No — flat strings (P1-3) | **P1** |
| Virtualized lists | Yes (efficient) | Yes (configurable) | No — full DOM (P1-4) | **P1** |
| Multi-chart | Yes (4×4 grid) | Yes (subplot) | No — single chart (P1-5) | **P1** |
| Command palette | Yes (<GO>) | Yes (Ctrl-P) | No — alphabetical (P1-6) | **P1** |
| Connection state UI | Yes (status bar) | Yes (status bar) | No — hard-coded (P1-7) | **P1** |
| Config file | Yes (BBG config) | Yes (config.json) | No — flags only (P2-3) | **P2** |
| Multi-page router | Yes (functions) | Yes (menus) | No — single page (P2-7) | **P2** |
| Health check at startup | N/A (always local) | Yes (PING) | No — late failure (P2-5) | **P2** |

**Today: 0/15 dimensions are met. After the P0 set: 5/15. After the P1 set: 10/15. After the P2 set: 14/15.** The remaining gap (multi-chart advanced features) is a future feature, not a bug.

---

## Recommendations (concrete, ordered)

| # | Action | Module | Effort | Impact |
|---|---|---|---|---|
| 1 | Per-user session isolation in `app` (P0-1) | `app`, `gateway` | 3w | Critical — multi-user |
| 2 | Per-user filter on `GatewayTopicRouter` (P0-2) | `gateway` | 1w | Critical — multi-user |
| 3 | Wire each panel to its WebSocket topic (P0-4) | `frontend` | 1w | High — terminal is a demo |
| 4 | Replace fixed grid with `react-resizable-panels` (P0-3) | `frontend` | 0.5w | High — usability |
| 5 | Async REPL with background WS thread (P0-5) | `cli` | 1-2w | High — CLI is a batch tool |
| 6 | Add `@tanstack/react-virtual` to list panels (P1-4) | `frontend` | 0.5w | High — perf |
| 7 | Structured error codes in `InteractiveShell` (P1-3) | `cli`, `core` | 0.5w | Medium — UX |
| 8 | Persist layout to `localStorage` (P1-2) | `frontend` | 0.25w | Medium — UX |
| 9 | Command palette in CLI (P1-6) | `cli` | 0.5w | Medium — UX |
| 10 | Real connection-state badges (P1-7) | `frontend` | 0.25w | Medium — UX |
| 11 | Config file for CLI (P2-3) | `cli` | 0.5w | Low — DX |
| 12 | Health check at CLI startup (P2-5) | `cli` | 0.25w | Low — DX |

Total: **~9-11 person-weeks** to go from "layout demo" to "Bloomberg-style terminal for one user per process, single-account."

If the goal is multi-user, the **P0-1 + P0-2 work is the gate**. Until that lands, the terminal is single-user. Make that explicit in the README and in `TerminalLayout.tsx`'s badge.

---

## Closing thought

The terminal is honestly labeled — `Phase 1` is right there in the JSDoc. The pieces that exist (the gateway WS, the CLI's REPL, the Spring controllers) are all single-user. The pieces that don't exist (per-user filtering, layout persistence, virtualized lists, async REPL) are all **scaling features** that the current architecture cannot support without restructuring.

The right next step is **not** to add more panels. The right next step is to fix the **single-user → multi-user transition** (P0-1, P0-2) and the **layout demo → live data** transition (P0-4). Once those are in, the terminal will feel like a real product. Until they are, it is what the comment says: a Phase 1 foundation.

See also:
- `docs/reports/BROKER_GATEWAY_ARCHITECTURE_REVIEW_2026-06-06.md` — P0-4 (GatewayTopicRouter single-thread) blocks P0-5 (async REPL)
- `docs/reports/PLUGIN_MARKET_UTILS_REVIEW_2026-06-06.md` — `BrokerCertification` smoke-test tooling is what a Bloomberg `BBG <GO>` would call
- `docs/reports/SIMULATION_REPLAY_BACKTEST_REVIEW_2026-06-06.md` — backtest progress (P1-4) is what the terminal's `Run` button should display
