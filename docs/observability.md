# Observability

Trade-J exposes its runtime state through Spring Boot Actuator + Micrometer, scraped
by Prometheus at `/actuator/prometheus`, and rendered in Grafana via the
[`dashboards/trade-j-overview.json`](../../dashboards/trade-j-overview.json)
dashboard.

## Quick start

1. **Confirm `/actuator/prometheus` is reachable.** With the app running locally:

   ```bash
   curl -s http://localhost:8080/actuator/prometheus | head -20
   ```

   You should see Prometheus exposition-format lines (HELP/TYPE followed by
   numeric samples). `management.endpoints.web.exposure.include: health,info,prometheus`
   is the relevant setting in `app/src/main/resources/application.yml:33-37`.

2. **Configure Prometheus to scrape the endpoint.** Add a scrape job in your
   `prometheus.yml`:

   ```yaml
   scrape_configs:
     - job_name: trade-j
       metrics_path: /actuator/prometheus
       scrape_interval: 15s
       static_configs:
         - targets: ['localhost:8080']
   ```

3. **Import the dashboard into Grafana.**
   - Grafana → Dashboards → New → Import.
   - Upload `dashboards/trade-j-overview.json`.
   - When prompted, pick the Prometheus datasource that scrapes Trade-J. (The
     dashboard references it via the `${DS_PROMETHEUS}` template variable, so
     any Prometheus datasource works.)
   - The `Broker` dropdown at the top of the dashboard lets you switch the
     `dhan` / `icici` / `upstox` / `simulation` namespace for the broker-tagged
     gauges.

## Dashboard panel → metric source

The dashboard has 15 panels in 5 rows. The metric each panel queries, and where
it is registered in the codebase:

### Row 1 — Gateway Health

| Panel | PromQL | Source |
| --- | --- | --- |
| Gateway events sent (rate) | `rate(gateway_events_sent[5m])` | `ObservabilityConfiguration.java:290` |
| Gateway events dropped (rate) | `rate(gateway_events_dropped[5m])` | `ObservabilityConfiguration.java:292` |
| Broker WebSocket connected | `${broker}_websocket_connected` | `ObservabilityConfiguration.java:170` |

### Row 2 — Market Data

| Panel | PromQL | Source |
| --- | --- | --- |
| Hot-path tick rate | `hotpath_ticks_rate` | `ObservabilityConfiguration.java:253` |
| Hot-path ticks rate-limited | `rate(hotpath_ticks_rate_limited[5m])` | `ObservabilityConfiguration.java:285` |

### Row 3 — Trading

| Panel | PromQL | Source |
| --- | --- | --- |
| Orders placed (rate) | `rate(orders_placed_total[5m])` | `PrometheusConfiguration.java:42` |
| Orders rejected (rate) | `rate(orders_rejected_total[5m])` | `PrometheusConfiguration.java:50` |
| Signals generated (rate) | `rate(signals_generated_total[5m])` | `PrometheusConfiguration.java:58` |
| Order latency p50/p95/p99 | `histogram_quantile(...)` on `${broker}_order_latency_seconds_bucket` | `ObservableOrderCommand.java:101` |

### Row 4 — Risk & Reconciliation

| Panel | PromQL | Source |
| --- | --- | --- |
| Async-sink events dropped (rate) | `rate(duckdb_events_dropped[5m])` + `rate(featurestore_events_dropped[5m])` + `rate(gateway_events_dropped[5m])` | `ObservabilityConfiguration.java:292,294,296` |
| Event-bus dispatch queue depth | `disruptor_dispatch_queue_depth` + `event_bus_dispatch_queue_depth` | `ObservabilityConfiguration.java:200,270` |
| Broker resilience | `rate(broker_retry_attempt_total[5m])` + `rate(broker_circuit_breaker_state_change_total[5m])` | `BrokerResilienceMetrics.java` |

### Row 5 — JVM & Runtime

| Panel | PromQL | Source |
| --- | --- | --- |
| JVM threads (current / peak) | `jvm_threads_count` + `jvm_threads_peak_count` | `VirtualThreadConfiguration.java:43,47` |
| Disruptor ring-buffer remaining capacity | `disruptor_ring_buffer_remaining_capacity` + `event_bus_ring_buffer_remaining_capacity` | `ObservabilityConfiguration.java:210,237` |
| Disruptor stage latency p95 | `histogram_quantile(0.95, ...)` on `disruptor_stage_latency_seconds_bucket` | `ObservabilityConfiguration.java:139` |

## Beyond the dashboard: full gauge inventory

`ObservabilityConfiguration.micrometerGauges(...)` registers 27 gauges. The
dashboard covers the most operationally important; the rest are still
exposed at `/actuator/prometheus` for ad-hoc PromQL queries:

- `dhan.websocket.connected`, `dhan.websocket.subscriptions`
- `instruments.catalog.size`
- `event.bus.started`, `event.bus.runtime`, `event.bus.instance`,
  `event.bus.subscribers`
- `event.bus.dispatch.queue.depth`, `event.bus.dispatch.dropped_events`
- `event.bus.ring.buffer.remaining_capacity`, `event.bus.ring.buffer.size`
- `execution.queue.depth`, `execution.queue.remaining_capacity`
- `disruptor.ring.buffer.remaining_capacity`, `disruptor.ring.buffer.size`,
  `disruptor.dispatch.queue.depth`, `disruptor.dispatch.dropped_events`,
  `disruptor.subscribers.count`
- `hotpath.ticks.total`, `hotpath.ticks.rate`, `hotpath.ticks.rate_limited`
- `hotpath.orders.accepted.total`, `hotpath.orders.rate`
- `gateway.events.sent`, `gateway.events.dropped`
- `duckdb.events.dropped`, `featurestore.events.dropped`

Other config files also register meters worth knowing about:

- `PrometheusConfiguration.java` — `orders_placed_total`, `orders_rejected_total`,
  `signals_generated_total` counters, `jvm.gc.pause` timer.
- `VirtualThreadConfiguration.java` — `jvm.threads.count`, `jvm.threads.peak.count`.
- `StrategyMetricsConfiguration.java` — `strategy.signals.gauge.size`,
  `strategy.signals.count` (per-strategy/per-event/per-outcome tags).
- `BrokerResilienceMetrics.java` — `broker.circuit_breaker.state_change`,
  `broker.circuit_breaker.execution.time`, `broker.failover.execution`,
  `broker.retry.attempt`, `broker.retry.execution.time`.
- `MicrometerBrokerWebSocketMetrics.java` — `broker.ws.reconnects`,
  `broker.ws.messages`, `broker.ws.parse_errors`, `broker.ws.stale_detections`,
  `broker.ws.dropped_ticks`.
- `ObservableOrderCommand.java` / `ObservableMarketDataProvider.java` —
  `${broker}.order.calls` / `${broker}.order.latency` and
  `${broker}.marketdata.calls` / `${broker}.marketdata.latency` (where
  `${broker}` is the broker namespace prefix).

## Conventions

- All gauges use dots as separators; Micrometer emits them to Prometheus with
  underscores, so `event.bus.dispatch.queue.depth` becomes
  `event_bus_dispatch_queue_depth` in PromQL.
- Broker-specific metrics are name-prefixed (`dhan.`, `icici.`, `upstox.`,
  `simulation.`) rather than tagged — the dashboard's `Broker` template
  variable handles the substitution.
- The common tags `application=trade-j` and `region=local` are attached to
  every meter by `PrometheusConfiguration.commonTags()`.
