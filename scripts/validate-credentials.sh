#!/usr/bin/env bash
# ============================================================================
# Trade-J Broker Credential Validation Script
# ============================================================================
# Validates connectivity and token freshness for all brokers
# Exit codes: 0 = ALL PASS, 1 = SOME FAIL
# ============================================================================

set -euo pipefail

WORKSPACE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$WORKSPACE_ROOT"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  Trade-J Broker Credential Validation${NC}"
echo -e "${BOLD}  Timestamp: $TIMESTAMP${NC}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════════${NC}"
echo ""

OVERALL="PASS"

check_jwt_expiry() {
    local token="$1"
    local broker="$2"
    
    if [ -z "$token" ]; then
        echo -e "  ${RED}✗ $broker: No access token found${NC}"
        return 1
    fi
    
    local exp
    exp=$(echo "$token" | cut -d. -f2 | base64 -d 2>/dev/null | grep -o '"exp":[0-9]*' | cut -d: -f2 || echo "")
    
    if [ -z "$exp" ]; then
        echo -e "  ${YELLOW}⚠ $broker: Cannot parse token expiry (non-standard JWT)${NC}"
        return 0
    fi
    
    local now
    now=$(date +%s)
    local diff=$(( exp - now ))
    local hours=$(( diff / 3600 ))
    local mins=$(( (diff % 3600) / 60 ))
    
    if [ "$exp" -gt "$now" ]; then
        echo -e "  ${GREEN}✓ $broker: Token valid (expires in ${hours}h ${mins}m)${NC}"
        return 0
    else
        echo -e "  ${RED}✗ $broker: Token EXPIRED (${hours}h ago) — refresh needed${NC}"
        return 1
    fi
}

echo -e "${BOLD}── Dhan Live ──${NC}"
DHAN_STATUS="FAIL"
DHAN_DETAIL=""

if [ -f "config/dhan-local.properties" ]; then
    # Check token state file first (primary source), then properties file (fallback)
    DHAN_TOKEN_STATE="runtime/dhan-token-state.json"
    DHAN_TOKEN=""
    TOKEN_SOURCE=""
    
    if [ -f "$DHAN_TOKEN_STATE" ] && [ -s "$DHAN_TOKEN_STATE" ]; then
        # Use token state file (primary source - has current valid token)
        DHAN_TOKEN=$(python3 -c "import json; print(json.load(open('$DHAN_TOKEN_STATE'))['accessToken'])" 2>/dev/null || echo "")
        DHAN_EXP_MS=$(python3 -c "import json; print(json.load(open('$DHAN_TOKEN_STATE'))['expiryEpochMs'])" 2>/dev/null || echo "")
        TOKEN_SOURCE="token state file"
        
        echo -e "  ${CYAN}ℹ Using token from $TOKEN_SOURCE${NC}"
        
        if [ -n "$DHAN_TOKEN" ] && [ -n "$DHAN_EXP_MS" ]; then
            # Convert ms to seconds
            DHAN_EXP=$((DHAN_EXP_MS / 1000))
            NOW=$(date +%s)
            DIFF=$((DHAN_EXP - NOW))
            HOURS=$((DIFF / 3600))
            MINS=$(((DIFF % 3600) / 60))
            EXPIRY_DT=$(date -u -d @"$DHAN_EXP" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -r "$DHAN_EXP" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || echo "unknown")
            
            if [ "$DHAN_EXP" -gt "$NOW" ]; then
                echo -e "  ${GREEN}✓ Dhan Live: Token valid (expires in ${HOURS}h ${MINS}m at $EXPIRY_DT)${NC}"
                DHAN_STATUS="PASS"
                DHAN_DETAIL="Token valid (from $TOKEN_SOURCE)"
            else
                echo -e "  ${RED}✗ Dhan Live: Token EXPIRED at $EXPIRY_DT${NC}"
                DHAN_STATUS="FAIL"
                DHAN_DETAIL="Token expired — run ./scripts/refresh-dhan-token.sh"
                OVERALL="FAIL"
            fi
        else
            echo -e "  ${RED}✗ Dhan Live: Cannot read token state file${NC}"
            DHAN_STATUS="FAIL"
            DHAN_DETAIL="Cannot read token state"
            OVERALL="FAIL"
        fi
    else
        # Fallback to properties file
        DHAN_TOKEN=$(grep "^dhan.accessToken=" config/dhan-local.properties | cut -d= -f2- || echo "")
        TOKEN_SOURCE="properties file"
        
        echo -e "  ${YELLOW}⚠ Token state file not found, checking $TOKEN_SOURCE${NC}"
        
        if [ -n "$DHAN_TOKEN" ]; then
            if check_jwt_expiry "$DHAN_TOKEN" "Dhan Live"; then
                DHAN_STATUS="PASS"
                DHAN_DETAIL="Token valid (from $TOKEN_SOURCE)"
            else
                DHAN_STATUS="FAIL"
                DHAN_DETAIL="Token expired — run ./scripts/refresh-dhan-token.sh"
                OVERALL="FAIL"
            fi
        else
            echo -e "  ${RED}✗ Dhan Live: No access token in properties${NC}"
            DHAN_STATUS="FAIL"
            DHAN_DETAIL="No token configured"
            OVERALL="FAIL"
        fi
    fi
    
    if [ -f "config/dhan-totp-secret.txt" ] && [ -f "config/dhan-pin.txt" ]; then
        echo -e "  ${GREEN}✓ TOTP secret and PIN files present${NC}"
    else
        echo -e "  ${YELLOW}⚠ TOTP/PIN files missing (needed for auto-refresh)${NC}"
    fi
