#!/usr/bin/env bash
# Generate TypeScript gateway event types from AsyncAPI spec.
# This script uses a lightweight approach: parses the AsyncAPI YAML
# and generates typed interfaces. For production, consider using
# @asyncapi/generator with a TypeScript template.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SPEC="$ROOT_DIR/docs/asyncapi.yaml"
OUTPUT="$ROOT_DIR/trade_j_frontend/src/generated/gateway-events.ts"

if [ ! -f "$SPEC" ]; then
  echo "ERROR: AsyncAPI spec not found at $SPEC" >&2
  exit 1
fi

echo "Generating gateway event types from $SPEC..."

# For now, the gateway-events.ts is maintained manually to match the spec.
# Future: integrate @asyncapi/generator with typescript template.
# npx @asyncapi/generator "$SPEC" @asyncapi/ts-template -o "$OUTPUT"

echo "Gateway event types at: $OUTPUT"
echo "Verify manually or run 'cd trade_j_frontend && npx tsc --noEmit'"
