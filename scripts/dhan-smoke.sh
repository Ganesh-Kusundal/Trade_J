#!/usr/bin/env bash
# Dhan API integration smoke test.
# Exercises Dhan REST endpoints directly against api.dhan.co using local config.
# No Spring Boot app required. Uses credentials from config/dhan-local.properties.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}"
if [ -f "${PROJECT_ROOT}/config/dhan-local.properties" ]; then
  DHAN_CLIENT_ID=$(grep -E '^dhan.clientId=' "${PROJECT_ROOT}/config/dhan-local.properties" | cut -d= -f2- | tr -d '[:space:]')
  DHAN_ACCESS_TOKEN=$(grep -E '^dhan.accessToken=' "${PROJECT_ROOT}/config/dhan-local.properties" | cut -d= -f2- | tr -d '[:space:]')
else
  echo "Missing config/dhan-local.properties"
  exit 1
fi

if [ -z "${DHAN_CLIENT_ID}" ] || [ -z "${DHAN_ACCESS_TOKEN}" ]; then
  echo "Missing dhan.clientId or dhan.accessToken in config/dhan-local.properties"
  exit 1
fi

BASE_URL="https://api.dhan.co/v2"
FAIL=0
PASS=0
TMP_BODY=$(mktemp)
trap 'rm -f "$TMP_BODY"' EXIT

check() {
  local method="$1" path="$2" expect="$3" label="$4"
  local code
  code=$(curl -s -o "$TMP_BODY" -w "%{http_code}" -X "$method" "${BASE_URL}${path}" \
    -H "Accept: application/json" \
    -H "client-id: ${DHAN_CLIENT_ID}" \
    -H "access-token: ${DHAN_ACCESS_TOKEN}" \
    || echo "000")
  if [[ "$code" != "$expect" ]]; then
    echo "FAIL [${label}] ${method} ${path} expected HTTP ${expect} got ${code}"
    head -c 300 "$TMP_BODY" 2>/dev/null || true
    echo
    FAIL=1
  else
    echo "PASS [${label}] ${method} ${path} HTTP ${code}"
    PASS=$((PASS + 1))
  fi
}

check_field() {
  local path="$1" field="$2" label="$3"
  if command -v jq >/dev/null 2>&1; then
    if curl -s "${BASE_URL}${path}" \
      -H "client-id: ${DHAN_CLIENT_ID}" \
      -H "access-token: ${DHAN_ACCESS_TOKEN}" \
      | jq -e ".${field}" >/dev/null 2>&1; then
      echo "PASS [${label}] field '${field}' present in ${path}"
      PASS=$((PASS + 1))
    else
      echo "FAIL [${label}] field '${field}' missing in ${path}"
      FAIL=1
    fi
  else
    echo "SKIP [${label}] jq not installed"
  fi
}

echo "============================================"
echo "  Dhan API Direct Smoke Test"
echo "  Target: ${BASE_URL}"
echo "============================================"
echo ""

# Core profile/fund checks
check GET "/fundlimit" "200" "fundlimit"
check_field "/fundlimit" "dhanClientId" "fundlimit-auth"

# Historical daily (NIFTY IDX_I)
check POST "/charts/historical" "200" "historical-daily" <<'EOF'
{
  "securityId": 13,
  "exchangeSegment": "IDX_I",
  "instrument": "EQUITY",
  "expiryCode": 0,
  "oi": false,
  "fromDate": "2025-06-01",
  "toDate": "2025-06-05"
}
EOF

# Intraday (NIFTY IDX_I)
check POST "/charts/intraday" "200" "historical-intraday" <<'EOF'
{
  "securityId": 13,
  "exchangeSegment": "IDX_I",
  "instrument": "EQUITY",
  "expiryCode": 0,
  "oi": false,
  "fromDate": "2025-06-05 09:15:00",
  "toDate": "2025-06-05 15:30:00",
  "interval": "1"
}
EOF

# Option chain expiry list (NIFTY)
check POST "/optionchain/expirylist" "200" "optionchain-expirylist" <<'EOF'
{
  "UnderlyingScrip": 13,
  "UnderlyingSeg": "IDX_I"
}
EOF

# Option chain (use the first available expiry from live response)
EXPIRY_JSON=$(curl -s -X POST "${BASE_URL}/optionchain/expirylist" \
  -H "content-type: application/json" \
  -H "client-id: ${DHAN_CLIENT_ID}" \
  -H "access-token: ${DHAN_ACCESS_TOKEN}" \
  -d '{"UnderlyingScrip":13,"UnderlyingSeg":"IDX_I"}')

EXPIRY=$(echo "$EXPIRY_JSON" | jq -r '.data[0] // empty' 2>/dev/null || true)
if [ -n "$EXPIRY" ]; then
  check POST "/optionchain" "200" "optionchain" <<EOF
{
  "UnderlyingScrip": 13,
  "UnderlyingSeg": "IDX_I",
  "Expiry": "${EXPIRY}"
}
EOF
  if command -v jq >/dev/null 2>&1; then
    CHAIN_BODY=$(curl -s -X POST "${BASE_URL}/optionchain" \
      -H "content-type: application/json" \
      -H "client-id: ${DHAN_CLIENT_ID}" \
      -H "access-token: ${DHAN_ACCESS_TOKEN}" \
      -d "{\"UnderlyingScrip\":13,\"UnderlyingSeg\":\"IDX_I\",\"Expiry\":\"${EXPIRY}\"}")
    STRIKE_COUNT=$(echo "$CHAIN_BODY" | jq '[.data.oc | keys[]] | length' 2>/dev/null || echo 0)
    echo "INFO [optionchain] strike count=${STRIKE_COUNT} for expiry=${EXPIRY}"
  fi
else
  echo "SKIP [optionchain] no expiry available from expirylist"
fi

# Portfolio
check GET "/holdings" "200" "holdings"
check GET "/positions" "200" "positions"

# Order list (empty list is ok)
check GET "/orders" "200" "orders"

# Trades (empty list is ok)
check GET "/trades" "200" "trades"

# Ledger
check GET "/ledger" "200" "ledger"

# Marketfeed quote (NIFTY)
check POST "/marketfeed/quote" "200" "marketfeed-quote" <<'EOF'
[
  {"securityId":"13","exchangeSegment":"IDX_I"}
]
EOF

# Margin calculator (defensive check)
check POST "/margincalculator" "200" "margincalculator" <<'EOF'
{
  "securityId": "13",
  "exchangeSegment": "IDX_I",
  "transactionType": "BUY",
  "quantity": 50,
  "productType": "MARGIN",
  "price": 23366.7
}
EOF

echo ""
echo "============================================"
echo "  Results: ${PASS} passed, ${FAIL} failed"
if [ "$FAIL" -eq 1 ]; then
  echo "  FAILURES DETECTED"
  exit 1
else
  echo "  ALL CHECKS PASSED"
fi
echo "============================================"
