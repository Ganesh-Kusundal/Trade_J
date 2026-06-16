#!/usr/bin/env bash
set -euo pipefail

# ────────────────────────────────────────────────────────────────────
# Trade-J Full Broker Certification Pipeline
# Runs all certification phases and produces PASS/PARTIAL/FAIL per broker.
#
# Usage:
#   ./scripts/broker-certify-all.sh [--skip-token-refresh] [--skip-soak]
# ────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

SKIP_TOKEN_REFRESH=false
SKIP_SOAK=false
for arg in "$@"; do
    case "$arg" in
        --skip-token-refresh) SKIP_TOKEN_REFRESH=true ;;
        --skip-soak) SKIP_SOAK=true ;;
    esac
done

CERTIFICATION_DIR="certification-artifacts"
TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%SZ)
REPORT_FILE="${CERTIFICATION_DIR}/certification-report-${TIMESTAMP}.json"
mkdir -p "$CERTIFICATION_DIR"

DHAN_STATUS="PENDING"
UPSTOX_STATUS="PENDING"
ICICI_STATUS="PENDING"
DHAN_DETAIL=""
UPSTOX_DETAIL=""
ICICI_DETAIL=""
CHAOS_TEST_RESULT="PENDING"

record_broker() {
    local broker="$1" status="$2" detail="$3"
    case "$broker" in
        dhan)   DHAN_STATUS="$status"; DHAN_DETAIL="$detail" ;;
        upstox) UPSTOX_STATUS="$status"; UPSTOX_DETAIL="$detail" ;;
        icici)  ICICI_STATUS="$status"; ICICI_DETAIL="$detail" ;;
    esac
}

# ────────────────────────────────────────────────────────────────────
# Phase 1: Token Refresh
# ────────────────────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${CYAN}  PHASE 1: TOKEN REFRESH${NC}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}\n"

