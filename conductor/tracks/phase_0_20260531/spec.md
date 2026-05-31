# Specification: Phase 0: Fix Hot Path and Introduce TradingClock

## Overview
Before building the Research Platform, the existing Hot Path (Lane A) must be hardened and prepared for Lane B (Research) integration. This phase addresses core architectural blockers related to state isolation, hot path performance, and time management.

## Goals
- **Fix AD-02 (Replay State Bleed):** Isolate replay state in a separate DI context to prevent contamination of live state.
- **Fix PE-02 (Risk Correlation Coupling):** Decouple risk checks from direct correlations and bind them to the `EventSourcedNetPositionProvider`.
- **Fix FS-01 (Hot Path I/O):** Move DuckDB writes to an asynchronous consumer, removing blocking I/O from the Disruptor hot path.
- **Introduce `TradingClock`:** Establish a unified interface for time that supports wall-clock time (Live) and controlled/backdated time (Replay/Backtest).

## Technical Requirements
1.  **DI Isolation:** Use Spring profiles or custom context hierarchy to ensure `ReplayController` beans never leak into `Live` sessions.
2.  **Event-Sourced Net Positions:** Ensure risk checks query an event-sourced provider rather than a direct-coupled state.
3.  **Async DuckDB Dispatcher:** Implement a Disruptor event handler or an async queue to batch DuckDB writes.
4.  **`TradingClock` Interface:**
    - `instant()`: Returns the current `Instant`.
    - `now()`: Returns IST `LocalDateTime`.
    - Implementations: `LiveTradingClock` (wraps `System.currentTimeMillis()`), `ReplayTradingClock` (manually advanced).

## Out of Scope
- Implementation of the Replay Viewer UI.
- Scanner Lab implementation.
- Full backtest orchestration.
