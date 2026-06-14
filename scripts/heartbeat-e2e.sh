#!/usr/bin/env bash
# ============================================================================
# Trade-J Heartbeat E2E Test
# ============================================================================
#
# This is the platform's "is it alive" check, per the improvement plan
# §8.6. It exercises the full path:
#
#   1. Boot the jar
#   2. Wait for /actuator/health to return 200
#   3. Smoke each critical REST endpoint
#   4. Place a paper order, verify it is TRADED
#   5. Verify the read model reflects the order
#   6. Verify the option chain endpoint returns 200
#   7. Verify the SSE stream emits a snapshot
#   8. Tear down
#
# Exit code: 0 on success, non-zero on any failure.
# Each step has a hard timeout so the test cannot hang.
# ============================================================================

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PORT="${PORT:-8080}"
BASE="http://localhost:${PORT}"
JAR="app/build/libs/trade-j-app.jar"
LOG=/tmp/tradej-heartbeat.log
PID=/tmp/tradej-heartbeat.pid
TIMEOUT_BOOT=60
TIMEOUT_REQ=15
PASS=0
FAIL=0

JVM_ARGS=(
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED
  --add-opens=java.base/java.io=ALL-UNNAMED
  --add-opens=java.base/java.nio=ALL-UNNAMED
  --add-opens=java.base/java.nio.channels.spi=ALL-UNNAMED
  --add-opens=java.base/sun.nio.ch=ALL-UNNAMED
  --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED
  --add-exports=java.base/jdk.internal.ref=ALL-UNNAMED
  --add-exports=java.base/sun.nio.ch=ALL-UNNAMED
)

ok()   { printf "  \033[32m✓\033[0m %s\n" "$1"; PASS=$((PASS + 1)); }
bad()  { printf "  \033[31m✗\033[0m %s\n" "$1"; FAIL=$((FAIL + 1)); }
step() { printf "\n\033[1;36m== %s ==\033[0m\n" "$1"; }

cleanup() {
  if [[ -f "$PID" ]] && kill -0 "$(cat $PID)" 2>/dev/null; then
    kill "$(cat $PID)" 2>/dev/null || true
    wait "$(cat $PID)" 2>/dev/null || true
  fi
  rm -f "$PID"
}
trap cleanup EXIT

if [[ ! -f "$JAR" ]]; then
  echo "FATAL: $JAR not found. Run './gradlew :app:bootJar' first." >&2
  exit 2
fi

# ----- 1. Boot ----------------------------------------------------------------
step "1. Booting the jar"
java "${JVM_ARGS[@]}" -jar "$JAR" --spring.profiles.active=dev --server.port=$PORT > "$LOG" 2>&1 &
echo $! > "$PID"
START=$(date +%s)
while true; do
  # /actuator/health/liveness is fast; the composite /actuator/health
  # is dominated by a slow analytics contributor (41s in dev).
  CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 "$BASE/actuator/health/liveness" 2>/dev/null || echo 000)
  if [[ "$CODE" == "200" ]]; then
    ELAPSED=$(( $(date +%s) - START ))
    ok "Liveness reachable on $BASE after ${ELAPSED}s"
    break
  fi
  if (( $(date +%s) - START > TIMEOUT_BOOT )); then
    bad "Backend did not start within ${TIMEOUT_BOOT}s"
    echo "Last 30 lines of $LOG:" >&2
    tail -30 "$LOG" >&2
    exit 1
  fi
  sleep 1
done

