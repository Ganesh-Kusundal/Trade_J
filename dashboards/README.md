# Dashboards

Pre-built Grafana dashboards for Trade-J.

- `trade-j-overview.json` — runtime overview. Wires the 27 Micrometer gauges
  emitted by `app/src/main/java/com/tradej/app/config/ObservabilityConfiguration.java`
  (plus the supporting meters from `PrometheusConfiguration`,
  `VirtualThreadConfiguration`, `StrategyMetricsConfiguration`, and
  `BrokerResilienceMetricsConfiguration`) into 15 panels in 5 rows.
  See [`docs/observability.md`](../docs/observability.md) for the panel→metric
  mapping and import instructions.
