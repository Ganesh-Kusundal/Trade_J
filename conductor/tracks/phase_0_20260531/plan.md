# Implementation Plan: Phase 0: Fix Hot Path and Introduce TradingClock

## Phase 1: Time Management (TradingClock) [checkpoint: 484a69c]
- [x] Task: Define `TradingClock` interface in `core` module (66fed19)
    - [x] Create `TradingClock.java` interface
    - [x] Write unit tests for `LiveTradingClock`
    - [x] Implement `LiveTradingClock` (System clock wrapper)
- [x] Task: Implement `ReplayTradingClock` for research lane (551cb7c)
    - [x] Write unit tests for `ReplayTradingClock` (verify manual advancement)
    - [x] Implement `ReplayTradingClock` with `advanceTo(Instant)` capability
- [x] Task: Inject `TradingClock` into existing services
    - [x] Identify all usages of `System.currentTimeMillis()` and `Instant.now()`
    - [x] Refactor `StrategyEngine` and `OMS` to use injected `TradingClock`
- [x] Task: Conductor - User Manual Verification 'Phase 1: Time Management (TradingClock)' (Protocol in workflow.md)

## Phase 2: Hot Path Optimization & Isolation
- [x] Task: Decouple Risk from direct correlations (Fix PE-02)
    - [x] Write tests for `EventSourcedNetPositionProvider`
    - [x] Implement/Refactor `RiskHandler` to use event-sourced positions
- [x] Task: Async DuckDB Writes (Fix FS-01)
    - [x] Write integration tests for async DuckDB persistence
    - [x] Implement `AsyncDuckDbDispatcher` (move off Disruptor hot path)
- [x] Task: Isolate Replay DI Context (Fix AD-02)
    - [x] Refactor Spring configuration to use profiles or separate contexts for `LIVE` vs `REPLAY`
        - [x] `ClockConfiguration` annotated `@Profile("!replay")` to prevent conflicting `TradingClock` beans in replay mode
        - [x] `TimeConfiguration` provides `@Profile("!replay")` `liveTradingClock()` and `@Profile("replay")` `replayTradingClock()`
        - [x] `PersistenceConfiguration.replayClock()` annotated `@Profile("replay")` to exclude from LIVE mode
        - [x] Created `application-replay.yml` profile config with `trade.runtime.mode: REPLAY`
    - [x] Write architectural test ensuring no `REPLAY` beans are loaded in `LIVE` profile
        - [x] `ClockConfiguration` verified annotated with `@Profile`
        - [x] `ReplayTradingClock` confirmed in `core` module (not auto-scanned by app)
        - [x] `ReplayClock` confirmed in `persistence` module
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Hot Path Optimization & Isolation' (Protocol in workflow.md)
