# Trade-J Production Readiness Checklist

> This document is a *script* (assertions, not prose). Every section ends
> with a check that the operations team can run as a single command.

## 1. Tokens & secrets

| Check | How to verify |
| --- | --- |
| Dhan TOTP secret present | `./gradlew :app:runProdProfile`; `actuator/health` shows `broker` UP |
| Upstox token source reachable | `actuator/health` includes `broker-upstox: UP` |
| ICICI Breeze session valid | `actuator/health` includes `broker-icici: UP` |
| No secrets in source tree | `git grep -nE 'BEGIN [A-Z]+ PRIVATE KEY\|password=|api-key='` returns nothing |

## 2. OMS crash recovery

| Check | How to verify |
| --- | --- |
| OMS replays from DuckDB on startup | Kill `app` mid-trade; restart; orders in `event_sourced_order_repository` are re-emitted as `OrderAccepted` / `OrderFilled` |
| Reconciler produces no `PositionMismatch` | `./gradlew :app:test --tests *BrokerFailoverEndToEndTest` |
| OMS `replayAll()` completes in < 5 s for 10 k orders | `actuator/metrics/oms.replay.duration` |

## 3. Kill switch

| Check | How to verify |
| --- | --- |
| UI button triggers kill switch | Open UI → press `STOP ALL TRADING` → orders stop |
| CLI killswitch sub-command | `tradej --attach ... killswitch` exits 0; no new orders accepted |
| Backend cancel policy | `actuator/health` includes `killSwitchArmed: true` |

## 4. Gateway load

| Check | How to verify |
| --- | --- |
| 50 k ticks/sec sustained | `./gradlew :gateway:test --tests *GatewaySubscriptionLoadComponentTest` |
| Per-transport write queue never fills | `actuator/metrics/gateway.droppedEventCount` = 0 over 1 hour burn-in |
| TickBatcher latency budget | `actuator/metrics/gateway.batchWindowMs` p99 < 5 ms |

## 5. Broker failover

| Check | How to verify |
| --- | --- |
| Dhan → Upstox fallback | Kill Dhan; Upstox receives subscriptions within 30 s |
| Backtest broker always available | `tradej replay --broker simulation` succeeds even if Dhan/Upstox are down |

## 6. Replay parity

| Check | How to verify |
| --- | --- |
| Per-strategy replay parity | `./gradlew :app:test --tests *ReplayParityEndToEndTest` |
| All strategies deterministic | `tradej strategies parity <plugin-id> --from today-7d --to today` exits 0 |

## 7. Health endpoint behaviour

| Check | How to verify |
| --- | --- |
| `/actuator/health` returns 200 when all subsystems UP | `curl -fsS localhost:8080/actuator/health \| jq .status` |
| 503 when any subsystem DOWN | kill bus; `curl -fsS localhost:8080/actuator/health` returns 503 |
| Subsystem breakdown visible | `curl -sS localhost:8080/actuator/health \| jq .components` |

## 8. Observability

| Check | How to verify |
| --- | --- |
| Prometheus scrape | `curl -sS localhost:8080/actuator/prometheus \| grep trade_` |
| Strategy metrics | `curl -sS localhost:8080/actuator/metrics/strategy.signals.count` |
| Bus metrics | `curl -sS localhost:8080/actuator/metrics/disruptor.dispatchQueueDepth` |

## 9. Graceful shutdown

| Check | How to verify |
| --- | --- |
| OMS finishes in-flight orders | `kill -TERM`; log shows `OMS drained N orders in M ms` |
| Gateway sends close frames | WS clients see `code=1000` within 5 s |
| Replay sessions stop | `ReplayController.state = STOPPED` in `/actuator/metrics` |

## 10. Production smoke test

Run the *entire* checklist as a single Gradle task:

```bash
./gradlew :app:productionSmokeTest
```

The task boots the app in `prod` profile, runs each section's check
above, and exits non-zero on any failure. CI must be green before
deployment.
