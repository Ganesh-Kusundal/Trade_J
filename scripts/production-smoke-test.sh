#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "=== Trade-J Production Smoke Test ==="
echo "Target: $BASE_URL"

curl -sf "$BASE_URL/actuator/health" | head -c 500
echo ""
curl -sf "$BASE_URL/admin/summary" | head -c 500
echo ""
curl -sf "$BASE_URL/api/v1/read-model" | head -c 500
echo ""

echo "Replay guard check (expect 409 in LIVE mode):"
curl -s -o /dev/null -w "%{http_code}\n" -X POST \
  "$BASE_URL/admin/historical/replay/ticks?symbol=SBIN&from=0&to=1" || true

echo "Smoke test completed."
