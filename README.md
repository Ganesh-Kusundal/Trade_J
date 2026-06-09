# Trade-J

Java 21 trading platform: live market data, OMS, risk, strategy plugins, and broker adapters (Dhan, Upstox).

## Quick start

```bash
# Default: Dhan sandbox
./gradlew :app:bootRun

# Live market data
./gradlew :app:bootRun --args='--spring.profiles.active=dev-live'

# Upstox analytics-only (REST market data)
./gradlew :app:bootRun --args='--spring.profiles.active=upstox-analytics'
```

Copy credential examples from `config/*.example` into gitignored property files (see [CONFIG.md](CONFIG.md)).

## Documentation

| Doc | Purpose |
|-----|---------|
| [CONFIG.md](CONFIG.md) | Spring profiles, credentials, broker routing |
| [TESTING.md](TESTING.md) | Test pyramid, Gradle tasks, regression |
| [REGRESSION_MANIFEST.md](REGRESSION_MANIFEST.md) | Test → invariant mapping |
| [CLI.md](CLI.md) | `cli` / `scripts/tradej` |
| [TRADEHULL_PARITY.md](TRADEHULL_PARITY.md) | Dhan capability matrix |
| [CONTRACT_NAMING.md](CONTRACT_NAMING.md) | Symbol normalization |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Full architecture, module layout, diagrams, and runtime flows |

## Module layout

| Folder | Modules |
|--------|---------|
| `core/` | Domain events, ports, pipeline graph types |
| `broker/` | `api`, `core`, `dhan`, `upstox` |
| `runtime/` | `disruptor`, `hotpath` |
| `trading/` | `strategy`, `execution`, `scanner`, `institutional-scanner`, `indicators`, `simulation` |
| `data/` | `persistence`, `feature-store`, `historical-ingest`, `analytics` |
| `app/`, `gateway/`, `cli/` | Spring Boot app, WebSocket bridge, operator CLI |
