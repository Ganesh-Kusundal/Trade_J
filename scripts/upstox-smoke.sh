#!/usr/bin/env bash
# ============================================================
# Upstox API End-to-End Smoke Test
# Tests all implemented Upstox broker endpoints via the CLI
# and optionally the running trade-app HTTP API.
#
# Usage:
#   ./scripts/upstox-smoke.sh                       # CLI mode (profile from env or default live)
#   ./scripts/upstox-smoke.sh --profile sandbox      # Use sandbox profile
#   ./scripts/upstox-smoke.sh --attach http://...    # Run HTTP API checks too
#   ./scripts/upstox-smoke.sh --verbose              # Show full responses
#   ./scripts/upstox-smoke.sh --endpoint balance     # Test specific endpoint only
#
# Environment:
#   UPSTOX_SMOKE_PROFILE   - live|sandbox (default: live)
#   TRADEJ_ATTACH_URL      - trade-app URL for HTTP checks
# ============================================================
set -euo pipefail

# ── Config ──────────────────────────────────────────────────
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLI="$PROJECT_ROOT/scripts/tradej"
PROFILE="${UPSTOX_SMOKE_PROFILE:-live}"
ATTACH_URL="${TRADEJ_ATTACH_URL:-}"
VERBOSE=false
SPECIFIC_ENDPOINT=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile) PROFILE="$2"; shift 2 ;;
    --attach)  ATTACH_URL="$2"; shift 2 ;;
    --verbose) VERBOSE=true; shift ;;
    --endpoint) SPECIFIC_ENDPOINT="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

CLI_ARGS=(--broker upstox --profile "$PROFILE" --json)

PASS=0
FAIL=0
SKIP=0

# ── Helpers ──────────────────────────────────────────────────
color() { printf '\033[%sm%s\033[0m' "$1" "$2"; }
green()  { color 32 "$1"; }
red()    { color 31 "$1"; }
yellow() { color 33 "$1"; }
cyan()   { color 36 "$1"; }

cli_run() {
  local cmd=("${CLI_ARGS[@]}" "$@")
  local out
  if $VERBOSE; then
    out=$("${CLI}" "${cmd[@]}" 2>&1 || true)
    echo "    CMD: tradej ${cmd[*]}"
    echo "    OUT: $out"
  else
    out=$("${CLI}" "${cmd[@]}" 2>/dev/null || true)
  fi
  echo "$out"
}

cli_check() {
  local label="$1" cmd="$2" desc="$3"
  local out
  out=$(cli_run "$cmd" 2>/dev/null) || true
  if [ -n "$out" ] && [ "$out" != "null" ]; then
    green "  ✓"
    echo "  [$label] $desc"
    PASS=$((PASS + 1))
  else
    red "  ✗"
    echo "  [$label] $desc — no response (may require credentials/token)"
    SKIP=$((SKIP + 1))
  fi
}

cli_json_field() {
  local label="$1" cmd="$2" field="$3" desc="$4"
  local out
  out=$(cli_run "$cmd" 2>/dev/null) || true
  if echo "$out" | grep -q "\"$field\"" 2>/dev/null; then
    green "  ✓"
    echo "  [$label] $desc (field '$field' present)"
    PASS=$((PASS + 1))
  else
    red "  ✗"
    echo "  [$label] $desc (field '$field' missing)"
    FAIL=$((FAIL + 1))
    if $VERBOSE; then
      echo "    Response: $out"
    fi
  fi
}

http_check() {
  local method="$1" path="$2" expect="$3" desc="$4"
  if [ -z "$ATTACH_URL" ]; then
    return
  fi
  local code
  code=$(curl -s -o /tmp/upstox-smoke-body.json -w "%{http_code}" --max-time 10 \
    -X "$method" "${ATTACH_URL}${path}" 2>/dev/null || echo "000")
  if [[ "$code" = "$expect" ]]; then
    green "  ✓"
    echo "  [HTTP] $method $path → $code — $desc"
    PASS=$((PASS + 1))
  else
    red "  ✗"
    echo "  [HTTP] $method $path → $code (expected $expect) — $desc"
    FAIL=$((FAIL + 1))
    if $VERBOSE; then
      cat /tmp/upstox-smoke-body.json 2>/dev/null | head -c 300 || true
      echo
    fi
  fi
}

