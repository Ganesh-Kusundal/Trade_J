#!/bin/bash
# Test MCX Commodity Options - Gold, Silver, CrudeOil
# Verifies option chains and live data subscription

set -e

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
BROKER="dhan"

echo "============================================"
echo "  MCX Commodity Options Test"
echo "============================================"
echo ""
echo "Gateway: $GATEWAY_URL"
echo "Broker: $BROKER"
echo "Commodities: GOLD, SILVER, CRUDEOIL"
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if gateway is running
echo -n "Checking gateway status... "
if curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/api/health" | grep -q "200"; then
    echo -e "${GREEN}✓ Gateway is running${NC}"
else
    echo -e "${RED}✗ Gateway is not running${NC}"
    echo "Please start the gateway first: ./gradlew :app:bootRun"
    exit 1
fi

echo ""
echo "============================================"
echo "  Test 1: MCX Market Hours Check"
echo "============================================"
echo ""

# MCX market hours: 09:00 - 23:30 IST (with break 17:00-17:30)
CURRENT_HOUR=$(date -u +%H)
IST_HOUR=$(( (CURRENT_HOUR + 5) % 24 ))

echo "Current IST time: $IST_HOUR:$(date -u +%M)"
echo "MCX Market Hours: 09:00 - 23:30 IST"
echo ""

if [ $IST_HOUR -ge 9 ] && [ $IST_HOUR -lt 23 ]; then
    echo -e "${GREEN}✓ MCX market is likely OPEN${NC}"
    MARKET_OPEN=true
elif [ $IST_HOUR -eq 23 ] && [ $(date -u +%M) -lt 30 ]; then
    echo -e "${GREEN}✓ MCX market is likely OPEN (closing soon)${NC}"
    MARKET_OPEN=true
else
    echo -e "${YELLOW}⚠ MCX market is likely CLOSED${NC}"
    MARKET_OPEN=false
fi

echo ""
echo "============================================"
echo "  Test 2: Check Instrument Catalog Loaded"
echo "============================================"
echo ""

echo "Checking if MCX instruments are available..."
echo ""

# Try to get MCX instrument data
RESPONSE=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/api/v1/broker/$BROKER/marketdata/quote/GOLD/MCX_COMM" \
    -H "Accept: application/json" 2>/dev/null || echo "000")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ GOLD instrument found${NC}"
    echo "$BODY" | jq -r '.data.symbol // empty' 2>/dev/null && echo "  Symbol: $(echo "$BODY" | jq -r '.data.symbol')"
    echo "$BODY" | jq -r '.data.ltp // empty' 2>/dev/null && echo "  LTP: $(echo "$BODY" | jq -r '.data.ltp')"
elif [ "$HTTP_CODE" = "404" ]; then
    echo -e "${YELLOW}⚠ Instrument catalog may not be loaded${NC}"
    echo "  Load catalog: POST /api/v1/broker/$BROKER/instruments/load"
else
    echo -e "${RED}✗ Failed to query GOLD instrument (HTTP $HTTP_CODE)${NC}"
fi

echo ""
echo "============================================"
echo "  Test 3: MCX Option Chains"
echo "============================================"
echo ""

