#!/usr/bin/env bash
set -euo pipefail

# Interactive Upstox OAuth token refresh.
# Opens browser for OAuth PKCE flow, captures access + refresh tokens,
# persists to runtime/upstox-token-state.json and syncs config/upstox-live.properties.
#
# Usage:
#   ./scripts/refresh-upstox-token.sh
#
# Prerequisites:
#   config/upstox-live.properties with clientId, clientSecret, redirectUri

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d /opt/homebrew/opt/openjdk@21 ]]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21
  fi
fi

LIVE_PROPS="${ROOT}/config/upstox-live.properties"
STATE_FILE="${ROOT}/runtime/upstox-token-state.json"

if [[ ! -f "$LIVE_PROPS" ]]; then
  echo "Missing: $LIVE_PROPS" >&2
  exit 1
fi

CLIENT_ID=$(grep "^upstox.live.clientId=" "$LIVE_PROPS" | cut -d= -f2-)
CLIENT_SECRET=$(grep "^upstox.live.clientSecret=" "$LIVE_PROPS" | cut -d= -f2-)

if [[ -z "$CLIENT_ID" || -z "$CLIENT_SECRET" ]]; then
  echo "Missing clientId or clientSecret in $LIVE_PROPS" >&2
  exit 1
fi

echo "Starting Upstox OAuth PKCE flow..."
echo "A browser window will open for authorization."
echo "After authorizing, you'll be redirected to localhost:18080."
echo ""

export UPSTOX_OAUTH_DRILL=true
./gradlew :app:brokerAuthDrillTest \
  --tests 'com.tradej.app.integration.UpstoxOAuthTokenIntegrationTest' \
  --no-daemon 2>&1 | tail -5

if [[ -f "$STATE_FILE" ]]; then
  echo ""
  echo "Token state persisted: $STATE_FILE"

  if command -v jq >/dev/null 2>&1; then
    ACCESS_TOKEN="$(jq -r '.accessToken // empty' "$STATE_FILE")"
    REFRESH_TOKEN="$(jq -r '.refreshToken // empty' "$STATE_FILE")"

    if [[ -n "$ACCESS_TOKEN" ]]; then
      # Sync access token to properties
      PROPS_TMP="$(mktemp)"
      awk -v token="$ACCESS_TOKEN" '
        /^upstox\.live\.accessToken=/ { print "upstox.live.accessToken=" token; found=1; next }
        { print }
        END { if (!found) print "upstox.live.accessToken=" token }
      ' "$LIVE_PROPS" > "$PROPS_TMP"
      mv "$PROPS_TMP" "$LIVE_PROPS"

      # Sync refresh token to properties
      if [[ -n "$REFRESH_TOKEN" ]]; then
        PROPS_TMP2="$(mktemp)"
        awk -v token="$REFRESH_TOKEN" '
          /^upstox\.live\.refreshToken=/ { print "upstox.live.refreshToken=" token; found=1; next }
          { print }
          END { if (!found) print "upstox.live.refreshToken=" token }
        ' "$LIVE_PROPS" > "$PROPS_TMP2"
        mv "$PROPS_TMP2" "$LIVE_PROPS"
        echo "Synced accessToken + refreshToken to $LIVE_PROPS"
      else
        echo "Synced accessToken to $LIVE_PROPS (no refresh token in response)"
      fi
    fi
  fi
else
  echo "Token state not written. The OAuth test may not exist yet."
  echo ""
  echo "Alternative: manually add tokens to $LIVE_PROPS:"
  echo "  upstox.live.accessToken=<your-access-token>"
  echo "  upstox.live.refreshToken=<your-refresh-token>"
  echo ""
  echo "Get tokens from Upstox Developer Console → OAuth Playground"
fi
