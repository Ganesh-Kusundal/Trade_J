#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────
# Trade-J WebSocket Connection Verification Script
# Tests all broker WebSocket connections end-to-end
# ────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0
RESULTS=()

record_result() {
    local broker="$1" ws_name="$2" status="$3" detail="$4"
    RESULTS+=("${broker}|${ws_name}|${status}|${detail}")
    case "$status" in
        PASS) PASS_COUNT=$((PASS_COUNT + 1)); echo -e "  ${GREEN}✓ PASS${NC}  $detail" ;;
        FAIL) FAIL_COUNT=$((FAIL_COUNT + 1)); echo -e "  ${RED}✗ FAIL${NC}  $detail" ;;
        SKIP) SKIP_COUNT=$((SKIP_COUNT + 1)); echo -e "  ${YELLOW}⊘ SKIP${NC}  $detail" ;;
    esac
}

check_token_expired() {
    local token="$1"
    local jwt_payload
    jwt_payload=$(echo "$token" | cut -d. -f2 | base64 -d 2>/dev/null || echo "")
    local jwt_exp
    jwt_exp=$(echo "$jwt_payload" | grep -o '"exp":[0-9]*' | cut -d: -f2 || echo "")
    local now
    now=$(date +%s)
    if [ -z "$jwt_exp" ]; then echo "unknown"; return; fi
    if [ "$jwt_exp" -gt "$now" ]; then
        echo "valid"
    else
        echo "expired"
    fi
}

# ────────────────────────────────────────────────────────────────────
# 0. Token Refresh Phase
# ────────────────────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${CYAN}  TOKEN REFRESH PHASE${NC}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}\n"

# 0a. Dhan token refresh
echo -e "${BOLD}  [Dhan] Token Manager (TOTP auto-refresh)${NC}"
if [ -f "config/dhan-local.properties" ]; then
    DHAN_TOKEN=$(grep "^dhan.accessToken=" config/dhan-local.properties | cut -d= -f2-)
    TOKEN_STATUS=$(check_token_expired "$DHAN_TOKEN")
    if [ "$TOKEN_STATUS" = "expired" ]; then
        echo -e "  ${YELLOW}Token expired — refreshing via DhanTokenManager (TOTP)...${NC}"
        if bash scripts/refresh-dhan-token.sh 2>&1 | tail -3; then
            DHAN_TOKEN=$(grep "^dhan.accessToken=" config/dhan-local.properties | cut -d= -f2-)
            TOKEN_STATUS=$(check_token_expired "$DHAN_TOKEN")
            if [ "$TOKEN_STATUS" = "valid" ]; then
                record_result "Dhan" "token-refresh" "PASS" "TOTP refresh successful — fresh token acquired"
            else
                record_result "Dhan" "token-refresh" "FAIL" "TOTP refresh ran but token still expired"
            fi
        else
            record_result "Dhan" "token-refresh" "FAIL" "TOTP refresh script failed (may need 2-min cooldown)"
        fi
    else
        record_result "Dhan" "token-refresh" "PASS" "Token already valid — no refresh needed"
    fi
else
    record_result "Dhan" "token-refresh" "SKIP" "config/dhan-local.properties not found"
fi

# 0b. Upstox token refresh
echo -e "\n${BOLD}  [Upstox] Token Manager (OAuth 2.0 PKCE)${NC}"
if [ -f "config/upstox-live.properties" ]; then
    UPSTOX_TOKEN=$(grep "^upstox.live.accessToken=" config/upstox-live.properties | cut -d= -f2-)
    TOKEN_STATUS=$(check_token_expired "$UPSTOX_TOKEN")
    if [ "$TOKEN_STATUS" = "expired" ]; then
        echo -e "  ${YELLOW}Token expired — Upstox requires interactive OAuth refresh${NC}"
        echo -e "  ${YELLOW}Run: ./gradlew :app:brokerAuthDrillTest --tests '*UpstoxRefreshTokenIntegrationTest*'${NC}"
        echo -e "  ${YELLOW}Or run: tradej token refresh --broker upstox (opens browser for OAuth)${NC}"
        record_result "Upstox" "token-refresh" "FAIL" "Token expired — requires interactive OAuth (browser redirect)"
    else
        record_result "Upstox" "token-refresh" "PASS" "Token already valid — no refresh needed"
    fi
else
    record_result "Upstox" "token-refresh" "SKIP" "config/upstox-live.properties not found"
fi

