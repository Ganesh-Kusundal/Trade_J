# Product Guidelines

## Prose Style
- **Precision:** Use exact technical and financial terms (e.g., "slippage", "latency P99", "order RTT").
- **Clarity:** Avoid ambiguity in strategy and scanner definitions.
- **Evidence-Based:** Claims about strategy performance must be backed by config-hash versioned experiment runs.

## Design Principles (The Trade-J Way)
- **Lane Isolation:** Maintain strict boundaries between Execution (Lane A), Research (Lane B), and UI/Agent (Lane C). No direct Disruptor access from Lane C.
- **Execution Parity:** The research pipeline MUST run the exact same byte code as the live path. Inject `TradingClock` and `SimulatedOrderService` for swaps; keep all other logic identical.
- **Reproducibility First:** Version every scanner, strategy, and feature definition with a content hash. No experiment run is valid without a documented config hash.
- **Zero Future Leakage:** In Replay mode, bar-by-bar reveal across timeframes must be perfectly synchronized. The system must never reveal future data to a strategy or scanner.
- **Performance-Centric:** Hot Path (Lane A) must be free of I/O and blocking operations.

## UX Principles (Decision Lens)
- **Visual Synchronization:** The multi-timeframe replay viewer must provide a unified view of market structure.
- **Actionable Explanations:** AI agents and UI panels should explain *why* a scanner fired or a strategy entered a trade (explainable signals).
- **Session Realism:** Always account for market dynamics like pre-open imbalances, midday chop, and expiry day premium collapse.
- **Frictionless Research:** Tools should make it easy to compare experiment runs (Sharpe, Drawdown, Expectancy) and analyze execution quality (MAE/MFE).