for COMMODITY in GOLD SILVER CRUDEOIL; do
    echo "--- $COMMODITY ---"
    echo ""
    
    # Get option expiries
    echo -n "  Fetching expiries... "
    EXPIRY_RESPONSE=$(curl -s -w "\n%{http_code}" \
        "$GATEWAY_URL/api/v1/broker/$BROKER/options/expiries/$COMMODITY/MCX_COMM" \
        -H "Accept: application/json" 2>/dev/null || echo "000")
    
    EXPIRY_CODE=$(echo "$EXPIRY_RESPONSE" | tail -n1)
    
    if [ "$EXPIRY_CODE" = "200" ]; then
        echo -e "${GREEN}✓ Success${NC}"
        
        # Get first expiry
        FIRST_EXPIRY=$(echo "$EXPIRY_RESPONSE" | sed '$d' | jq -r '.data[0] // empty' 2>/dev/null)
        
        if [ -n "$FIRST_EXPIRY" ] && [ "$FIRST_EXPIRY" != "null" ]; then
            echo "  Nearest expiry: $FIRST_EXPIRY"
            echo ""
            
            # Get option chain
            echo -n "  Fetching option chain... "
            CHAIN_RESPONSE=$(curl -s -w "\n%{http_code}" \
                "$GATEWAY_URL/api/v1/broker/$BROKER/options/chain/$COMMODITY/MCX_COMM/$FIRST_EXPIRY" \
                -H "Accept: application/json" 2>/dev/null || echo "000")
            
            CHAIN_CODE=$(echo "$CHAIN_RESPONSE" | tail -n1)
            
            if [ "$CHAIN_CODE" = "200" ]; then
                echo -e "${GREEN}✓ Success${NC}"
                
                # Extract key metrics
                SPOT_PRICE=$(echo "$CHAIN_RESPONSE" | sed '$d' | jq -r '.data.spotPricePaisa // empty' 2>/dev/null)
                STRIKES_COUNT=$(echo "$CHAIN_RESPONSE" | sed '$d' | jq -r '.data.strikes | length // empty' 2>/dev/null)
                
                if [ -n "$SPOT_PRICE" ]; then
                    echo "  Spot price: ₹$(echo "scale=2; $SPOT_PRICE / 100" | bc)"
                fi
                
                if [ -n "$STRIKES_COUNT" ]; then
                    echo "  Strikes available: $STRIKES_COUNT"
                    
                    # Count contracts with data
                    CONTRACTS_WITH_DATA=$(echo "$CHAIN_RESPONSE" | sed '$d' | jq '
                        [.data.strikes[] | 
                         select(.call != null or .put != null)] | length
                    ' 2>/dev/null || echo "0")
                    
                    echo "  Contracts with live data: $CONTRACTS_WITH_DATA"
                fi
            else
                echo -e "${RED}✗ Failed (HTTP $CHAIN_CODE)${NC}"
            fi
        else
            echo -e "${YELLOW}⚠ No expiries available${NC}"
        fi
    else
        echo -e "${RED}✗ Failed (HTTP $EXPIRY_CODE)${NC}"
    fi
    
    echo ""
done

echo "============================================"
echo "  Test 4: OI Analysis"
echo "============================================"
echo ""

echo "Calculating total OI for each commodity..."
echo ""

for COMMODITY in GOLD SILVER CRUDEOIL; do
    echo -n "  $COMMODITY OI: "
    
    # Get nearest expiry
    EXPIRY_RESPONSE=$(curl -s \
        "$GATEWAY_URL/api/v1/broker/$BROKER/options/expiries/$COMMODITY/MCX_COMM" \
        -H "Accept: application/json" 2>/dev/null)
    
    FIRST_EXPIRY=$(echo "$EXPIRY_RESPONSE" | jq -r '.data[0] // empty' 2>/dev/null)
    
    if [ -n "$FIRST_EXPIRY" ] && [ "$FIRST_EXPIRY" != "null" ]; then
        # Get option chain and calculate OI
        CHAIN_RESPONSE=$(curl -s \
            "$GATEWAY_URL/api/v1/broker/$BROKER/options/chain/$COMMODITY/MCX_COMM/$FIRST_EXPIRY" \
            -H "Accept: application/json" 2>/dev/null)
        
        TOTAL_OI=$(echo "$CHAIN_RESPONSE" | jq '
            [.data.strikes[] | 
             ((.call.openInterest // 0) + (.put.openInterest // 0))] | add // 0
        ' 2>/dev/null || echo "0")
        
        if [ "$TOTAL_OI" != "0" ] && [ -n "$TOTAL_OI" ]; then
            echo -e "${GREEN}${TOTAL_OI} contracts${NC}"
        else
            echo -e "${YELLOW}No OI data available${NC}"
        fi
    else
        echo -e "${YELLOW}No expiry data${NC}"
    fi
done

echo ""
echo "============================================"
echo "  Test 5: Live Data Subscription (WebSocket)"
echo "============================================"
echo ""

echo "WebSocket subscription requires a running client."
echo ""
echo "To test manually, use the Java integration test:"
echo "  ./gradlew :app:test --tests \"McxCommodityOptionsIntegrationTest.subscribesToMcxCommoditiesInFullMode\""
echo ""
echo "Or use wscat:"
echo "  wscat -c ws://localhost:8080/ws/marketdata"
echo "  Then subscribe to: GOLD, SILVER, CRUDEOIL in FULL mode"
echo ""

echo "============================================"
echo "  Summary"
echo "============================================"
echo ""

if [ "$MARKET_OPEN" = true ]; then
    echo -e "${GREEN}✓ MCX market is OPEN${NC}"
    echo "  All endpoints should be returning live data"
else
    echo -e "${YELLOW}⚠ MCX market is CLOSED${NC}"
    echo "  Option chains may not have live prices"
    echo "  But structure should still be available"
fi

echo ""
echo "Next Steps:"
echo "  1. Run integration test: ./gradlew :app:test --tests \"McxCommodityOptionsIntegrationTest\""
echo "  2. Check logs for FULL mode subscription details"
echo "  3. Verify real-time updates in WebSocket listener"
echo ""