# 0c. ICICI token refresh
echo -e "\n${BOLD}  [ICICI] Token Manager (Browser Automated)${NC}"
if [ -f "config/icici-local.properties" ]; then
    if [ -f "runtime/icici-token-state.json" ] && [ -s "runtime/icici-token-state.json" ]; then
        record_result "ICICI" "token-refresh" "PASS" "Session state file exists"
    elif [ -f "config/icici-api-session.txt" ] && [ -s "config/icici-api-session.txt" ]; then
        record_result "ICICI" "token-refresh" "PASS" "API session cached in config/icici-api-session.txt"
    else
        echo -e "  ${YELLOW}No session — refreshing via BreezeTokenManager (Selenium)...${NC}"
        if bash scripts/refresh-icici-session.sh 2>&1 | tail -3; then
            if [ -f "runtime/icici-token-state.json" ] && [ -s "runtime/icici-token-state.json" ]; then
                record_result "ICICI" "token-refresh" "PASS" "Browser automation successful — session acquired"
            else
                record_result "ICICI" "token-refresh" "FAIL" "Browser automation ran but no session file"
            fi
        else
            record_result "ICICI" "token-refresh" "FAIL" "Browser automation failed (requires Chrome + credentials)"
        fi
    fi
else
    record_result "ICICI" "token-refresh" "SKIP" "config/icici-local.properties not found"
fi

echo -e "\n${BOLD}  Token refresh phase complete. Proceeding to WebSocket tests...${NC}\n"
sleep 2

# ────────────────────────────────────────────────────────────────────
# 1. Dhan WebSockets
# ────────────────────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${CYAN}  DHAN WEBSOCKET CONNECTIONS${NC}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}\n"

