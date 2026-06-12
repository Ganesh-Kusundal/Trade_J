# Trade-J Runtime Verification Suite

This directory contains **runtime verification** tests and scripts that prove actual system behavior through execution, not code inspection.

## Philosophy

> **Code says A, Runtime does B**

All conclusions must be backed by **measurable evidence** from actual execution.

## Directory Structure

```
runtime-verification/
├── event-bus/           # Event bus propagation, ordering, backpressure tests
├── composition/         # CLI vs Spring object graph comparison
├── broker-certification/# Live broker capability certification
├── strategy/            # Strategy execution traces
├── data/                # Data integrity lineage verification
├── gateway/             # WebSocket performance tests
├── spring/              # Bean usage audit
├── replay/              # Replay determinism certification
└── reports/             # Generated reports (timestamped)
```

## Quick Start

### Run All Runtime Verifications

```bash
./scripts/run-runtime-verification.sh
```

This executes:
- Event bus tests (propagation, ordering, stress)
- Composition root tests
- Broker certification (if credentials exist)
- Data integrity tests
- Replay determinism tests
- Architecture tests

**Output**: `runtime-verification/reports/{timestamp}/`

### Run Broker Certification (Live)

```bash
# Dhan broker
./scripts/certify-broker-runtime.sh dhan

# Upstox broker
./scripts/certify-broker-runtime.sh upstox

# ICICI broker
./scripts/certify-broker-runtime.sh icici
```

Tests 10+ capabilities with live credentials:
- Authentication
- Connection
- Market data (quotes, historical)
- Option chain
- Positions & holdings
- Order placement (dry run)
- WebSocket streaming
- Instrument search

**Output**: `runtime-verification/broker-certification/{broker}-certification-{timestamp}.json`

## Verification Phases

### Phase 1: Event Bus Runtime Verification

**What we prove**:
- ✅ Events actually propagate through DisruptorEventBus
- ✅ All subscribers are invoked
- ✅ Event ordering is preserved under load
- ✅ Backpressure behavior is correct
- ✅ Duplicates are detected and suppressed

**How we prove it**:
```bash
./gradlew :runtime-disruptor:test --tests 'DisruptorEventBusStressTest'
./gradlew :runtime-disruptor:test --tests 'DisruptorEventBusConcurrencyTest'
./gradlew :runtime-disruptor:test --tests 'DisruptorEventBusDedupTest'
```

**Artifacts**:
- `event-bus/trace-001.json` - Event propagation trace
- `event-bus/ordering-test.json` - Ordering verification
- `event-bus/backpressure-test.json` - Backpressure behavior

### Phase 2: Composition Root Verification

**What we prove**:
- CLI and Spring create same object graphs (or document differences)
- Same OMS implementation
- Same Risk handler
- Same EventBus (or document why different)

**How we prove it**:
```bash
# CLI graph
tradej doctor graph --format json --output cli-graph.json

# Spring graph
curl http://localhost:8080/api/admin/graph > spring-graph.json

# Compare
scripts/compare-composition-graphs.sh cli-graph.json spring-graph.json
```

**Artifacts**:
- `composition/cli-graph.json`
- `composition/spring-graph.json`
- `composition/graph-comparison.json`

### Phase 3: Broker Certification (Live)

**What we prove**:
- Each broker capability works with REAL credentials
- Authentication succeeds
- Market data flows
- Orders can be placed
- WebSocket streams work

**How we prove it**:
```bash
./scripts/certify-broker-runtime.sh dhan
```

**Test Matrix**:

| Capability | Status | Latency | Evidence |
|------------|--------|---------|----------|
| Authentication | PASS/FAIL | X ms | auth-response.json |
| Connection | PASS/FAIL | X ms | connection.log |
| Market Data | PASS/FAIL | X ms | quote.json |
| Historical | PASS/FAIL | X ms | historical.json |
| Option Chain | PASS/FAIL | X ms | option-chain.json |
| Positions | PASS/FAIL | X ms | positions.json |
| Holdings | PASS/FAIL | X ms | holdings.json |
| Order Placement | PASS/FAIL | X ms | order.json |
| WebSocket | PASS/FAIL | X ms | ws-stream.json |
| Instrument Search | PASS/FAIL | X ms | search.json |

**Artifacts**:
- `broker-certification/{broker}-certification-{timestamp}.json`

### Phase 4: Strategy Execution Trace

**What we prove**:
- Scanner detects setup
- Signal generated
- Risk check passes
- Order created
- Order sent to broker
- Fill received
- Position updated
- PnL calculated
- Trade persisted

**How we prove it**:
```bash
tradej replay candle NIFTY 1m 2026-06-01 2026-06-02 \
  --strategy moving-average-crossover \
  --trace full \
  --output strategy-trace.json
```

**Artifacts**:
- `strategy/execution-trace-001.json`