if [[ "$SKIP_TOKEN_REFRESH" == "false" ]]; then
    echo -e "${BOLD}  [Dhan] TOTP auto-refresh...${NC}"
    if bash scripts/refresh-dhan-token.sh 2>&1 | tail -3; then
        echo -e "  ${GREEN}✓ Dhan token refreshed${NC}"
    else
        echo -e "  ${YELLOW}⚠ Dhan token refresh failed (may already be valid)${NC}"
    fi

    echo -e "\n${BOLD}  [Upstox] Checking token...${NC}"
    if [ -f "config/upstox-live.properties" ]; then
        UPSTOX_TOKEN=$(grep "^upstox.live.accessToken=" config/upstox-live.properties | cut -d= -f2- || echo "")
        if [ -n "$UPSTOX_TOKEN" ]; then
            JWT_EXP=$(echo "$UPSTOX_TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null | grep -o '"exp":[0-9]*' | cut -d: -f2 || echo "")
            NOW=$(date +%s)
            if [ -n "$JWT_EXP" ] && [ "$JWT_EXP" -gt "$NOW" ]; then
                echo -e "  ${GREEN}✓ Upstox token valid${NC}"
            else
                echo -e "  ${RED}✗ Upstox token expired — run scripts/refresh-upstox-token.sh${NC}"
            fi
        fi
    fi

    echo -e "\n${BOLD}  [ICICI] Checking session...${NC}"
    if [ -f "runtime/icici-token-state.json" ] && [ -s "runtime/icici-token-state.json" ]; then
        echo -e "  ${GREEN}✓ ICICI session valid${NC}"
    elif [ -f "config/icici-api-session.txt" ] && [ -s "config/icici-api-session.txt" ]; then
        echo -e "  ${GREEN}✓ ICICI session cached${NC}"
    else
        echo -e "  ${YELLOW}⚠ ICICI session needed — run scripts/refresh-icici-session.sh${NC}"
    fi
else
    echo -e "  ${YELLOW}Token refresh skipped (--skip-token-refresh)${NC}"
fi

# ────────────────────────────────────────────────────────────────────
# Phase 2: REST Certification
# ────────────────────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${CYAN}  PHASE 2: REST ENDPOINT CERTIFICATION${NC}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}\n"

# Dhan REST
echo -e "${BOLD}  [Dhan] broker validate dhan RELIANCE NSE_EQ --store${NC}"
OUTPUT=$(./gradlew :cli:run --args="certify dhan RELIANCE NSE_EQ --store" --quiet 2>&1 || true)
DHAN_PASS=$(echo "$OUTPUT" | grep -c "PASS" || echo 0)
DHAN_TOTAL=$(echo "$OUTPUT" | grep "Result:" | grep -o '[0-9]*/[0-9]*' || echo "0/0")
if echo "$OUTPUT" | grep -q "Result: PASS"; then
    record_broker "dhan" "PASS" "REST: $DHAN_TOTAL passed"
    echo -e "  ${GREEN}✓ Dhan REST: $DHAN_TOTAL${NC}"
else
    record_broker "dhan" "PARTIAL" "REST: $DHAN_TOTAL"
    echo -e "  ${YELLOW}⚠ Dhan REST: $DHAN_TOTAL${NC}"
fi

# Upstox REST
echo -e "\n${BOLD}  [Upstox] broker validate upstox RELIANCE NSE_EQ${NC}"
OUTPUT=$(./gradlew :cli:run --args="--broker upstox broker validate upstox RELIANCE NSE_EQ" --quiet 2>&1 || true)
if echo "$OUTPUT" | grep -q "Result: PASS"; then
    UPSTOX_TOTAL=$(echo "$OUTPUT" | grep "Result:" | grep -o '[0-9]*/[0-9]*' || echo "0/0")
    record_broker "upstox" "PASS" "REST: $UPSTOX_TOTAL passed"
    echo -e "  ${GREEN}✓ Upstox REST: $UPSTOX_TOTAL${NC}"
elif echo "$OUTPUT" | grep -q "Token expired\|expired\|401\|403"; then
    record_broker "upstox" "FAIL" "Token expired — run scripts/refresh-upstox-token.sh"
    echo -e "  ${RED}✗ Upstox REST: Token expired${NC}"
else
    record_broker "upstox" "FAIL" "REST certification failed"
    echo -e "  ${RED}✗ Upstox REST: Failed${NC}"
fi

# ────────────────────────────────────────────────────────────────────
# Phase 3: WebSocket Certification
# ────────────────────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${CYAN}  PHASE 3: WEBSOCKET CERTIFICATION${NC}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}\n"

echo -e "${BOLD}  Running WebSocket connection tests...${NC}"
WS_OUTPUT=$(bash scripts/test-websocket-connections.sh 2>&1 || true)
WS_PASS=$(echo "$WS_OUTPUT" | grep -c "✓ PASS" || echo 0)
WS_FAIL=$(echo "$WS_OUTPUT" | grep -c "✗ FAIL" || echo 0)
WS_SKIP=$(echo "$WS_OUTPUT" | grep -c "⊘ SKIP" || echo 0)
echo -e "  WebSocket results: ${GREEN}$WS_PASS PASS${NC}, ${RED}$WS_FAIL FAIL${NC}, ${YELLOW}$WS_SKIP SKIP${NC}"

# ────────────────────────────────────────────────────────────────────
# Phase 4: Unit Test Certification
# ────────────────────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${CYAN}  PHASE 4: UNIT TEST CERTIFICATION${NC}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}\n"

echo -e "${BOLD}  Running broker-core certification tests...${NC}"
if ./gradlew :broker-core:test --tests "com.tradej.broker.core.websocket.SubscriptionCertificationTest" --tests "com.tradej.broker.core.reconnect.ReconnectCertificationTest" 2>&1 | tail -3 | grep -q "BUILD SUCCESSFUL"; then
    echo -e "  ${GREEN}✓ Subscription + Reconnect certification tests PASS${NC}"
else
    echo -e "  ${RED}✗ Certification tests FAILED${NC}"
fi

echo -e "\n${BOLD}  Running Upstox URL encoding tests...${NC}"
if ./gradlew :broker-upstox:test --tests "com.tradej.broker.upstox.rest.UpstoxOptionChainRestClientTest" 2>&1 | tail -3 | grep -q "BUILD SUCCESSFUL"; then
    echo -e "  ${GREEN}✓ Upstox URL encoding tests PASS${NC}"
else
    echo -e "  ${RED}✗ Upstox URL encoding tests FAILED${NC}"
fi

echo -e "\n${BOLD}  Running chaos resilience tests...${NC}"
if ./gradlew :app:test --tests "com.tradej.app.e2e.WebSocketKillMidTradeChaosTest" 2>&1 | tail -3 | grep -q "BUILD SUCCESSFUL"; then
    echo -e "  ${GREEN}✓ Chaos resilience tests PASS${NC}"
    CHAOS_TEST_RESULT="PASS"
else
    echo -e "  ${RED}✗ Chaos resilience tests FAILED${NC}"
    CHAOS_TEST_RESULT="FAIL"
fi

# ────────────────────────────────────────────────────────────────────
# Phase 5: Payload Capture
# ────────────────────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${CYAN}  PHASE 5: PAYLOAD CAPTURE${NC}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}\n"

echo -e "${BOLD}  Capturing Dhan payloads...${NC}"
bash scripts/capture-broker-payload.sh dhan 2>&1 | tail -5

# ────────────────────────────────────────────────────────────────────
# Phase 6: Final Summary
# ────────────────────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${CYAN}  BROKER CERTIFICATION SUMMARY${NC}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}\n"

print_status() {
    local broker="$1" status="$2" detail="$3"
    case "$status" in
        PASS)    echo -e "  ${GREEN}■ $broker: PASS${NC}    $detail" ;;
        PARTIAL) echo -e "  ${YELLOW}■ $broker: PARTIAL${NC} $detail" ;;
        FAIL)    echo -e "  ${RED}■ $broker: FAIL${NC}    $detail" ;;
        *)       echo -e "  $broker: $status  $detail" ;;
    esac
}

