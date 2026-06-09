# Changelog

All notable changes to the Trade-J platform.

## [0.2.0-SNAPSHOT] - 2026-06-09

### Added

#### Spring Boot AutoConfiguration
- Each broker adapter (Dhan, Upstox, ICICI) now self-registers via Spring Boot AutoConfiguration
- Brokers can be activated by adding their module to classpath and setting `trade.broker-type`
- New `broker/template/` module with quickstart guide for adding new brokers
- Auto-configuration imports registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

#### Architecture Governance
- Spring Modulith architecture tests (ModulithArchitectureTest) verify module boundaries
- AutoConfiguration architecture tests verify broker isolation
- Module dependency cycle detection via DFS traversal
- Broker cross-dependency isolation verified

#### Observability
- PlatformHealthIndicator: aggregated health check across all subsystems
- PrometheusConfiguration: common tags and custom counters (orders, signals)
- Distributed tracing support via Micrometer Observations (trade.tracing.enabled=true)

#### Configuration Consolidation
- RuntimeConfiguration + StartupConfiguration merged into RuntimeAndStartupConfiguration
- MicrometerConfiguration merged into ObservabilityConfiguration
- Reduced top-level @Configuration class count

### Changed
- Broker modules now depend on spring-boot-autoconfigure
- architecture-test module depends on spring-modulith-test

### Deprecated
- RuntimeConfiguration (use RuntimeAndStartupConfiguration)
- StartupConfiguration (use RuntimeAndStartupConfiguration)
- MicrometerConfiguration (use ObservabilityConfiguration)