### Phase 5: Data Integrity Certification

**What we prove**:
- Broker data → Parquet → DuckDB → Replay maintains identical data
- Same candle count
- Same OHLC values
- Same volume
- Same timestamps
- No data gaps

**How we prove it**:
```bash
# Download from broker
tradej historical download NIFTY 1m 2026-06-01 2026-06-05 --output broker-data.json

# Read from Parquet
tradej parquet read historical/nifty-1m-2026-06.parquet --output parquet-data.json

# Query DuckDB
tradej data query "SELECT * FROM candles WHERE symbol='NIFTY' ..." --output duckdb-data.json

# Run replay
tradej replay candle NIFTY 1m 2026-06-01 2026-06-05 --output replay-data.json

# Compare
scripts/compare-data-lineage.sh broker-data.json parquet-data.json duckdb-data.json replay-data.json
```

**Artifacts**:
- `data/lineage-certification.json`
- `data/certification-{1d,5d,30d}.json`

### Phase 6: Gateway/WebSocket Certification

**What we prove**:
- WebSocket connections established
- Market ticks received in frontend
- No dropped messages
- Latency < 50ms (p50)
- Latency < 200ms (p99)
- Reconnect works

**How we prove it**:
```bash
./scripts/gateway-performance-test.sh --duration 300
```

**Artifacts**:
- `gateway/performance-certification.json`
- `gateway/frontend-integration.json`

### Phase 7: Spring Bean Usage Audit

**What we prove**:
- Which beans are actually used at runtime
- Which beans are dead code
- Which configurations are unused
- Actual invocation counts

**How we prove it**:
```bash
# Start app with bean tracking
./gradlew :app:bootRun --args='--tradej.bean-tracking=true'

# Exercise features
tradej market quote NIFTY
tradej order place ...
tradej portfolio positions

# Get usage report
curl http://localhost:8080/api/admin/bean-usage > bean-usage.json
```

**Artifacts**:
- `spring/bean-usage-report.json`
- `spring/dead-configurations.json`

### Phase 8: Replay Determinism Certification

**What we prove**:
- Same replay run 5 times produces identical results
- Same signals generated
- Same trades executed
- Same PnL calculated
- Same event order
- State properly reset between runs

**How we prove it**:
```bash
# Run replay 5 times
for i in {1..5}; do
  tradej replay candle NIFTY 1m 2026-06-01 2026-06-02 \
    --strategy moving-average-crossover \
    --output replay-run-${i}.json
done

# Compare
scripts/verify-replay-determinism.sh replay-run-*.json
```

**Artifacts**:
- `replay/determinism-certification.json`
- `replay/state-reset-verification.json`

## Certification Levels (New Framework)

```
Level 0:  Build & Compilation ✅
Level 1:  Composition Root Verification (NEW)
Level 2:  Broker Runtime Certification (NEW)
Level 3:  Data Integrity Certification (NEW)
Level 4:  Event Flow Runtime Verification (NEW)
Level 5:  Strategy Execution Trace (NEW)
Level 6:  Replay Determinism Certification (NEW)
Level 7:  Gateway/WebSocket Certification (NEW)
Level 8:  UI Integration Test (NEW)
Level 9:  Production Readiness (NEW)
```

Each level generates:
- JSON report
- Execution traces
- Performance metrics
- PASS/FAIL status

## Interpreting Results

### PASS Criteria

A test PASSES when:
1. Code executes without errors
2. Expected behavior matches actual behavior
3. Performance meets thresholds
4. Data integrity verified

### FAIL Criteria

A test FAILS when:
1. Code throws exceptions
2. Expected behavior differs from actual
3. Performance below thresholds
4. Data integrity violated

### Evidence Requirements

Every conclusion must cite:
- JSON report with metrics
- Execution logs
- Timestamps
- Latency measurements

**No architectural inference allowed without runtime proof.**

## Troubleshooting

### Tests Fail Due to Missing Credentials

```bash
# Check credentials exist
ls -la config/dhan-local.properties
ls -la config/upstox-live.properties
ls -la config/icici-local.properties

# If missing, copy from examples
cp config/dhan-local.properties.example config/dhan-local.properties
# Edit with actual credentials
```

### Tests Timeout

Increase timeout in script:
```bash
timeout 60 ./gradlew ...  # Increase from 30 to 60 seconds
```

### Network Issues

Verify connectivity:
```bash
# Dhan
curl https://api.dhan.co/health

# Upstox
curl https://api.upstox.com/health
```

## Next Steps

After running all verifications:

1. Review reports in `runtime-verification/reports/{timestamp}/`
2. Identify FAIL tests
3. Investigate root causes
4. Fix issues
5. Re-run verifications
6. Generate final runtime verification report

**Goal**: 100% PASS rate with measurable evidence for every critical path.