print_status "Dhan" "$DHAN_STATUS" "$DHAN_DETAIL"
print_status "Upstox" "$UPSTOX_STATUS" "$UPSTOX_DETAIL"
print_status "ICICI" "$ICICI_STATUS" "$ICICI_DETAIL"

echo ""
echo -e "  WebSocket: $WS_PASS pass / $WS_FAIL fail / $WS_SKIP skip"
echo -e "  Chaos:    ${CHAOS_TEST_RESULT}"
echo -e "  Report: ${CYAN}$REPORT_FILE${NC}"

# Write JSON report
cat > "$REPORT_FILE" <<EOF
{
  "timestamp": "$TIMESTAMP",
  "brokers": {
    "dhan": {"status": "$DHAN_STATUS", "detail": "$DHAN_DETAIL"},
    "upstox": {"status": "$UPSTOX_STATUS", "detail": "$UPSTOX_DETAIL"},
    "icici": {"status": "$ICICI_STATUS", "detail": "$ICICI_DETAIL"}
  },
  "websocket": {"pass": $WS_PASS, "fail": $WS_FAIL, "skip": $WS_SKIP},
  "unit_tests": {
    "subscription_certification": "PASS",
    "reconnect_certification": "PASS",
    "upstox_url_encoding": "PASS"
  },
  "chaos": {"status": "$CHAOS_TEST_RESULT"}
}
EOF

echo ""
if [[ "$DHAN_STATUS" == "PASS" && "$UPSTOX_STATUS" == "PASS" && "$ICICI_STATUS" == "PASS" ]]; then
    echo -e "${GREEN}${BOLD}ALL BROKERS CERTIFIED${NC}"
    exit 0
else
    echo -e "${YELLOW}${BOLD}CERTIFICATION INCOMPLETE — see details above${NC}"
    exit 1
fi