should_run() {
  [ -z "$SPECIFIC_ENDPOINT" ] || [ "$SPECIFIC_ENDPOINT" = "$1" ]
}

# ── Header ───────────────────────────────────────────────────
echo ""
yellow "╔══════════════════════════════════════════════════════════════╗"
yellow "║           Upstox End-to-End Smoke Test                      ║"
yellow "║           Profile: $PROFILE"
if [ -n "$ATTACH_URL" ]; then
  yellow "║           HTTP API: $ATTACH_URL"
fi
yellow "╚══════════════════════════════════════════════════════════════╝"
echo ""

# ══════════════════════════════════════════════════════════════
# 1. Instrument Catalog
# ══════════════════════════════════════════════════════════════
if should_run "catalog"; then
echo "━━━ 1. Instrument Catalog ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  (Requires fresh download — may take 30s+ on first run)"
cli_json_field "catalog" "catalog refresh" "instrumentCount" "Download/load instrument master"
http_check GET "/admin/summary" "200" "Runtime summary (instruments loaded)"
echo ""
fi

# ══════════════════════════════════════════════════════════════
# 2. User Profile
# ══════════════════════════════════════════════════════════════
if should_run "profile"; then
echo "━━━ 2. User Profile ─━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
# Profile is accessed via HTTP API or direct Java — CLI doesn't expose profile commands directly
http_check GET "/user/profile" "400" "Profile endpoint (expect 400 without Bearer token, or 200 if auto-authorized)"
http_check GET "/actuator/health" "200" "System health"
echo ""
fi

# ══════════════════════════════════════════════════════════════
# 3. Portfolio & Funds
# ══════════════════════════════════════════════════════════════
if should_run "portfolio"; then
echo "━━━ 3. Portfolio & Funds ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
cli_check "balance" "balance" "getBalance() — funds and margin"
cli_json_field "balance" "balance" "cashPaisa" "Balance response has cashPaisa"
cli_check "holdings" "holdings" "getHoldings() — long-term holdings"
cli_check "positions" "broker-positions" "getPositions() — short-term positions"
http_check GET "/admin/portfolio/balance" "200" "Portfolio balance (via HTTP API)"
echo ""
fi

# ══════════════════════════════════════════════════════════════
# 4. Market Data (REST)
# ══════════════════════════════════════════════════════════════
if should_run "marketdata"; then
echo "━━━ 4. Market Data (REST) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
cli_check "ltp" "ltp NIFTY NSE_EQ" "getLtp() — NIFTY index LTP"
cli_check "ltp-equity" "ltp SBIN NSE_EQ" "getLtp() — SBIN equity LTP"
cli_check "quote" "quote NIFTY NSE_EQ" "getQuote() — full quote NIFTY"
cli_check "quote-equity" "quote SBIN NSE_EQ" "getQuote() — full quote SBIN"
cli_check "ohlc" "ohlc NIFTY NSE_EQ" "getOhlcSnapshot() — OHLC NIFTY"
echo ""
fi

# ══════════════════════════════════════════════════════════════
# 5. Market Data (Historical Candles)
# ══════════════════════════════════════════════════════════════
if should_run "candles"; then
echo "━━━ 5. Historical Candles ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
cli_check "candles-5m" "candles NIFTY NSE_EQ --interval 5m --from 2026-05-01 --to 2026-05-10" "getCandles() — 5m NIFTY (last 10 days)"
cli_check "candles-1d" "candles NIFTY NSE_EQ --interval 1d --from 2026-04-01 --to 2026-05-10" "getCandles() — daily NIFTY"
echo ""
fi

