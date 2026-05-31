# Implementation Plan: Phase 0: Fix Hot Path and Introduce TradingClock

## Phase 1: Time Management (TradingClock)
- [x] Task: Define `TradingClock` interface in `core` module (66fed19)
    - [x] Create `TradingClock.java` interface
    - [x] Write unit tests for `LiveTradingClock`
    - [x] Implement `LiveTradingClock` (System clock wrapper)
- [x] Task: Implement `ReplayTradingClock` for research lane (551cb7c)
    - [x] Write unit tests for `ReplayTradingClock` (verify manual advancement)
    - [x] Implement `ReplayTradingClock` with `advanceTo(Instant)` capability
- [~] Task: Inject `TradingClock` into existing services
    - [ ] Identify all usages of `System.currentTimeMillis()` and `Instant.now()`
    - [ ] Refactor `StrategyEngine` and `OMS` to use injected `TradingClock`
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Time Management (TradingClock)' (Protocol in workflow.md)

## Phase 2: Hot Path Optimization & Isolation
- [ ] Task: Decouple Risk from direct correlations (Fix PE-02)
    - [ ] Write tests for `EventSourcedNetPositionProvider`
    - [ ] Implement/Refactor `RiskHandler` to use event-sourced positions
- [ ] Task: Async DuckDB Writes (Fix FS-01)
    - [ ] Write integration tests for async DuckDB persistence
    - [ ] Implement `AsyncDuckDbDispatcher` (move off Disruptor hot path)
- [ ] Task: Isolate Replay DI Context (Fix AD-02)
    - [ ] Refactor Spring configuration to use profiles or separate contexts for `LIVE` vs `REPLAY`
    - [ ] Write architectural test ensuring no `REPLAY` beans are loaded in `LIVE` profile
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Hot Path Optimization & Isolation' (Protocol in workflow.md)