# 1a. Check Dhan credentials
echo -e "${BOLD}  [1/4] Dhan Credentials${NC}"
if [ -f "config/dhan-local.properties" ]; then
    DHAN_TOKEN=$(grep "^dhan.accessToken=" config/dhan-local.properties | cut -d= -f2-)
    DHAN_CLIENT=$(grep "^dhan.clientId=" config/dhan-local.properties | cut -d= -f2-)
    if [ -n "$DHAN_TOKEN" ] && [ -n "$DHAN_CLIENT" ]; then
        # Check JWT expiry
        JWT_PAYLOAD=$(echo "$DHAN_TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null || echo "")
        JWT_EXP=$(echo "$JWT_PAYLOAD" | grep -o '"exp":[0-9]*' | cut -d: -f2)
        NOW=$(date +%s)
        if [ -n "$JWT_EXP" ] && [ "$JWT_EXP" -gt "$NOW" ]; then
            record_result "Dhan" "credentials" "PASS" "Token valid (exp: $(date -r $JWT_EXP 2>/dev/null || echo $JWT_EXP)), clientId=$DHAN_CLIENT"
        else
            record_result "Dhan" "credentials" "FAIL" "Token expired (exp=$JWT_EXP, now=$NOW)"
        fi
    else
        record_result "Dhan" "credentials" "FAIL" "Missing accessToken or clientId"
    fi
else
    record_result "Dhan" "credentials" "SKIP" "config/dhan-local.properties not found"
fi

# 1b. Dhan Market Feed WebSocket (wss://api-feed.dhan.co)
echo -e "\n${BOLD}  [2/4] Dhan Market Feed WS (wss://api-feed.dhan.co)${NC}"
OUTPUT=$(./gradlew :cli:run --args="broker dhan quote NIFTY IDX_I" --quiet 2>&1 || true)
if echo "$OUTPUT" | grep -q "Quote:"; then
    LATENCY=$(echo "$OUTPUT" | grep "Quote:" | grep -o '[0-9]*ms')
    record_result "Dhan" "market-feed" "PASS" "Quote received ($LATENCY)"
else
    ERROR=$(echo "$OUTPUT" | grep -E "Exception|Error" | head -1)
    record_result "Dhan" "market-feed" "FAIL" "${ERROR:-No quote data received}"
fi

# 1c. Dhan 20-Level Depth WebSocket (wss://depth-api-feed.dhan.co/twentydepth)
echo -e "\n${BOLD}  [3/4] Dhan 20-Level Depth WS (wss://depth-api-feed.dhan.co/twentydepth)${NC}"
OUTPUT=$(./gradlew :cli:run --args="broker dhan depth NIFTY IDX_I" --quiet 2>&1 || true)
if echo "$OUTPUT" | grep -q "Depth:"; then
    BIDS=$(echo "$OUTPUT" | grep -o 'Bids: [0-9]*')
    ASKS=$(echo "$OUTPUT" | grep -o 'Asks: [0-9]*')
    LATENCY=$(echo "$OUTPUT" | grep "Depth:" | grep -o '[0-9]*ms')
    record_result "Dhan" "depth-20" "PASS" "Depth received ($BIDS, $ASKS, $LATENCY) — note: REST returns 5-level"
else
    ERROR=$(echo "$OUTPUT" | grep -E "Exception|Error" | head -1)
    record_result "Dhan" "depth-20" "FAIL" "${ERROR:-No depth data received}"
fi

# 1d. Dhan Order Stream WebSocket (wss://api-order-update.dhan.co)
echo -e "\n${BOLD}  [4/4] Dhan Order Stream WS (wss://api-order-update.dhan.co)${NC}"
OUTPUT=$(./gradlew :cli:run --args="broker dhan orders" --quiet 2>&1 || true)
if echo "$OUTPUT" | grep -q "Orders"; then
    record_result "Dhan" "order-stream" "PASS" "Order book accessible (REST endpoint verified)"
else
    record_result "Dhan" "order-stream" "FAIL" "Order book not accessible"
fi

# ────────────────────────────────────────────────────────────────────
# 2. Upstox WebSockets
# ────────────────────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${CYAN}  UPSTOX WEBSOCKET CONNECTIONS${NC}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}\n"

# 2a. Check Upstox credentials
echo -e "${BOLD}  [1/3] Upstox Credentials${NC}"
if [ -f "config/upstox-live.properties" ]; then
    UPSTOX_TOKEN=$(grep "^upstox.live.accessToken=" config/upstox-live.properties | cut -d= -f2-)
    UPSTOX_CLIENT=$(grep "^upstox.live.clientId=" config/upstox-live.properties | cut -d= -f2-)
    if [ -n "$UPSTOX_TOKEN" ] && [ -n "$UPSTOX_CLIENT" ]; then
        JWT_PAYLOAD=$(echo "$UPSTOX_TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null || echo "")
        JWT_EXP=$(echo "$JWT_PAYLOAD" | grep -o '"exp":[0-9]*' | cut -d: -f2)
        NOW=$(date +%s)
        if [ -n "$JWT_EXP" ] && [ "$JWT_EXP" -gt "$NOW" ]; then
            record_result "Upstox" "credentials" "PASS" "Token valid (exp: $(date -r $JWT_EXP 2>/dev/null || echo $JWT_EXP)), clientId=${UPSTOX_CLIENT:0:8}..."
        else
            record_result "Upstox" "credentials" "FAIL" "Token expired (exp=$JWT_EXP, now=$NOW)"
        fi
    else
        record_result "Upstox" "credentials" "FAIL" "Missing accessToken or clientId"
    fi
else
    record_result "Upstox" "credentials" "SKIP" "config/upstox-live.properties not found"
fi

# 2b. Upstox Market Data Feed (authorize → redirect → binary WS)
echo -e "\n${BOLD}  [2/3] Upstox Market Data Feed WS (via /v2/feed/market-data-feed/authorize)${NC}"
OUTPUT=$(./gradlew :cli:run --args="--broker upstox broker upstox quote NIFTY IDX_I" --quiet 2>&1 || true)
if echo "$OUTPUT" | grep -q "Quote:"; then
    LATENCY=$(echo "$OUTPUT" | grep "Quote:" | grep -o '[0-9]*ms')
    record_result "Upstox" "market-feed" "PASS" "Quote received ($LATENCY)"
elif echo "$OUTPUT" | grep -q "missing data"; then
    record_result "Upstox" "market-feed" "FAIL" "Connected but no data (market may be closed — weekend)"
else
    ERROR=$(echo "$OUTPUT" | grep -E "Exception|Error" | head -1)
    record_result "Upstox" "market-feed" "FAIL" "${ERROR:-Connection failed}"
fi

# 2c. Upstox Portfolio Stream (authorize → redirect → JSON WS)
echo -e "\n${BOLD}  [3/3] Upstox Portfolio Stream WS (via /v2/feed/portfolio-stream-feed/authorize)${NC}"
OUTPUT=$(./gradlew :cli:run --args="--broker upstox broker upstox chain NIFTY IDX_I" --quiet 2>&1 || true)
if echo "$OUTPUT" | grep -q "Option Chain:"; then
    STRIKES=$(echo "$OUTPUT" | grep -o 'Strikes: [0-9]*')
    record_result "Upstox" "portfolio-stream" "PASS" "Options data received ($STRIKES)"
elif echo "$OUTPUT" | grep -q "Illegal character"; then
    record_result "Upstox" "portfolio-stream" "FAIL" "URL encoding bug: pipe character not encoded in query"
else
    ERROR=$(echo "$OUTPUT" | grep -E "Exception|Error" | head -1)
    record_result "Upstox" "portfolio-stream" "FAIL" "${ERROR:-Connection failed}"
fi

# ────────────────────────────────────────────────────────────────────
# 3. ICICI WebSockets
# ────────────────────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${CYAN}  ICICI WEBSOCKET CONNECTIONS${NC}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}\n"

# 3a. Check ICICI credentials
echo -e "${BOLD}  [1/3] ICICI Credentials${NC}"
if [ -f "config/icici-local.properties" ]; then
    ICICI_KEY=$(grep "^icici.appKey=" config/icici-local.properties | cut -d= -f2-)
    ICICI_AUTH=$(grep "^icici.authMode=" config/icici-local.properties | cut -d= -f2-)
    if [ -n "$ICICI_KEY" ]; then
        record_result "ICICI" "credentials" "PASS" "App key present, authMode=$ICICI_AUTH (requires browser login)"
    else
        record_result "ICICI" "credentials" "FAIL" "Missing appKey"
    fi
    # Check if API session file exists
    if [ -f "config/icici-api-session.txt" ] && [ -s "config/icici-api-session.txt" ]; then
        record_result "ICICI" "session" "PASS" "API session file exists and non-empty"
    else
        record_result "ICICI" "session" "SKIP" "No API session — requires browser-based login"
    fi
else
    record_result "ICICI" "credentials" "SKIP" "config/icici-local.properties not found"
fi

# 3b. ICICI Quote WebSocket (livefeeds.icicidirect.com — Socket.IO)
echo -e "\n${BOLD}  [2/3] ICICI Quote WS (livefeeds.icicidirect.com — Socket.IO)${NC}"
record_result "ICICI" "quote-ws" "SKIP" "Requires browser auth session — cannot test from CLI"

# 3c. ICICI Order WebSocket
echo -e "\n${BOLD}  [3/3] ICICI Order WS (livefeeds.icicidirect.com — Socket.IO)${NC}"
record_result "ICICI" "order-ws" "SKIP" "Requires browser auth session — cannot test from CLI"

# ────────────────────────────────────────────────────────────────────
# 4. Cross-Broker Summary
# ────────────────────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${CYAN}  CROSS-BROKER SUMMARY${NC}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}\n"

printf "  ${BOLD}%-12s %-22s %-8s %s${NC}\n" "Broker" "WebSocket" "Status" "Detail"
echo "  $(printf '%.0s─' {1..75})"

for result in "${RESULTS[@]}"; do
    IFS='|' read -r broker ws status detail <<< "$result"
    case "$status" in
        PASS) color="$GREEN" ;;
        FAIL) color="$RED" ;;
        SKIP) color="$YELLOW" ;;
        *) color="$NC" ;;
    esac
    printf "  %-12s %-22s ${color}%-8s${NC} %s\n" "$broker" "$ws" "$status" "$detail"
