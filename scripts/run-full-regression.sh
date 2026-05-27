#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d /opt/homebrew/opt/openjdk@21 ]]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21
  fi
fi

LIVE_PROPS="${ROOT}/config/dhan-local.properties"
SANDBOX_PROPS="${ROOT}/config/dhan-sandbox.properties"

if [[ ! -f "$LIVE_PROPS" ]]; then
  echo "Missing live credentials: $LIVE_PROPS (copy from config/dhan-local.properties.example)" >&2
  exit 1
fi
if [[ ! -f "$SANDBOX_PROPS" ]]; then
  echo "Missing sandbox credentials: $SANDBOX_PROPS (copy from config/dhan-sandbox.properties.example)" >&2
  exit 1
fi

# Integration tests read config/dhan-local.properties and config/dhan-sandbox.properties directly.
export DHAN_ORDER_TEST_ENABLED=true
export DHAN_SLICE_ORDER_TEST_ENABLED=true
export DHAN_SUPER_ORDER_TEST_ENABLED=true
export DHAN_FOREVER_ORDER_TEST_ENABLED=true
export DHAN_SQUAREOFF_TEST_ENABLED=true
export DHAN_ALERT_TEST_ENABLED=true
export DHAN_PNL_EXIT_TEST_ENABLED=true
export DHAN_MARGIN_TEST_ENABLED=true
export DHAN_ROLLING_OPTION_TEST_ENABLED=true
export DHAN_CROSS_LAYER_TEST_ENABLED=true

echo "Running fullRegressionTest (live data + sandbox orders + cross-layer)..."
./gradlew fullRegressionTest --no-daemon "$@"
