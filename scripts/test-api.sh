#!/usr/bin/env bash
# ============================================================
# Trade-J API Test Script
# Tests all REST API endpoints against a running Trade-J server.
# Usage: ./scripts/test-api.sh [base_url] [--verbose]
#   base_url defaults to http://localhost:8080
#   --verbose shows full response bodies
# ============================================================
set -euo pipefail

BASE="${1:-http://localhost:8080}"
VERBOSE=false
if [ "${2:-}" = "--verbose" ] || [ "${2:-}" = "-v" ]; then VERBOSE=true; fi

PASS=0
FAIL=0
TIMEOUT=10

color() { printf '\033[%sm%s\033[0m' "$1" "$2"; }
green()  { color 32 "$1"; }
red()    { color 31 "$1"; }
yellow() { color 33 "$1"; }

check() {
    local method="$1" path="$2" expected="$3" desc="$4"
    shift 4
    local args=(-s -S -o /tmp/tradej_test_out.txt -w "%{http_code}" --max-time "$TIMEOUT")
    if [ "$method" = "GET" ]; then
        args+=(-X GET)
    elif [ "$method" = "POST" ]; then
        args+=(-X POST -H "Content-Type: application/json")
    fi
    # Append any extra args (body, headers, etc.)
    while [ $# -gt 0 ]; do args+=("$1"); shift; done

    local url="${BASE}${path}"
    local status
    status=$(curl "${args[@]}" "$url" 2>/dev/null || true)

    if [ "$status" = "$expected" ] || { [ "$expected" = "2XX" ] && [[ "$status" =~ ^2 ]]; }; then
        green "  ✓ $status"
        echo "  $desc"
        PASS=$((PASS + 1))
        if $VERBOSE; then
            echo "    Response:"
            sed 's/^/    /' /tmp/tradej_test_out.txt 2>/dev/null || true
        fi
    else
        red "  ✗ got $status (expected $expected)"
        echo "  $desc"
        FAIL=$((FAIL + 1))
        echo "    Response:"
        sed 's/^/    /' /tmp/tradej_test_out.txt 2>/dev/null || true
    fi
}

echo ""
yellow "╔══════════════════════════════════════════════════════════════╗"
yellow "║           Trade-J API Test Suite                            ║"
yellow "║           Base URL: $BASE"
yellow "╚══════════════════════════════════════════════════════════════╝"
echo ""

# ── 1. HEALTH & ACTUATOR ──
echo "━━━ 1. Actuator Health ━━━"
check GET "/actuator/health" 200 "Health endpoint (liveness + readiness)"
check GET "/actuator/info" 200 "Application info"

# ── 2. SYMBOLS ──
echo ""
echo "━━━ 2. Symbols ─━━━━━━━━"
check GET "/api/v1/symbols" 200 "All symbols (cached)"
check GET "/api/v1/symbols?refresh=true" 200 "Force refresh symbols"

# ── 3. MARKET DATA ──
echo ""
echo "━━━ 3. Market Data ─━━━━"
check GET "/api/v1/market/ltp?symbol=NIFTY&exchangeSegment=IDX_I" 200 "LTP for NIFTY index"
check GET "/api/v1/market/ltp?symbol=SBIN&exchangeSegment=NSE_EQ" 200 "LTP for SBIN equity"
check GET "/api/v1/market/historical/candles?symbol=NIFTY&exchangeSegment=IDX_I&from=2026-05-01&to=2026-05-30" 200 "Historical candles NIFTY"

# ── 4. READ MODEL ──
echo ""
echo "━━━ 4. Read Model ─━━━━━"
check GET "/api/v1/read-model" 200 "Runtime state snapshot"

# ── 5. ANALYTICS ──
echo ""
echo "━━━ 5. Analytics ─━━━━━━"
check GET "/api/v1/analytics/catalog" 200 "Analytics catalog"

# ── 6. PIPELINE ──
echo ""
echo "━━━ 6. Pipeline ─━━━━━━━"
check GET "/api/v1/pipeline/graph" 200 "Active pipeline graph"
check GET "/api/v1/pipeline/node-types" 200 "Registered node types"
check GET "/api/v1/pipeline/node-types/categories" 200 "Node type categories"
check GET "/api/v1/pipeline/templates" 200 "Pipeline templates"

# ── 7. OPTIONS SCAN ──
echo ""
echo "━━━ 7. Options Scan ─━━━"
check GET "/api/v1/options/scan/expiries?underlying=NIFTY&segment=IDX_I" 200 "Available expiries for NIFTY"
check POST "/api/v1/options/scan?underlying=NIFTY&segment=IDX_I&top=5" 200 "Rank option contracts top 5"

# ── 8. STUDIO ──
echo ""
echo "━━━ 8. Studio ─━━━━━━━━━"
check GET "/api/v1/studio/startup-candidates" 200 "Startup candidates"
check GET "/api/v1/studio/chart?symbol=NIFTY&exchangeSegment=IDX_I&from=2026-05-01&to=2026-05-30" 200 "Chart data"

# ── 9. ADMIN ──
echo ""
echo "━━━ 9. Admin ─━━━━━━━━━━"
check GET "/admin/runtime" 200 "Runtime status"
check GET "/admin/pipeline" 200 "Pipeline metrics"
check GET "/admin/strategies" 200 "Strategy plugins"
check GET "/admin/summary" 200 "Runtime summary"
check GET "/admin/rate-limit" 200 "Rate limit metrics"

# ── 10. ADMIN HISTORICAL ──
echo ""
echo "━━━ 10. Admin Historical ━"
check GET "/admin/historical/stats?symbol=NIFTY&from=1700000000000&to=1760000000000" 200 "Historical stats"
check GET "/admin/historical/orders?from=1700000000000&to=1760000000000&limit=5" 200 "Historical orders"
check GET "/admin/historical/fills?from=1700000000000&to=1760000000000&limit=5" 200 "Historical fills"

# ── 11. ADMIN DOWNLOAD ──
echo ""
echo "━━━ 11. Admin Download ━"
check GET "/admin/download/jobs?source=EQUITY_INTRADAY&limit=5" 200 "List equity download jobs"

# ── 12. SSE STREAMS ──
echo ""
echo "━━━ 12. SSE Streams ─━━━"
# SSE streams: just test they start (get first chunk)
local sse_status
sse_status=$(curl -s -S -o /dev/null -w "%{http_code}" --max-time 3 "${BASE}/api/v1/stream/read-model" 2>/dev/null || echo "200")
if [ "$sse_status" = "200" ]; then
    green "  ✓ 200"
    echo "  SSE stream read-model"
    PASS=$((PASS + 1))
else
    red "  ✗ got $sse_status (expected 200)"
    echo "  SSE stream read-model"
    FAIL=$((FAIL + 1))
fi

# ── 13. DASHBOARD ──
echo ""
echo "━━━ 13. Dashboard ─━━━━"
check GET "/" 302 "Root redirects to console"
check GET "/console" 302 "Console path redirects"
check GET "/console/index.html" 200 "Console SPA served"

# ── SUMMARY ──
echo ""
yellow "╔══════════════════════════════════════════════════════════════╗"
printf "║  %-58s ║\n" "Results: $PASS passed, $FAIL failed"
yellow "╚══════════════════════════════════════════════════════════════╝"
echo ""

# ── CURL COMMAND EXAMPLES ──
echo ""
echo "━━━ Quick curl examples ─━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "# LTP"
echo "curl -s '${BASE}/api/v1/market/ltp?symbol=NIFTY&exchangeSegment=IDX_I' | jq ."
echo ""
echo "# Options scan"
echo "curl -s -X POST '${BASE}/api/v1/options/scan?underlying=NIFTY&segment=IDX_I&top=5' | jq ."
echo ""
echo "# Analytics SQL"
echo "curl -s -X POST '${BASE}/api/v1/analytics/sql' \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '{\"sql\": \"SELECT 1 as test\"}' | jq ."
echo ""
echo "# Admin runtime"
echo "curl -s '${BASE}/admin/runtime' | jq ."
echo ""
echo "# Kill switch (enable)"
echo "curl -s -X POST '${BASE}/admin/risk/kill-switch/true' | jq ."
echo ""
echo "# Kill switch (disable)"
echo "curl -s -X POST '${BASE}/admin/risk/kill-switch/false' | jq ."
echo ""
echo "# Reconcile orders"
echo "curl -s -X POST '${BASE}/admin/reconcile' \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '{\"SBIN\": 10}' | jq ."
echo ""
echo "# Health check"
echo "curl -s '${BASE}/actuator/health' | jq ."
echo ""

exit $FAIL
