# Trade-J Developer Guides

Step-by-step recipes for adding new platform components. Each guide assumes you have the repository cloned and the build environment working.

## Contents

- [ADDING_A_STRATEGY.md](ADDING_A_STRATEGY.md) — Add a new `GraphStrategyPlugin` (tick, depth, candle, or multi-event strategy).
- [ADDING_AN_INDICATOR.md](ADDING_AN_INDICATOR.md) — Add a new `IndicatorProvider` to the candle-aligned indicator registry.
- [ADDING_A_SCANNER.md](ADDING_A_SCANNER.md) — Add a new `ScanCriterionProvider` (scan filter type used in scan profiles).
- [ADDING_A_BROKER.md](ADDING_A_BROKER.md) — Add a new broker adapter (a new `BrokerProvider` plus its connection module).
- [ADDING_A_DASHBOARD.md](ADDING_A_DASHBOARD.md) — Add a new widget to the bottom tab bar or a new full-page dashboard.

## How Trade-J discovers plugins

All backend plugins (strategies, indicators, scanners, brokers) are loaded by the Java `ServiceLoader` mechanism from `META-INF/services/<fully-qualified-interface-name>` files. The frontend dashboard uses a static registry (`WIDGET_REGISTRY`) and a static config object (`BOTTOM_DASHBOARD_CONFIG`).

You do not edit a central switch to add a plugin. You implement an interface, register your class in a `META-INF/services` file, and the platform picks it up on next startup. See each guide for the exact file path and example.

## Conventions

- Class names: `PascalCase`, package-private unless SPI requires public.
- Each plugin must have a unique stable string key (the `name()` for indicators, `type()` for scanner criteria, `source()` enum for brokers).
- Tests live in `src/test/java` for unit tests, `src/testFixtures/java` for reusable harnesses.
- All guides use a real example (HalfTrend strategy / RSI-style indicator / volume-spike scanner / Dhan-style broker / MockPanel-style empty state) so the snippets compile against the actual interfaces.
