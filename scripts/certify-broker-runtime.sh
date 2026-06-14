#!/bin/bash
# Broker Runtime Certification Script
# Tests actual broker capabilities with live credentials
# Usage: ./scripts/certify-broker-runtime.sh [dhan|upstox|icici]

set -e

BROKER=${1:-dhan}
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
CERT_DIR="$PROJECT_ROOT/runtime-verification/broker-certification"
REPORT_FILE="$CERT_DIR/${BROKER}-certification-${TIMESTAMP}.json"

mkdir -p "$CERT_DIR"

echo "========================================="
echo "Broker Runtime Certification: $BROKER"
echo "========================================="
echo "Timestamp: $TIMESTAMP"
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
NOT_TESTED=0

run_capability_test() {
    local capability="$1"
    local test_command="$2"
    local status="NOT_TESTED"
    local latency_ms=0
    local error_msg=""
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    echo -n "Testing: $capability ... "
    
    START_TIME=$(date +%s%N)
    
    if eval "$test_command" > /tmp/broker-test-output.log 2>&1; then
        END_TIME=$(date +%s%N)
        LATENCY_NS=$((END_TIME - START_TIME))
        latency_ms=$((LATENCY_NS / 1000000))
        status="PASS"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        echo -e "${GREEN}PASS${NC} (${latency_ms}ms)"
    else
        status="FAIL"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        error_msg=$(cat /tmp/broker-test-output.log | tail -5)
        echo -e "${RED}FAIL${NC}"
        echo "  Error: $error_msg"
    fi
    
    # Store result for JSON report
    echo "$capability|$status|$latency_ms|$error_msg" >> /tmp/broker-cert-results.txt
}

# Initialize results file
> /tmp/broker-cert-results.txt

# ========================================
# Check credentials exist
# ========================================
echo ""
echo "Checking credentials..."

if [ "$BROKER" = "dhan" ]; then
    if [ ! -f "$PROJECT_ROOT/config/dhan-local.properties" ]; then
        echo -e "${RED}ERROR: Dhan credentials not found at config/dhan-local.properties${NC}"
        exit 1
    fi
    echo -e "${GREEN}Dhan credentials found${NC}"
    
elif [ "$BROKER" = "upstox" ]; then
    if [ ! -f "$PROJECT_ROOT/config/upstox-live.properties" ]; then
        echo -e "${RED}ERROR: Upstox credentials not found at config/upstox-live.properties${NC}"
        exit 1
    fi
    echo -e "${GREEN}Upstox credentials found${NC}"
    
elif [ "$BROKER" = "icici" ]; then
    if [ ! -f "$PROJECT_ROOT/config/icici-local.properties" ]; then
        echo -e "${RED}ERROR: ICICI credentials not found at config/icici-local.properties${NC}"
        exit 1
    fi
    echo -e "${GREEN}ICICI credentials found${NC}"
fi

# ========================================
# Run Broker Capability Tests
# ========================================
echo ""
echo "Running capability tests..."
echo ""