else
    echo -e "  ${RED}✗ Dhan Live: config/dhan-local.properties not found${NC}"
    DHAN_STATUS="FAIL"
    DHAN_DETAIL="Config file missing"
    OVERALL="FAIL"
fi
echo ""

echo -e "${BOLD}── Dhan Sandbox ──${NC}"
DHAN_SB_STATUS="FAIL"
DHAN_SB_DETAIL=""
if [ -f "config/dhan-sandbox.properties" ]; then
    echo -e "  ${GREEN}✓ Sandbox properties file exists${NC}"
    DHAN_SB_CLIENT=$(grep "^dhan.clientId=" config/dhan-sandbox.properties | cut -d= -f2- || echo "")
    if [ -n "$DHAN_SB_CLIENT" ]; then
        echo -e "  ${GREEN}✓ Client ID: $DHAN_SB_CLIENT${NC}"
        DHAN_SB_STATUS="PASS"
        DHAN_SB_DETAIL="Configured (clientId=$DHAN_SB_CLIENT)"
    else
        echo -e "  ${RED}✗ No client ID found${NC}"
        DHAN_SB_STATUS="FAIL"
        DHAN_SB_DETAIL="No client ID"
        OVERALL="FAIL"
    fi
else
    echo -e "  ${RED}✗ Sandbox properties file missing${NC}"
    DHAN_SB_STATUS="FAIL"
    DHAN_SB_DETAIL="Config file missing"
    OVERALL="FAIL"
fi
echo ""

echo -e "${BOLD}── Upstox Live ──${NC}"
UPSTOX_STATUS="FAIL"
UPSTOX_DETAIL=""
if [ -f "config/upstox-live.properties" ]; then
    UPSTOX_TOKEN=$(grep "^upstox.live.accessToken=" config/upstox-live.properties | cut -d= -f2- || echo "")
    UPSTOX_ANALYTICS=$(grep "^upstox.live.analyticsToken=" config/upstox-live.properties | cut -d= -f2- || echo "")
    if [ -n "$UPSTOX_TOKEN" ]; then
        if check_jwt_expiry "$UPSTOX_TOKEN" "Upstox Live"; then
            UPSTOX_STATUS="PASS"
            UPSTOX_DETAIL="Access token valid"
        else
            UPSTOX_STATUS="FAIL"
            UPSTOX_DETAIL="Token expired — run ./scripts/refresh-upstox-token.sh"
            OVERALL="FAIL"
        fi
    else
        echo -e "  ${RED}✗ Upstox Live: No access token${NC}"
        UPSTOX_STATUS="FAIL"
        UPSTOX_DETAIL="No token"
        OVERALL="FAIL"
    fi
    if [ -n "$UPSTOX_ANALYTICS" ]; then
        echo -e "  ${GREEN}✓ Analytics token present (Plus-plan)${NC}"
    else
        echo -e "  ${YELLOW}⚠ No analytics token${NC}"
    fi
else
    echo -e "  ${RED}✗ Upstox Live: config/upstox-live.properties not found${NC}"
    UPSTOX_STATUS="FAIL"
    UPSTOX_DETAIL="Config file missing"
    OVERALL="FAIL"
fi
echo ""

echo -e "${BOLD}── Upstox Sandbox ──${NC}"
UPSTOX_SB_STATUS="FAIL"
UPSTOX_SB_DETAIL=""
if [ -f "config/upstox-sandbox.properties" ]; then
    echo -e "  ${GREEN}✓ Sandbox properties file exists${NC}"
    UPSTOX_SB_STATUS="PASS"
    UPSTOX_SB_DETAIL="Config file present"
