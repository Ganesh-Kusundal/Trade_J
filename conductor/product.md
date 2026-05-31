# Initial Concept
Trade-J Research Platform: A high-performance trading research and execution environment featuring a three-lane architecture (Execution, Research/Analytics, Agent/UI).

# Product Definition
## Vision
Trade-J is a modular, high-performance Java 21 trading platform designed to bridge the gap between backtesting and live execution. By employing a "Three-Lane Architecture" (Hot Path, Research, and Agent Layer), it ensures sub-millisecond execution while providing powerful analytical and AI-driven capabilities.

## Target Users
- **Quantitative Researchers:** Need deterministic candle-by-candle replay and batch experiment orchestration.
- **Algorithmic Traders:** Require a low-latency execution engine with native Indian broker support (Dhan, Upstox).
- **Strategy Developers:** Benefit from a "Decision Lens" replay viewer and MCP-integrated AI agents for strategy analysis.

## Core Lanes
- **Lane A: Execution Hot Path:** LMAX Disruptor, OMS, Risk, Strategy Evaluation. No I/O, no blocking.
- **Lane B: Research & Analytics:** DuckDB, Parquet analysis, batch scanner evaluation, and indicator tests.
- **Lane C: Agent & UI Layer:** MCP tools, React charts, and AI assistant panel.

## Key Capabilities
- **Multi-Timeframe Replay Viewer:** bar-by-bar reveal across 1m, 5m, 15m, and Daily timeframes.
- **Scanner-Strategy-Execution Pipeline:** Deterministic flow from market data to order placement.
- **MCP Server:** Exposes 16+ tools (Scanner, Replay, Analytics, Data) for AI agents (askfuzz-style).
- **Execution Realism:** Models lot sizes, liquidity, freeze limits, and Dhan/Upstox-specific latencies.

## Success Metrics
- **Execution Parity:** Identical byte code for backtest and live execution (TradingClock injection).
- **Research Throughput:** Performance of batch experiment runs using config-hash versioning.
- **Agent Intelligence:** Utility and accuracy of AI-driven strategy "Decision Lens" explanations.
