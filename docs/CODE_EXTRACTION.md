# Code extraction follow-up

Physical module grouping is complete (domain folders: `broker/`, `data/`, etc.). These logical moves are **separate PRs** — do not block on them for builds.

## Priority order

| Current location | Target | Domain | PR scope |
|------------------|--------|--------|----------|
| `app` … `BrokerConfiguration`, `UpstoxConfiguration` | `broker/dhan` / `broker/upstox` config packages or new `broker/spring` | broker | Spring `@Configuration` only; keep `@ConditionalOnProperty` |
| `app` … `BrokerStartupOrchestrator`, `BrokerRuntimeMode*` | `broker/core` | broker | Startup without Spring dependency where possible |
| `app` … `LivePnlService`, depth orchestrators | `trading/execution` or `runtime/hotpath` | trading / runtime | Business logic out of app |
| `data/persistence` … `DuckDbScanStore` + scanner model imports | Shared scan DTOs in `core` | core | Break persistence → scanner compile coupling |
| Dual pipeline (`PipelineConfig` vs `GraphRuntimeService`) | Single authoritative path | runtime + app | Epic — document retirement in [ARCHITECTURE.md](ARCHITECTURE.md) first |

## Out of scope

- Retiring Disruptor pipeline entirely
- New pipeline Gradle module
- Splitting `app/frontend` to its own module

## Verification per extraction PR

```bash
./gradlew build --no-daemon
./gradlew :app:runtimeE2eTest   # when touching startup/broker wiring
```