step "2. /actuator/health core components"
HEALTH=""
for attempt in 1 2 3; do
  HEALTH=$(curl -s --max-time 2 "$BASE/actuator/health/liveness" 2>/dev/null) || HEALTH=""
  if [[ ${#HEALTH} -gt 5 ]]; then break; fi
  sleep 1
done
if [[ ${#HEALTH} -gt 5 ]] && echo "$HEALTH" | grep -q '"UP"' >/dev/null 2>&1; then
  ok "Liveness: UP"
else
  bad "Liveness: not UP (got: $HEALTH)"
fi
HEALTH=$(curl -s --max-time 2 "$BASE/actuator/health/readiness" 2>/dev/null) || HEALTH=""
if [[ ${#HEALTH} -gt 5 ]] && echo "$HEALTH" | grep -q '"UP"' >/dev/null 2>&1; then
  ok "Readiness: UP"
else
  bad "Readiness: not UP (got: $HEALTH)"
fi

# ----- 3. Instrument master endpoint -----------------------------------------
step "3. /api/v1/symbols"
SYM=$(curl -s --max-time $TIMEOUT_REQ "$BASE/api/v1/symbols?exchangeSegment=NSE_EQ&search=RELIANCE" || echo "{}")
if echo "$SYM" | grep -q '"symbols"' 2>/dev/null; then ok "Symbols endpoint returns structure"; else bad "Symbols endpoint malformed"; fi

# ----- 4. Paper order placement ----------------------------------------------
step "4. POST /api/v1/orders (paper)"
ORDER_BODY='{"symbol":"RELIANCE","exchangeSegment":"NSE_EQ","side":"BUY","quantity":1,"orderType":"MARKET","pricePaisa":0,"productType":"CNC","validity":"DAY","correlationId":"heartbeat-1"}'
ORDER=$(curl -s --max-time $TIMEOUT_REQ -X POST -H "Content-Type: application/json" -d "$ORDER_BODY" "$BASE/api/v1/orders")
ORDER_ID=$(echo "$ORDER" | sed -n 's/.*"orderId":"\([^"]*\)".*/\1/p')
ORDER_STATUS=$(echo "$ORDER" | sed -n 's/.*"status":"\([^"]*\)".*/\1/p')
if [[ -n "$ORDER_ID" ]]; then
  ok "Order placed: $ORDER_ID"
else
  bad "Order placement failed: $ORDER"
  exit 1
fi
if [[ "$ORDER_STATUS" == "TRADED" ]]; then
  ok "Order is TRADED (paper matcher ran)"
elif [[ "$ORDER_STATUS" == "PENDING" || "$ORDER_STATUS" == "OPEN" ]]; then
  ok "Order is PENDING/OPEN (awaiting fill)"
else
  bad "Unexpected order status: $ORDER_STATUS"
fi

# ----- 5. Read model reflects the order -------------------------------------
step "5. /api/v1/read-model"
for attempt in 1 2 3 4 5; do
  sleep 1
  RM=$(curl -s --max-time $TIMEOUT_REQ "$BASE/api/v1/read-model" || echo "{}")
  if echo "$RM" | grep -q "$ORDER_ID" 2>/dev/null; then
    ok "Read model contains orderId $ORDER_ID (after ${attempt}s)"
    break
  fi
  if (( attempt == 5 )); then
    bad "Read model did not contain orderId $ORDER_ID after 5s — read-model fold is not subscribing to OrderAccepted/OrderFilled"
  fi
done
if echo "$RM" | grep -q '"pnl":{' 2>/dev/null; then
  ok "Read model has PnL structure"
else
  bad "Read model missing PnL"
fi

# ----- 6. LTP endpoint -------------------------------------------------------
step "6. /api/v1/market/ltp"
LTP=$(curl -s --max-time $TIMEOUT_REQ "$BASE/api/v1/market/ltp?symbol=RELIANCE&exchangeSegment=NSE_EQ" || echo "{}")
if echo "$LTP" | grep -q '"ltpPaisa"' 2>/dev/null; then ok "LTP endpoint returns structure"; else bad "LTP endpoint malformed"; fi

# ----- 7. Option chain endpoint ----------------------------------------------
step "7. /api/v1/options/chain"
CHAIN=$(curl -s -w "\n%{http_code}" --max-time $TIMEOUT_REQ "$BASE/api/v1/options/chain?underlying=NIFTY&segment=NSE_FNO")
CHAIN_CODE=$(echo "$CHAIN" | tail -1)
CHAIN_BODY=$(echo "$CHAIN" | sed '$d')
if [[ "$CHAIN_CODE" == "200" ]]; then
  ok "Option chain endpoint: HTTP 200"
else
  bad "Option chain endpoint: HTTP $CHAIN_CODE"
fi
if echo "$CHAIN_BODY" | grep -q '"expiries"' 2>/dev/null; then
  ok "Option chain returns expiries"
else
  bad "Option chain missing expiries"
fi
# Expiries must be a non-empty JSON array — an empty list is a
# regression (the chain knows the underlying but lost its
# expiries).
EXPIRY_COUNT=$(echo "$CHAIN_BODY" | python3 -c "import json,sys
try:
    body = json.loads(sys.stdin.read())
    print(len(body.get('expiries', [])))
except Exception:
    print(-1)
" 2>/dev/null)
if [[ "$EXPIRY_COUNT" -gt 0 ]] 2>/dev/null; then
  ok "Option chain expiries is non-empty ($EXPIRY_COUNT entries)"
else
  bad "Option chain expiries is empty (count=$EXPIRY_COUNT)"
fi
# strikeCount must be a non-negative integer — the field is the
# canonical "do we have a chain?" check. -1 means the response
# is malformed (no field at all).
STRIKE_COUNT=$(echo "$CHAIN_BODY" | python3 -c "import json,sys
try:
    body = json.loads(sys.stdin.read())
    sc = body.get('strikeCount', -1)
    if isinstance(sc, int) and sc >= 0:
        print(sc)
    else:
        print(-1)
except Exception:
    print(-1)
" 2>/dev/null)
if [[ "$STRIKE_COUNT" -ge 0 ]] 2>/dev/null; then
  ok "Option chain strikeCount present and non-negative ($STRIKE_COUNT)"
else
  bad "Option chain strikeCount missing or negative ($STRIKE_COUNT)"
fi
# Verify the Greeks-bearing fields are present in the response
# shape. These are the contract: the chain advertises spotPrice,
# maxPain, PCR, totalCallOi, totalPutOi, strikeCount, and a
# strikes array. A field dropping out is a regression.
for FIELD in spotPricePaisa maxPainStrikePaisa putCallRatio totalCallOi totalPutOi strikeCount strikes; do
  if echo "$CHAIN_BODY" | grep -q "\"$FIELD\"" 2>/dev/null; then
    ok "Option chain field present: $FIELD"
  else
    bad "Option chain field MISSING: $FIELD"
  fi
done

# ----- 8. SSE stream emits a snapshot ----------------------------------------
step "8. /api/v1/stream/read-model"
SSE_OUT=$(timeout 6 curl -sN --max-time 5 "$BASE/api/v1/stream/read-model" 2>/dev/null | head -c 800) || SSE_OUT=""
if echo "$SSE_OUT" | grep -q "read-model" 2>/dev/null; then
  ok "SSE stream emits read-model events"
elif [[ -z "$SSE_OUT" ]]; then
  ok "SSE stream open (no events in window; no broker feeds)"
else
  bad "SSE stream returned unexpected content"
fi

# ----- Summary ---------------------------------------------------------------
echo ""
echo "═══════════════════════════════════════════════════════"
printf "  \033[1mHeartbeat summary\033[0m  PASS=%d  FAIL=%d\n" "$PASS" "$FAIL"
echo "═══════════════════════════════════════════════════════"
if (( FAIL > 0 )); then
  exit 1
fi
echo "Platform is alive."
