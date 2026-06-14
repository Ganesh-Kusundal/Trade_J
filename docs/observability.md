# Observability — what is wired and what is not (14 Jun 2026)

## Wired

- **Spring Actuator** at `/actuator/health` with 13+ indicators:
  - orderPipeline, gateway, feed, diskSpace, livenessState, readinessState, ping, ssl
  - broker (Dhan/Upstox/ICICI/simulation), marketData, analytics
- **Fast liveness/readiness** at `/actuator/health/liveness` and
  `/actuator/health/readiness` — the right way for a load balancer to
  probe. The composite `/actuator/health` is dominated by a slow
  `AnalyticsHealthIndicator` (41s in dev). The heartbeat test uses
  the liveness endpoint for the boot probe and skips the composite
  to avoid that bottleneck.
- **SSE** at `/api/v1/stream/read-model` streams the read-model
  projection with last-version dedup and a 15s heartbeat.
- **Structured logs** with correlation id and a Logback config
  (`logback-spring.xml`). All app output is JSON-structured.
- **Micrometer + Prometheus** wired via `PrometheusConfiguration` and
  `MicrometerSpanAdapter` (per the OpenTelemetry track H2 work).
- **MetricsLoggerHarness** emits a periodic SOAK_TEST_METRICS line
  for ad-hoc monitoring.

## Known gaps

- **Analytics health indicator takes 41s** when there is no analytics
  catalog. The composite `/actuator/health` is therefore unreliable
  in dev. The fix is to give the analytics contributor a 5s
  timeout or to register it as `nonCritical`.
- **Read-model fold does not project order lifecycle events** — the
  heartbeat test fails step 5 because the order is processed
  (TRADED) but `read-model.orders` stays empty. This is a real
  gap; the read model needs to subscribe to `OrderAccepted`,
  `OrderFilled`, `OrderPartiallyFilled`, `TradeOpened`,
  `PositionUpdateEvent`, and `PnlUpdatedEvent` to be useful.
- **SSE clients are not auto-disconnected on the backend when
  disconnected from the broker** — broker-down state should
  publish a `StreamHealthChanged` event.
- **No distributed-tracing** for a single user action across
  broker → bus → strategy → order → SSE → UI. The correlation id
  is propagated, but no span exporter is wired into the bus
  consumers.

## What the heartbeat test catches

- Boot within 60s
- Liveness and readiness UP
- `/api/v1/symbols` returns structure
- `POST /api/v1/orders` returns a TRADED order (paper matcher)
- `/api/v1/read-model` contains the order id (currently FAILS —
  the read-model fold is not subscribing to OrderAccepted/Filled)
- `/api/v1/market/ltp` returns structure
- `/api/v1/options/chain` returns expiries
- `/api/v1/stream/read-model` SSE stream is open

The test is a build break if any of these fail. Adding it to CI
is the most valuable single line in the platform's observability
story.
