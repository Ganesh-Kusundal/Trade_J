# Trade-J CLI (`cli`)

Interactive and non-interactive operator surface for Trade-J. Uses two backends:

| Backend | When | Examples |
|---------|------|----------|
| **Attach** | `app` running on HTTP | `status`, `runtime`, read-model, historical, replay, kill-switch, scan |
| **Standalone** | Broker credentials in `config/` | `quote`, `chain`, `balance`, sandbox `place` |

## Quick start

```bash
# Interactive REPL (default when no subcommand)
./scripts/tradej interactive

# Or via Gradle
./gradlew :cli:run --args='interactive'

# Non-interactive attach checks (app on :8080)
./scripts/tradej status
./scripts/tradej runtime --attach http://127.0.0.1:8080

# Standalone Dhan (live profile, config/dhan-local.properties)
./scripts/tradej quote NIFTY IDX_I
./scripts/tradej balance

# Standalone Upstox analytics (config/upstox-live.properties)
./scripts/tradej --broker upstox ltp SBIN NSE_EQ

# Sandbox orders only
./scripts/tradej --profile sandbox place RELIANCE NSE_EQ --side BUY --qty 1 --type LIMIT --price 250000
```

## Global flags

| Flag | Description |
|------|-------------|
| `--attach URL` | app base URL (default `http://127.0.0.1:8080` or `TRADEJ_ATTACH_URL`) |
| `--broker dhan\|upstox` | Standalone broker backend (default `dhan`, or `TRADEJ_BROKER`) |
| `--profile live\|sandbox` | Dhan profile; Upstox loads `upstox-live` or `upstox-sandbox` properties |
| `--json` | Machine-readable JSON output |
| `-y`, `--yes` | Skip confirmation prompts |

## Interactive menu

Run `./scripts/tradej` or `./scripts/tradej interactive`. Main menu:

| Key | Action |
|-----|--------|
| 1 | Quick status |
| 2 | Runtime & pipeline |
| 3 | Orders & positions |
| 4 | Risk & reconcile |
| 5 | Portfolio & PnL |
| 6 | Broker / market |
| 7 | Options & derivatives |
| 8 | Historical & replay (attach) |
| 9 | Scan (attach) |
| 10 | Verify & maintenance |
| 11 | Sandbox orders |
| s | Settings — change attach URL, profile, broker without exit |
| 0 | Exit |

## Command groups

### Attach (runtime)

- `status` — health + summary + runtime
- `runtime`, `pipeline`, `strategies`, `summary`
- `orders`, `positions`, `read-model`
- `scan run|list` — intraday scan profiles (including `option-liquidity` for per-contract ranking)
- `historical stats|candles|ticks|orders|fills --symbol X --from MS --to MS`
- `replay ticks|candles|fills|orders|chronicle` — blocked when `trade.runtime.mode=LIVE` in `application.yml`
- `kill-switch on|off --confirm` — LIVE requires confirmation
- `reconcile '{"SYMBOL":1}'`

### Standalone (broker)

- `catalog refresh [--force]` — download instrument master (`runtime/cli-instruments/` or `runtime/cli-instruments-upstox/`)
- `ltp`, `quote`, `depth`, `ohlc`, `candles` (auto-load catalog if missing)
- `balance`, `holdings`, `broker-positions`, `live-pnl` (not available with Upstox analytics-only token)
- `orderbook`, `trades`, `order <id>`
- `expiries`, `chain`, `strike`, `margin`, `rolling-option` (Dhan rolling opt-in: `DHAN_ROLLING_OPTION_TEST_ENABLED=true`)
- `options-scan UNDERLYING [SEGMENT]` — rank CE/PE legs by OI, volume, and bid-ask spread (standalone or attach)
- `stream-read-model --seconds 30` (attach; SSE sample)
- `place`, `cancel`, `modify` — **sandbox profile only**

### Verify

- `token` — runs `scripts/refresh-dhan-token.sh`
- `test unit|broker-rest|preflight|full-regression`

## Safety

- Destructive attach actions require `YES` confirmation unless `--yes`.
- Replay refuses when configured runtime mode is `LIVE`.
- Sandbox order commands refuse on `--profile live`.
- Upstox analytics token supports market data only (no orders/portfolio).
- Upstox expired option history is available via Spring API only in v1 (`GET /api/v1/market/expired-options/*` with `upstox-analytics` profile); no CLI `download` yet.
- No mocked market data; all calls hit broker REST or the running app.

## Manual integration checklist

1. Start `app` (`./gradlew :app:bootRun`).
2. `./scripts/tradej status` — attach ✓, health UP.
3. `./scripts/tradej orders` — read-model tables render.
4. Stop app; `./scripts/tradej quote NIFTY IDX_I` — standalone Dhan LTP.
5. `./scripts/tradej --broker upstox ltp SBIN NSE_EQ` — Upstox analytics LTP (requires `config/upstox-live.properties`).
6. `./scripts/tradej --profile sandbox balance` — sandbox fund limit.
7. In interactive mode: `s` → change broker/profile; `7` → `liquidity-scan`; `9` → scan run (with app up).
8. `./scripts/tradej --broker dhan options-scan NIFTY IDX_I --top 10` — ranked option contracts.
9. `./scripts/tradej test unit` — CLI unit tests pass.

See [TESTING.md](TESTING.md) for Gradle tasks and regression alignment.
