#!/usr/bin/env bash
set -euo pipefail

# Automate ICICI Breeze login via Java Selenium (Chrome), exchange API_Session for a
# signed session, and persist runtime/icici-token-state.json.
#
# Requires (gitignored):
#   config/icici-local.properties   — appKey, secretKey, authMode=BROWSER_AUTOMATED
#   config/icici-username.txt       — ICICI Direct login id
#   config/icici-password.txt       — ICICI Direct password
#   config/icici-totp-secret.txt    — TOTP seed for 2FA
#
# Your ICICI developer app redirect URL is typically:
#   https://api.icicidirect.com/
# After login, the browser lands on https://api.icicidirect.com/?apisession=<token>
# (Some apps use http://127.0.0.1:9080/api — optional local listener; not required.)
#
# Chrome must be installed. Selenium Manager downloads chromedriver automatically.
#
# Usage:
#   ./scripts/refresh-icici-session.sh

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d /opt/homebrew/opt/openjdk@21 ]]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21
  fi
fi

LIVE_PROPS="${ROOT}/config/icici-local.properties"
STATE_FILE="${ROOT}/runtime/icici-token-state.json"

for f in "$LIVE_PROPS" "${ROOT}/config/icici-username.txt" "${ROOT}/config/icici-password.txt" "${ROOT}/config/icici-totp-secret.txt"; do
  if [[ ! -s "$f" ]]; then
    echo "Missing required file: $f" >&2
    exit 1
  fi
done

if ! grep -qE '^icici\.authMode=(BROWSER_AUTOMATED|browser_automated)' "$LIVE_PROPS" 2>/dev/null; then
  echo "Set icici.authMode=BROWSER_AUTOMATED in config/icici-local.properties for automated login." >&2
  exit 1
fi

echo "Automating ICICI browser login and refreshing ${STATE_FILE}..."
export ICICI_SESSION_DRILL=true
export ICICI_TEST_ENABLED=true
./gradlew :app:brokerAuthDrillTest \
  --tests 'com.tradej.app.integration.IciciRefreshSessionIntegrationTest' \
  --no-daemon

if [[ ! -f "$STATE_FILE" ]]; then
  echo "Session state not written to ${STATE_FILE}" >&2
  exit 1
fi

echo "ICICI session state: ${STATE_FILE}"
echo "Cached API_Session: ${ROOT}/config/icici-api-session.txt"
