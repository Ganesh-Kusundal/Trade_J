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

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required (brew install jq)" >&2
  exit 1
fi

TOKEN="$(jq -r '.accessToken // empty' "$STATE_FILE")"
if [[ -z "$TOKEN" ]]; then
  echo "accessToken missing in token state file" >&2
  exit 1
fi

PROPS_TMP="$(mktemp)"
trap 'rm -f "$PROPS_TMP"' EXIT
awk -v token="$TOKEN" '
  /^dhan\.accessToken=/ { print "dhan.accessToken=" token; found=1; next }
  { print }
  END { if (!found) print "dhan.accessToken=" token }
' "$LIVE_PROPS" >"$PROPS_TMP"
mv "$PROPS_TMP" "$LIVE_PROPS"
trap - EXIT

echo "Synced dhan.accessToken in $LIVE_PROPS"
echo "Token state: $STATE_FILE"
echo "Wait at least 2 minutes before another TOTP mint or ./scripts/run-full-regression.sh"
