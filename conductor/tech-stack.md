# Tech Stack

## Core Language & Runtime
- **Java 21:** Modern Java features and performance.
- **Spring Boot 3.4.13:** Dependency injection, configuration management, and application framework.

## Build & Dependencies
- **Gradle:** Build automation and dependency management.
- **Spring Boot Starter:** Standard Spring Boot dependencies.

## Data & Persistence
- **DuckDB:** Embedded analytical database for fast queries on trading data.
- **Chronicle Queue:** Low-latency, persisted off-heap messaging for order and market data audit trails.

## High Performance & Concurrency
- **LMAX Disruptor:** High-performance inter-thread messaging library.
- **Caffeine:** High-performance caching.

## External Integrations
- **Dhan SDK:** Native integration for Dhan broker.
- **Upstox API:** Integration for Upstox broker.

## Verification & Quality
- **JUnit 5:** Unit and integration testing.
- **SpotBugs:** Static analysis for bug detection.
- **Checkstyle:** Coding standard enforcement.
