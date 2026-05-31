# Specification: Implement core Order Management System (OMS) state machine

## Overview
The Order Management System (OMS) is the heart of the Trade-J platform. This track focuses on implementing a robust, event-driven state machine to manage the lifecycle of an order, from creation to finality (Filled, Cancelled, or Rejected).

## Goals
- Define a clear set of order states and valid transitions.
- Implement a thread-safe, high-performance state machine.
- Ensure all state transitions are auditable and persisted.
- Decouple state logic from broker-specific implementations.

## Requirements
- **Order States:** `NEW`, `PENDING_NEW`, `OPEN`, `PARTIALLY_FILLED`, `FILLED`, `PENDING_CANCEL`, `CANCELLED`, `REJECTED`.
- **Transitions:** Only allow valid transitions (e.g., `NEW` -> `PENDING_NEW`, `OPEN` -> `FILLED`).
- **Events:** `OrderCreated`, `OrderSubmitted`, `OrderAccepted`, `OrderExecutionReport`, `OrderCancelRequested`, `OrderCancelled`, `OrderRejected`.
- **Performance:** Low-latency processing of execution reports.
- **Persistence:** All transitions must be logged to a durable store (Chronicle Queue).

## Technical Design
- **Core Module:** Define states and events in the `core` module.
- **State Machine:** Implement logic using the "State" pattern or a lightweight transition table.
- **Concurrency:** Ensure safe updates in a multi-threaded environment (potentially using the Disruptor for event sequencing).
- **Audit Log:** Use `Chronicle Queue` to record every event and resulting state change.

## Out of Scope
- Actual broker API calls (handled in `broker` modules).
- Strategy execution logic.
- Complex risk checks (though the state machine should support "Rejected" states).
