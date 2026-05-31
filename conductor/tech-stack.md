# Tech Stack

## Core Language & Runtime
- **Java 21:** Primary language for all three lanes.
- **Spring Boot 3.4.13:** Application framework and dependency injection.

## Build System
- **Gradle:** Modular build system managing the 30+ subprojects.

## Lane A: Execution Hot Path
- **LMAX Disruptor:** High-performance inter-thread messaging.
- **Chronicle Queue:** Low-latency event journaling for execution parity.

## Lane B: Research & Analytics
- **DuckDB:** Embedded analytical database for fast queries on strategy runs and scanner hits.
- **Apache Parquet:** Storage format for historical market data (candles).
- **Caffeine:** High-performance caching for feature engineering.

## Lane C: Agent & UI Layer
- **React (TypeScript):** Frontend framework for the Research Workspace.
- **Vite:** Modern frontend build tool.
- **Lightweight Charts:** Financial charting for the Multi-TF Replay Viewer.
- **Model Context Protocol (MCP):** SSE-based server to expose tools to AI agents.

## Infrastructure & Testing
- **JUnit 5:** Core testing framework (Unit, Component, Integration).
- **SpotBugs / Checkstyle:** Static analysis and code quality enforcement.
- **Micrometer + Prometheus:** Observability for Disruptor and system health.
