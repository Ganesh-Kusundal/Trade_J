#!/usr/bin/env bash
# Smoke-check console API paths (requires trade-j-app on BASE_URL, default http://127.0.0.1:8080).
set -euo pipefail

BASE_URL="${TRADEJ_ATTACH_URL:-http://127.0.0.1:8080}"
FAIL=0

check() {
  local method="$1" path="$2" expect="$3"
  local code
  code=$(curl -s -o /tmp/console-smoke-body.json -w "%{http_code}" -X "$method" "${BASE_URL}${path}" || echo "000")
  if [[ "$code" != "$expect" ]]; then
    echo "FAIL $method $path expected HTTP $expect got $code"
    cat /tmp/console-smoke-body.json 2>/dev/null | head -c 200 || true
    echo
    FAIL=1
  else
    echo "OK   $method $path HTTP $code"
  fi
}

echo "Console smoke against ${BASE_URL}"
check GET "/actuator/health" "200"
check GET "/console/index.html" "200"
check GET "/admin/runtime" "200"
check GET "/admin/strategies" "200"
check GET "/admin/summary" "200"
check GET "/api/v1/pipeline/templates" "200"
check GET "/api/v1/pipeline/node-types/categories" "200"

if command -v jq >/dev/null 2>&1; then
  curl -s "${BASE_URL}/admin/strategies" | jq -e '.plugins | type == "array"' >/dev/null \
    && echo "OK   /admin/strategies JSON has plugins array" \
    || { echo "FAIL /admin/strategies missing plugins array"; FAIL=1; }
fi

exit "$FAIL"