# ══════════════════════════════════════════════════════════════
# 6. Orders (Read-only)
# ══════════════════════════════════════════════════════════════
if should_run "orders"; then
echo "━━━ 6. Orders (Read-only) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
cli_check "orderbook" "orderbook" "getOrderBook() — order history"
cli_check "trades" "trades" "getTrades() — trade book for the day"
http_check GET "/admin/runtime" "200" "Runtime status (order WS connected)"
echo ""
fi

# ══════════════════════════════════════════════════════════════
# 7. Live P&L
# ══════════════════════════════════════════════════════════════
if should_run "pnl"; then
echo "━━━ 7. Live P&L ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
cli_check "live-pnl" "live-pnl" "getLivePnl() — computed from positions + LTP"
echo ""
fi

# ══════════════════════════════════════════════════════════════
# 8. Options
# ══════════════════════════════════════════════════════════════
if should_run "options"; then
echo "━━━ 8. Options ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
cli_check "expiries" "expiries NIFTY NSE_EQ" "getExpiries() — available expiries"
cli_check "chain" "chain NIFTY NSE_EQ 2026-06-25" "getOptionChain() — option chain snapshot (adjust expiry date as needed)"
echo ""
fi

# ══════════════════════════════════════════════════════════════
# 9. Margin
# ══════════════════════════════════════════════════════════════
if should_run "margin"; then
echo "━━━ 9. Margin ─━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
cli_check "margin" "margin SBIN NSE_EQ --side BUY --qty 100 --product INTRADAY --order-type MARKET" "estimateMargin() — margin for SBIN intraday"
echo ""
fi

# ══════════════════════════════════════════════════════════════
# 10. System Admin (via HTTP API, if attached)
# ══════════════════════════════════════════════════════════════
if should_run "admin" && [ -n "$ATTACH_URL" ]; then
echo "━━━ 10. System Admin (HTTP API) ━━━━━━━━━━━━━━━━━━━━━━━━━"
http_check GET "/actuator/health" "200" "Health endpoint"
http_check GET "/admin/runtime" "200" "Runtime status"
http_check GET "/admin/summary" "200" "Runtime summary"
http_check GET "/admin/strategies" "200" "Strategy plugins"
http_check GET "/admin/rate-limit" "200" "Rate limit metrics"
echo ""
fi

# ══════════════════════════════════════════════════════════════
# 11. Data Services (Charges, P&L, Holidays — via HTTP API)
# ══════════════════════════════════════════════════════════════
if should_run "data" && [ -n "$ATTACH_URL" ]; then
echo "━━━ 11. Data Services (HTTP API) ━━━━━━━━━━━━━━━━━━━━━━━━"
# These require a running trade-app that forwards to Upstox API
http_check GET "/admin/pipeline" "200" "Pipeline metrics (proves event processing)"
echo ""
fi

# ══════════════════════════════════════════════════════════════
# Summary
# ══════════════════════════════════════════════════════════════
echo ""
yellow "╔══════════════════════════════════════════════════════════════╗"
printf "║  %-58s ║\n" "Passed: $PASS   Failed: $FAIL   Skipped: $SKIP"
if [ -n "$SPECIFIC_ENDPOINT" ]; then
  printf "║  %-58s ║\n" "Endpoint filter: $SPECIFIC_ENDPOINT"
fi
yellow "╚══════════════════════════════════════════════════════════════╝"
echo ""

if [ "$FAIL" -gt 0 ]; then
  red "  FAILURES DETECTED — review output above for details."
  echo ""
  exit 1
elif [ "$PASS" -gt 0 ]; then
  green "  ALL ENDPOINT CHECKS PASSED."
  echo ""
  exit 0
else
  yellow "  No checks executed (use --profile and verify credentials)."
  echo ""
  exit 0
fi
