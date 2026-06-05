# Pipeline Design — Safe Topology

## Required topology (hot path with strategies)

1. **Risk** node must be present when **Strategy** is present
2. **OMS** or **OrderPlacement** must be present when **Strategy** is present
3. **Portfolio** node recommended for capital lifecycle (reservation/release)
4. Graph validated at compile time via `PipelineGraphValidator`

## Event flow

```
Strategy → SignalGenerated → Risk → SignalPendingExecution → SignalGate → OrderPlacement → OMS
```

Portfolio capital reservation also runs inside `PositionRiskHandler` when `PortfolioEngine` is wired.
