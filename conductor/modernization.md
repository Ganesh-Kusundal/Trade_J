# Institutional Modernization Plan: Phase 1 & 2

## Objective
Harden the core execution pipeline to meet institutional grade standards by eliminating branching overhead in the Risk Engine and enforcing strict broker uniformity.

## Background & Motivation
1.  **Risk Engine (`PositionRiskHandler.java`)**: Currently relies on `instanceof` to process `DomainEvent` objects. This incurs unnecessary branching and degrades performance under high tick burst scenarios.
2.  **Broker Adapters**: The current multi-broker setup contains numerous `Unsupported` stubs for Upstox and ICICI, breaking the uniformity of the execution engine. We need to enforce a single functional source of truth (Dhan) for now.

## Proposed Solution
1.  **Visitor Pattern for Risk Engine**:
    *   Introduce `DomainEventVisitor` interface in `trade-core`.
    *   Update `DomainEvent` to include `accept(DomainEventVisitor visitor)`.
    *   Refactor `PositionRiskHandler` to implement the visitor pattern, replacing the `instanceof` blocks with direct type-safe callbacks.
2.  **Enforce Dhan as Primary Broker**:
    *   Remove `UpstoxUnsupported*` and `IciciUnsupported*` stub implementations.
    *   Isolate active trading capabilities strictly to the Dhan adapters.

## Implementation Steps
1.  **Core Event Refactoring (Week 1)**:
    *   Create `DomainEventVisitor` and implement `accept()` across all 44 `DomainEvent` implementations.
    *   Refactor `PositionRiskHandler`.
2.  **Broker Cleanup (Week 1)**:
    *   Delete Upstox and ICICI stub classes.
    *   Update Dependency Injection / Spring config to strictly use Dhan as the live provider.
3.  **Verification**:
    *   Ensure all unit tests (`PositionRiskHandlerStressTest`) pass.
    *   Verify throughput improvements or stable baseline via JMH benchmarks.

## Verification & Testing
*   Add strict assertions in `ConsoleApiContractIntegrationTest` to verify that `PositionRiskHandler` processes events without allocation or `instanceof` overhead.
*   Ensure zero compilation errors after removing Upstox/ICICI stubs.

## Migration & Rollback
*   If the Visitor pattern introduces unexpected double-dispatch latency, we can revert to the `instanceof` pattern as it currently works via a clean `git revert`.