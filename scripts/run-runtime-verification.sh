#!/bin/bash
# Trade-J Runtime Verification Executor
# This script runs all runtime verification tests and generates proof artifacts

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
VERIFICATION_DIR="$PROJECT_ROOT/runtime-verification"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_DIR="$VERIFICATION_DIR/reports/$TIMESTAMP"

mkdir -p "$REPORT_DIR"

echo "========================================="
echo "Trade-J Runtime Verification Suite"
echo "========================================="
echo "Timestamp: $TIMESTAMP"
echo "Report Dir: $REPORT_DIR"
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

pass_count=0
fail_count=0
skip_count=0

run_test() {
    local test_name="$1"
    local test_command="$2"
    local output_file="$3"
    
    echo -n "Running: $test_name ... "
    
    if eval "$test_command" > "$output_file" 2>&1; then
        echo -e "${GREEN}PASS${NC}"
        pass_count=$((pass_count + 1))
    else
        echo -e "${RED}FAIL${NC}"
        echo "  See: $output_file"
        fail_count=$((fail_count + 1))
    fi
}

# ========================================
# Phase 1: Event Bus Runtime Verification
# ========================================
echo ""
echo "Phase 1: Event Bus Runtime Verification"
echo "-----------------------------------------"

# Test 1.1: Event Bus Unit Tests
run_test "EventBus Runtime Tests" \
    "cd $PROJECT_ROOT && ./gradlew :runtime-disruptor:test --tests 'DisruptorEventBusStressTest' --quiet" \
    "$REPORT_DIR/event-bus-unit-tests.log"

# Test 1.2: Event Bus Concurrency Tests  
run_test "EventBus Concurrency Tests" \
    "cd $PROJECT_ROOT && ./gradlew :runtime-disruptor:test --tests 'DisruptorEventBusConcurrencyTest' --quiet" \
    "$REPORT_DIR/event-bus-concurrency-tests.log"

# Test 1.3: Event Bus Stress Tests
run_test "EventBus Stress Tests" \
    "cd $PROJECT_ROOT && ./gradlew :runtime-disruptor:test --tests 'DisruptorEventBusStressTest' --quiet" \
    "$REPORT_DIR/event-bus-stress-tests.log"

# Test 1.4: Event Bus Dedup Tests
run_test "EventBus Dedup Tests" \
    "cd $PROJECT_ROOT && ./gradlew :runtime-disruptor:test --tests 'DisruptorEventBusDedupTest' --quiet" \
    "$REPORT_DIR/event-bus-dedup-tests.log"

# ========================================
# Phase 2: Composition Root Verification
# ========================================
echo ""
echo "Phase 2: Composition Root Verification"
echo "-----------------------------------------"

# Test 2.1: CLI Broker Session
run_test "CLI Broker Composition" \
    "cd $PROJECT_ROOT && ./gradlew :cli:run --args='broker validate --broker dhan' --quiet 2>&1 | head -20" \
    "$REPORT_DIR/cli-broker-composition.log"

# Test 2.2: FullComposition Creation
run_test "FullComposition Test" \
    "cd $PROJECT_ROOT && ./gradlew :composition:test --tests '*CompositionTest' --quiet" \
    "$REPORT_DIR/composition-tests.log"

# ========================================
# Phase 3: Broker Certification (Live)
# ========================================
echo ""
echo "Phase 3: Broker Runtime Certification"
echo "-----------------------------------------"

# Test 3.1: Dhan Broker REST Tests
if [ -f "$PROJECT_ROOT/config/dhan-local.properties" ]; then
    run_test "Dhan REST Certification" \
        "cd $PROJECT_ROOT && ./gradlew :app:brokerRestTest --quiet 2>&1 | tail -30" \
        "$REPORT_DIR/dhan-rest-certification.log"
else
    echo -e "${YELLOW}SKIP${NC}: Dhan credentials not found"
    skip_count=$((skip_count + 1))
fi