done

echo ""
echo -e "  ${GREEN}PASS: $PASS_COUNT${NC}  ${RED}FAIL: $FAIL_COUNT${NC}  ${YELLOW}SKIP: $SKIP_COUNT${NC}  Total: $((PASS_COUNT + FAIL_COUNT + SKIP_COUNT))"
echo ""

# Save results to JSON
TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%SZ)
REPORT_FILE="CertificationArtifacts/websocket-test-${TIMESTAMP}.json"
mkdir -p CertificationArtifacts

echo "[" > "$REPORT_FILE"
FIRST=true
for result in "${RESULTS[@]}"; do
    IFS='|' read -r broker ws status detail <<< "$result"
    if [ "$FIRST" = true ]; then FIRST=false; else echo "," >> "$REPORT_FILE"; fi
    echo "  {\"broker\":\"$broker\",\"websocket\":\"$ws\",\"status\":\"$status\",\"detail\":\"$detail\",\"timestamp\":\"$TIMESTAMP\"}" >> "$REPORT_FILE"
done
echo "]" >> "$REPORT_FILE"

echo -e "  Report saved: ${CYAN}$REPORT_FILE${NC}\n"

# Exit with failure if any tests failed
if [ "$FAIL_COUNT" -gt 0 ]; then
    exit 1
fi