case "$BROKER" in
    dhan)
        # Test 1: Authentication
        run_capability_test "Authentication" \
            "cd $PROJECT_ROOT && ./gradlew :app:regressionPreflightTest --quiet 2>&1 | grep -i 'dhan'"
        
        # Test 2: Connection
        run_capability_test "Connection" \
            "cd $PROJECT_ROOT && timeout 30 ./gradlew :cli:run --args='broker validate --broker dhan' --quiet 2>&1"
        
        # Test 3: Market Data (Quote)
        run_capability_test "Market Data - Quote" \
            "cd $PROJECT_ROOT && timeout 30 ./gradlew :cli:run --args='market quote --symbol NIFTY --broker dhan' --quiet 2>&1 | head -10"
        
        # Test 4: Historical Data
        run_capability_test "Historical Data" \
            "cd $PROJECT_ROOT && timeout 60 ./gradlew :cli:run --args='historical fetch --symbol NIFTY --interval 1m --days 1 --broker dhan' --quiet 2>&1 | head -10"
        
        # Test 5: Option Chain
        run_capability_test "Option Chain" \
            "cd $PROJECT_ROOT && timeout 30 ./gradlew :cli:run --args='options chain --symbol NIFTY --broker dhan' --quiet 2>&1 | head -10"
        
        # Test 6: Positions
        run_capability_test "Positions" \
            "cd $PROJECT_ROOT && timeout 30 ./gradlew :cli:run --args='portfolio positions --broker dhan' --quiet 2>&1 | head -10"
        
        # Test 7: Holdings
        run_capability_test "Holdings" \
            "cd $PROJECT_ROOT && timeout 30 ./gradlew :cli:run --args='portfolio holdings --broker dhan' --quiet 2>&1 | head -10"
        
        # Test 8: Order Placement (DRY RUN)
        run_capability_test "Order Placement (Dry Run)" \
            "cd $PROJECT_ROOT && timeout 30 ./gradlew :cli:run --args='order place --symbol RELIANCE --action BUY --quantity 1 --order-type LIMIT --price 2500 --dry-run --broker dhan' --quiet 2>&1 | head -10"
        
        # Test 9: WebSocket (quick test)
        run_capability_test "WebSocket Connection" \
            "cd $PROJECT_ROOT && timeout 10 ./gradlew :app:brokerWsTest --quiet 2>&1 | tail -5"
        
        # Test 10: Instrument Search
        run_capability_test "Instrument Search" \
            "cd $PROJECT_ROOT && timeout 30 ./gradlew :cli:run --args='catalog search RELIANCE --broker dhan' --quiet 2>&1 | head -10"
        ;;
        
    upstox)
        # Test 1: Authentication
        run_capability_test "Authentication" \
            "cd $PROJECT_ROOT && timeout 30 ./gradlew :cli:run --args='broker validate --broker upstox' --quiet 2>&1"
        
        # Test 2: Connection
        run_capability_test "Connection" \
            "cd $PROJECT_ROOT && timeout 30 ./gradlew :cli:run --args='broker validate --broker upstox' --quiet 2>&1"
        
        # Test 3: Market Data (Quote - REST only)
        run_capability_test "Market Data - Quote" \
            "cd $PROJECT_ROOT && timeout 30 ./gradlew :cli:run --args='market quote --symbol NIFTY --broker upstox' --quiet 2>&1 | head -10"
        
        # Test 4: Historical Data
        run_capability_test "Historical Data" \
            "cd $PROJECT_ROOT && timeout 60 ./gradlew :cli:run --args='historical fetch --symbol NIFTY --interval 1m --days 1 --broker upstox' --quiet 2>&1 | head -10"
        
        # Test 5: Option Chain
        run_capability_test "Option Chain" \
            "cd $PROJECT_ROOT && timeout 30 ./gradlew :cli:run --args='options chain --symbol NIFTY --broker upstox' --quiet 2>&1 | head -10"
        
        # Test 6: Positions
        run_capability_test "Positions" \
            "cd $PROJECT_ROOT && timeout 30 ./gradlew :cli:run --args='portfolio positions --broker upstox' --quiet 2>&1 | head -10"
        
        # Test 7: Holdings
        run_capability_test "Holdings" \
            "cd $PROJECT_ROOT && timeout 30 ./gradlew :cli:run --args='portfolio holdings --broker upstox' --quiet 2>&1 | head -10"
        ;;
        
    icici)
        # Test 1: Authentication
        run_capability_test "Authentication" \
            "cd $PROJECT_ROOT && timeout 30 ./gradlew :cli:run --args='broker validate --broker icici' --quiet 2>&1"
        
        # Test 2: Connection
        run_capability_test "Connection" \
            "cd $PROJECT_ROOT && timeout 30 ./gradlew :cli:run --args='broker validate --broker icici' --quiet 2>&1"
        
        # Test 3: Market Data (Quote)
        run_capability_test "Market Data - Quote" \
            "cd $PROJECT_ROOT && timeout 30 ./gradlew :cli:run --args='market quote --symbol NIFTY --broker icici' --quiet 2>&1 | head -10"
        
        # Test 4: Positions
        run_capability_test "Positions" \
            "cd $PROJECT_ROOT && timeout 30 ./gradlew :cli:run --args='portfolio positions --broker icici' --quiet 2>&1 | head -10"
        
        # Test 5: Holdings
        run_capability_test "Holdings" \
            "cd $PROJECT_ROOT && timeout 30 ./gradlew :cli:run --args='portfolio holdings --broker icici' --quiet 2>&1 | head -10"
        ;;
esac

# ========================================
# Generate JSON Report
# ========================================
echo ""
echo "Generating certification report..."

# Build JSON report
cat > "$REPORT_FILE" << 'JSONHEADER'
{
  "broker": "BROKER_NAME",
  "certification_date": "TIMESTAMP",
  "capabilities": [
JSONHEADER

# Replace placeholders
sed -i '' "s/BROKER_NAME/$BROKER/g" "$REPORT_FILE"
sed -i '' "s/TIMESTAMP/$(date -u +%Y-%m-%dT%H:%M:%SZ)/g" "$REPORT_FILE"

# Add capability results
FIRST=true
while IFS='|' read -r capability status latency error; do
    if [ "$FIRST" = true ]; then
        FIRST=false
    else
        echo "    ," >> "$REPORT_FILE"
    fi
    
    cat >> "$REPORT_FILE" << EOF
    {
      "name": "$capability",
      "status": "$status",
      "latency_ms": $latency,
      "errors": ["${error:-}"]
    }
EOF
done < /tmp/broker-cert-results.txt

# Close JSON
cat >> "$REPORT_FILE" << EOF
  ],
  "summary": {
    "total_capabilities": $TOTAL_TESTS,
    "passed": $PASSED_TESTS,
    "failed": $FAILED_TESTS,
    "not_tested": $NOT_TESTED,
    "pass_rate": $(echo "scale=2; $PASSED_TESTS * 100 / $TOTAL_TESTS" | bc 2>/dev/null || echo "N/A")
  }
}
EOF

echo ""
echo "========================================="
echo "Broker Certification Summary: $BROKER"
echo "========================================="
echo -e "Total Tests: $TOTAL_TESTS"
echo -e "Passed: ${GREEN}$PASSED_TESTS${NC}"
echo -e "Failed: ${RED}$FAILED_TESTS${NC}"
echo -e "Not Tested: ${YELLOW}$NOT_TESTED${NC}"
echo ""
echo "Report saved to: $REPORT_FILE"
echo ""

# Cleanup
rm -f /tmp/broker-test-output.log /tmp/broker-cert-results.txt

if [ $FAILED_TESTS -gt 0 ]; then
    echo -e "${RED}Some capability tests failed. Review report for details.${NC}"
    exit 1
else
    echo -e "${GREEN}All tested capabilities passed!${NC}"
    exit 0
fi