else
    echo -e "  ${YELLOW}⚠ Sandbox properties file missing (optional)${NC}"
    UPSTOX_SB_STATUS="PARTIAL"
    UPSTOX_SB_DETAIL="No sandbox config"
fi
echo ""

echo -e "${BOLD}── ICICI Breeze Live ──${NC}"
ICICI_STATUS="FAIL"
ICICI_DETAIL=""
if [ -f "config/icici-local.properties" ]; then
    echo -e "  ${GREEN}✓ ICICI properties file exists${NC}"
    ICICI_APPKEY=$(grep "^icici.appKey=" config/icici-local.properties | cut -d= -f2- || echo "")
    if [ -n "$ICICI_APPKEY" ]; then
        echo -e "  ${GREEN}✓ App key present${NC}"
    else
        echo -e "  ${RED}✗ No app key${NC}"
    fi
    AUTH_MODE=$(grep "^icici.authMode=" config/icici-local.properties | cut -d= -f2- || echo "")
    echo -e "  ${GREEN}✓ Auth mode: $AUTH_MODE${NC}"
    if [ -f "config/icici-api-session.txt" ] && [ -s "config/icici-api-session.txt" ]; then
        ICICI_SESSION=$(cat config/icici-api-session.txt | tr -d '[:space:]')
        echo -e "  ${GREEN}✓ API session: $ICICI_SESSION${NC}"
        ICICI_STATUS="PASS"
        ICICI_DETAIL="Session active ($ICICI_SESSION)"
    elif [ -f "runtime/icici-token-state.json" ] && [ -s "runtime/icici-token-state.json" ]; then
        echo -e "  ${GREEN}✓ Token state file exists${NC}"
        ICICI_STATUS="PASS"
        ICICI_DETAIL="Token state cached"
    else
        echo -e "  ${YELLOW}⚠ No active session — run ./scripts/refresh-icici-session.sh${NC}"
        ICICI_STATUS="PARTIAL"
        ICICI_DETAIL="No session (browser auth needed)"
    fi
    ORDERS_ENABLED=$(grep "^icici.ordersEnabled=" config/icici-local.properties | cut -d= -f2- || echo "false")
    if [ "$ORDERS_ENABLED" = "true" ]; then
        echo -e "  ${GREEN}✓ Orders enabled${NC}"
    else
        echo -e "  ${YELLOW}⚠ Orders disabled (analytics-only mode)${NC}"
    fi
else
    echo -e "  ${RED}✗ ICICI: config/icici-local.properties not found${NC}"
    ICICI_STATUS="FAIL"
    ICICI_DETAIL="Config file missing"
    OVERALL="FAIL"
fi
echo ""

echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  Credential Validation Summary${NC}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════════${NC}"
echo ""

print_status() {
    local broker="$1" status="$2" detail="$3"
    case "$status" in
        PASS)    echo -e "  ${GREEN}■ $broker: PASS${NC}    $detail" ;;
        PARTIAL) echo -e "  ${YELLOW}■ $broker: PARTIAL${NC} $detail" ;;
        FAIL)    echo -e "  ${RED}■ $broker: FAIL${NC}    $detail" ;;
    esac
}

print_status "Dhan Live" "$DHAN_STATUS" "$DHAN_DETAIL"
print_status "Dhan Sandbox" "$DHAN_SB_STATUS" "$DHAN_SB_DETAIL"
print_status "Upstox Live" "$UPSTOX_STATUS" "$UPSTOX_DETAIL"
print_status "Upstox Sandbox" "$UPSTOX_SB_STATUS" "$UPSTOX_SB_DETAIL"
print_status "ICICI Live" "$ICICI_STATUS" "$ICICI_DETAIL"

echo ""
if [ "$OVERALL" = "PASS" ]; then
    echo -e "${GREEN}${BOLD}ALL CREDENTIALS VALID${NC}"
    exit 0
else
    echo -e "${YELLOW}${BOLD}SOME CREDENTIALS NEED ATTENTION${NC}"
    echo ""
    echo -e "Recovery instructions:"
    if [[ "$DHAN_STATUS" == "FAIL" ]]; then
        echo -e "  ${CYAN}Dhan:${NC} ./scripts/refresh-dhan-token.sh"
    fi
    if [[ "$UPSTOX_STATUS" == "FAIL" ]]; then
        echo -e "  ${CYAN}Upstox:${NC} ./scripts/refresh-upstox-token.sh"
    fi
    if [[ "$ICICI_STATUS" == "PARTIAL" || "$ICICI_STATUS" == "FAIL" ]]; then
        echo -e "  ${CYAN}ICICI:${NC} ./scripts/refresh-icici-session.sh"
    fi
    exit 1
fi
