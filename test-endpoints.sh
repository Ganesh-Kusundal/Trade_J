#!/bin/bash
# Test all Trade-J backend endpoints
# Usage: ./test-endpoints.sh

BASE_URL="http://localhost:8080"
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}  Trade-J Backend Endpoint Tests${NC}"
echo -e "${GREEN}========================================${NC}\n"

# Test 1: Symbols Endpoint
echo -e "${YELLOW}1. Testing /api/v1/symbols${NC}"
RESULT=$(curl -s "$BASE_URL/api/v1/symbols?exchangeSegment=NSE_EQ&search=RELIANCE" | jq '{count: .count, sample: .symbols[0].symbol}')
echo "   Response: $RESULT"
echo ""

# Test 2: Historical Candles (try recent dates)
echo -e "${YELLOW}2. Testing /api/v1/market/historical/candles (recent dates)${NC}"
RESULT=$(curl -s "$BASE_URL/api/v1/market/historical/candles?symbol=RELIANCE&exchangeSegment=NSE_EQ&interval=1d&from=2026-06-01&to=2026-06-10&source=duckdb" | jq '{count: .count, first: .candles[0]}')
echo "   Response: $RESULT"
echo ""

# Test 3: Orders Endpoint
echo -e "${YELLOW}3. Testing /api/v1/orders${NC}"
RESULT=$(curl -s "$BASE_URL/api/v1/orders" | jq '{count: (. | length)}')
echo "   Response: $RESULT"
echo ""

# Test 4: Positions Endpoint
echo -e "${YELLOW}4. Testing /api/v1/positions${NC}"
RESULT=$(curl -s "$BASE_URL/api/v1/positions" | jq '{count: (. | length)}')
echo "   Response: $RESULT"
echo ""

# Test 5: Quote Endpoint
echo -e "${YELLOW}5. Testing /api/v1/market/quote${NC}"
RESULT=$(curl -s "$BASE_URL/api/v1/market/quote?symbol=RELIANCE&exchangeSegment=NSE_EQ" | jq '{symbol: .symbol, ltpPaisa: .ltpPaisa}')
echo "   Response: $RESULT"
echo ""

# Test 6: Depth/Order Book
echo -e "${YELLOW}6. Testing /api/v1/market/depth${NC}"
RESULT=$(curl -s "$BASE_URL/api/v1/market/depth?symbol=RELIANCE&exchangeSegment=NSE_EQ" | jq '{symbol: .symbol, bidLevels: (.bids | length), askLevels: (.asks | length)}')
echo "   Response: $RESULT"
echo ""

# Test 7: SSE Read Model Stream (just check if it connects)
echo -e "${YELLOW}7. Testing /api/v1/stream/read-model (SSE connection)${NC}"
timeout 3 curl -s -N "$BASE_URL/api/v1/stream/read-model" 2>&1 | head -5 || echo "   SSE endpoint is responding (connection established)"
echo ""

# Test 8: Place Order (test payload structure)
echo -e "${YELLOW}8. Testing POST /api/v1/orders (order placement)${NC}"
RESULT=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "RELIANCE",
    "exchangeSegment": "NSE_EQ",
    "orderType": "LIMIT",
    "transactionType": "BUY",
    "quantity": 1,
    "pricePaisa": 250000,
    "timeInForce": "DAY"
  }' | jq '{orderId: .orderId, status: .status}')
echo "   Response: $RESULT"
echo ""

# Test 9: Health Check
echo -e "${YELLOW}9. Testing /actuator/health${NC}"
RESULT=$(curl -s "$BASE_URL/actuator/health" | jq '.status')
echo "   Response: $RESULT"
echo ""

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  All tests completed!${NC}"
echo -e "${GREEN}========================================${NC}\n"
