#!/bin/bash
# Dhan Broker Gateway - Data Retrieval Endpoints Verification
# Tests all read-only endpoints through the broker gateway

set -e

echo "=============================================="
echo "Dhan Broker Gateway - Data Retrieval Test"
echo "=============================================="
echo ""

# Configuration
BASE_URL="http://localhost:8080"
SYMBOL="RELIANCE"
EXCHANGE_SEGMENT="NSE_EQ"
INTERVAL="1d"
FROM_DATE=$(date -v-30d +%Y-%m-%d)  # 30 days ago (macOS)
TO_DATE=$(date +%Y-%m-%d)  # today

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Counter
PASSED=0
FAILED=0
SKIPPED=0

# Helper function to test endpoint
test_endpoint() {
    local description=$1
    local endpoint=$2
    local expected_status=${3:-200}
    
    echo -n "Testing: $description ... "
    
    # Make the request
    HTTP_STATUS=$(curl -s -o /tmp/response.json -w "%{http_code}" "${BASE_URL}${endpoint}" 2>/dev/null || echo "000")
    
    if [ "$HTTP_STATUS" = "$expected_status" ]; then
        echo -e "${GREEN}✓ PASS${NC} (HTTP $HTTP_STATUS)"
        PASSED=$((PASSED + 1))
        
        # Show response size
        RESPONSE_SIZE=$(wc -c < /tmp/response.json)
        echo "  Response size: ${RESPONSE_SIZE} bytes"
        
        # Pretty print first few lines of response
        if [ "$RESPONSE_SIZE" -gt 0 ]; then
            echo "  Sample response:"
            head -5 /tmp/response.json | sed 's/^/    /'
            echo "    ..."
        fi
    elif [ "$HTTP_STATUS" = "000" ]; then
        echo -e "${RED}✗ FAIL${NC} (Connection refused - is the server running?)"
        FAILED=$((FAILED + 1))
        echo "  Endpoint: $endpoint"
    else
        echo -e "${RED}✗ FAIL${NC} (HTTP $HTTP_STATUS, expected $expected_status)"
        FAILED=$((FAILED + 1))
        echo "  Endpoint: $endpoint"
        echo "  Response:"
        cat /tmp/response.json | head -10 | sed 's/^/    /'
    fi
    echo ""
}

echo "Prerequisites Check:"
echo "--------------------"
# Check if server is running
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/actuator/health" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓${NC} Server is running at ${BASE_URL}"
else
    echo -e "${RED}✗${NC} Server is NOT running at ${BASE_URL}"
    echo "  Please start the application first:"
    echo "  ./gradlew :app:bootRun"
    echo ""
    exit 1
fi
echo ""

echo "Market Data Endpoints (via Gateway):"
echo "-------------------------------------"

# 1. LTP (Last Traded Price)
test_endpoint \
    "LTP - Last Traded Price" \
    "/api/v1/market/ltp?symbol=${SYMBOL}&exchangeSegment=${EXCHANGE_SEGMENT}"

# 2. Historical Candles
test_endpoint \
    "Historical Candles (30 days, 1d interval)" \
    "/api/v1/market/historical/candles?symbol=${SYMBOL}&exchangeSegment=${EXCHANGE_SEGMENT}&interval=${INTERVAL}&from=${FROM_DATE}&to=${TO_DATE}"

# 3. Market Capabilities
test_endpoint \
    "Market Capabilities" \
    "/api/v1/market/capabilities"

# 4. Supported Intervals
test_endpoint \
    "Supported Intervals" \
    "/api/v1/market/intervals"

echo ""
echo "Additional Data Retrieval (via CLI if available):"
echo "--------------------------------------------------"

echo "Note: The following endpoints are available via BrokerHandle API:"
echo "  - Quote (full quote with OHLCV)"
echo "  - Market Depth (5-level order book)"
echo "  - OHLC Snapshot"
echo "  - Batch LTP"
echo "  - Batch Quote"
echo "  - Batch OHLC"
echo "  - Option Chain"
echo "  - Option Expiries"
echo "  - Option Greeks"
echo "  - Portfolio Balance"
echo "  - Portfolio Positions"
echo "  - Portfolio Holdings"
echo "  - Order Book (orders)"
echo "  - Trade Book (trades)"
echo "  - Margin Estimate"
echo "  - Futures Contracts"
echo "  - Instrument Catalog"
echo ""

echo "=============================================="
echo "Test Summary"
echo "=============================================="
echo -e "${GREEN}Passed:${NC}  $PASSED"
echo -e "${RED}Failed:${NC}  $FAILED"
echo -e "${YELLOW}Skipped:${NC} $SKIPPED"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    exit 1
fi