# Test 3.2: Dhan WebSocket Tests
run_test "Dhan WebSocket Certification" \
    "cd $PROJECT_ROOT && ./gradlew :app:brokerWsTest --quiet 2>&1 | tail -30" \
    "$REPORT_DIR/dhan-ws-certification.log"

# Test 3.3: Broker Order Tests
run_test "Broker Order Certification" \
    "cd $PROJECT_ROOT && ./gradlew :app:brokerOrderTest --quiet 2>&1 | tail -30" \
    "$REPORT_DIR/broker-order-certification.log"

# ========================================
# Phase 4: Data Integrity Verification
# ========================================
echo ""
echo "Phase 4: Data Integrity Verification"
echo "-----------------------------------------"

# Test 4.1: DuckDB Event Store Tests
run_test "DuckDB Event Store Tests" \
    "cd $PROJECT_ROOT && ./gradlew :data-persistence:test --tests '*DuckDbEventStoreTest' --quiet" \
    "$REPORT_DIR/duckdb-event-store-tests.log"

# Test 4.2: Chronicle WAL Tests
run_test "Chronicle WAL Tests" \
    "cd $PROJECT_ROOT && ./gradlew :data-persistence:test --tests '*ChronicleEventWalTest' --quiet" \
    "$REPORT_DIR/chronicle-wal-tests.log"

# ========================================
# Phase 5: Replay Determinism
# ========================================
echo ""
echo "Phase 5: Replay Determinism Verification"
echo "-----------------------------------------"

# Test 5.1: Replay Parity Tests
run_test "Replay Parity Tests" \
    "cd $PROJECT_ROOT && ./gradlew :runtime-disruptor:test --tests 'DisruptorGraphReplayParityTest' --quiet" \
    "$REPORT_DIR/replay-parity-tests.log"

# ========================================
# Phase 6: Integration Tests
# ========================================
echo ""
echo "Phase 6: Runtime Integration Tests"
echo "-----------------------------------------"

# Test 6.1: Runtime E2E Tests
run_test "Runtime E2E Tests" \
    "cd $PROJECT_ROOT && ./gradlew :app:runtimeE2eTest --quiet 2>&1 | tail -30" \
    "$REPORT_DIR/runtime-e2e-tests.log"

# Test 6.2: Cross-Layer Tests
run_test "Cross-Layer Integration Tests" \
    "cd $PROJECT_ROOT && ./gradlew :app:crossLayerRegressionTest --quiet 2>&1 | tail -30" \
    "$REPORT_DIR/cross-layer-tests.log"

# ========================================
# Phase 7: Architecture Tests
# ========================================
echo ""
echo "Phase 7: Architecture Verification"
echo "-----------------------------------------"

run_test "Architecture Tests" \
    "cd $PROJECT_ROOT && ./gradlew :architecture-test:test --quiet" \
    "$REPORT_DIR/architecture-tests.log"

# ========================================
# Generate Summary Report
# ========================================
echo ""
echo "========================================="
echo "Runtime Verification Summary"
echo "========================================="
echo -e "Passed: ${GREEN}$pass_count${NC}"
echo -e "Failed: ${RED}$fail_count${NC}"
echo -e "Skipped: ${YELLOW}$skip_count${NC}"
echo ""

# Create JSON summary
cat > "$REPORT_DIR/summary.json" << EOF
{
  "timestamp": "$TIMESTAMP",
  "total_tests": $((pass_count + fail_count + skip_count)),
  "passed": $pass_count,
  "failed": $fail_count,
  "skipped": $skip_count,
  "pass_rate": $(echo "scale=2; $pass_count * 100 / ($pass_count + $fail_count)" | bc 2>/dev/null || echo "N/A"),
  "reports_directory": "$REPORT_DIR"
}
EOF

echo "Summary saved to: $REPORT_DIR/summary.json"
echo ""

if [ $fail_count -gt 0 ]; then
    echo -e "${RED}Some tests failed. Check logs in: $REPORT_DIR${NC}"
    exit 1
else
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
fi
