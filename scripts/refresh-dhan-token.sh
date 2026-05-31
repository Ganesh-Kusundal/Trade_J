#!/usr/bin/env bash
set -euo pipefail

# Mint a fresh live Dhan access token once via TOTP, persist runtime state, and sync
# config/dhan-local.properties. Dhan enforces ~2 minutes between TOTP mints for the same account.
#
# Usage:
#   ./scripts/refresh-dhan-token.sh
#
# Wait at least 2 minutes before running again or before fullRegressionTest if another mint
# just occurred (including a failed regression run that attempted TOTP).

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d /opt/homebrew/opt/openjdk@21 ]]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21
  fi
fi

LIVE_PROPS="${ROOT}/config/dhan-local.properties"
STATE_FILE="${ROOT}/runtime/dhan-token-state.json"
PIN_FILE="${ROOT}/config/dhan-pin.txt"
TOTP_FILE="${ROOT}/config/dhan-totp-secret.txt"

for f in "$LIVE_PROPS" "$PIN_FILE" "$TOTP_FILE"; do
  if [[ ! -f "$f" ]]; then
    echo "Missing required file: $f" >&2
    exit 1
  fi
done

if ! grep -q 'dhan.authMode=TOTP_GENERATED' "$LIVE_PROPS" 2>/dev/null \
    && ! grep -q 'dhan.authMode=totp_generated' "$LIVE_PROPS" 2>/dev/null; then
  echo "config/dhan-local.properties must set dhan.authMode=TOTP_GENERATED" >&2
  exit 1
fi

echo "Minting live Dhan token (destructive drill — clears ${STATE_FILE})..."
export DHAN_FORCE_TOKEN_REFRESH=true
./gradlew :app:brokerAuthDrillTest \
  --tests 'com.tradej.app.integration.DhanRefreshProductionTokenIntegrationTest' \
  --no-daemon

if [[ ! -f "$STATE_FILE" ]]; then
  echo "Token state not written to $STATE_FILE" >&2
  exit 1
fi

TOKEN="$(python3 - <<'PY'
import json
import sys
from pathlib import Path

state = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
token = state.get("accessToken", "").strip()
if not token:
    raise SystemExit("accessToken missing in token state file")
print(token)
PY
"$STATE_FILE")"

python3 - <<'PY' "$LIVE_PROPS" "$TOKEN"
import re
import sys
from pathlib import Path

props_path = Path(sys.argv[1])
token = sys.argv[2]
lines = props_path.read_text(encoding="utf-8").splitlines()
updated = False
out = []
for line in lines:
    if line.startswith("dhan.accessToken="):
        out.append(f"dhan.accessToken={token}")
        updated = True
    else:
        out.append(line)
if not updated:
    out.append(f"dhan.accessToken={token}")
props_path.write_text("\n".join(out) + "\n", encoding="utf-8")
PY

echo "Synced dhan.accessToken in $LIVE_PROPS"
echo "Token state: $STATE_FILE"
echo "Wait at least 2 minutes before another TOTP mint or ./scripts/run-full-regression.sh"
