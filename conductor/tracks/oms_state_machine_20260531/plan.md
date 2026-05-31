# Implementation Plan: Implement core Order Management System (OMS) state machine

## Phase 1: Architecture & Data Structures
- [ ] Task: Define `OrderState` enum in `core` module
    - [ ] Create `OrderState.java` with states: NEW, PENDING_NEW, OPEN, PARTIALLY_FILLED, FILLED, PENDING_CANCEL, CANCELLED, REJECTED
- [ ] Task: Define `OrderEvent` hierarchy
    - [ ] Create base `OrderEvent` interface
    - [ ] Implement event classes: `OrderCreated`, `OrderSubmitted`, `OrderAccepted`, `OrderExecutionReport`, etc.
- [ ] Task: Define `Order` model refinements
    - [ ] Ensure `Order` record/class includes current state and audit trail capability
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Architecture & Data Structures' (Protocol in workflow.md)

## Phase 2: State Machine Logic (TDD)
- [ ] Task: Implement `OrderStateMachine` transitions
    - [ ] Write tests for valid transitions (e.g., NEW -> PENDING_NEW)
    - [ ] Implement transition logic in `OrderStateMachine`
    - [ ] Write tests for invalid transitions (e.g., FILLED -> CANCELLED) and ensure they fail/throw exceptions
    - [ ] Implement transition validation
- [ ] Task: Implement `OrderExecutionReport` handling
    - [ ] Write tests for partial fills and full fills
    - [ ] Implement logic to update order state based on execution reports
- [ ] Task: Conductor - User Manual Verification 'Phase 2: State Machine Logic (TDD)' (Protocol in workflow.md)

## Phase 3: Persistence & Audit Logging
- [ ] Task: Implement Chronicle Queue state persistence
    - [ ] Write tests for state transition logging
    - [ ] Implement `ChronicleOrderAuditLog` to record events and state changes
- [ ] Task: Integrate State Machine into `OrderManagementService`
    - [ ] Refactor existing service to use the new state machine
    - [ ] Ensure backward compatibility or migration path
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Persistence & Audit Logging' (Protocol in workflow.md)
