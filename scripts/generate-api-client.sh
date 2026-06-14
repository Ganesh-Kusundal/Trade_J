#!/usr/bin/env bash
# Generate TypeScript API client from OpenAPI spec.
# Requires: npx @openapitools/openapi-generator-cli
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SPEC="$ROOT_DIR/docs/openapi.yaml"
OUTPUT="$ROOT_DIR/trade_j_frontend/src/generated"

if [ ! -f "$SPEC" ]; then
  echo "ERROR: OpenAPI spec not found at $SPEC" >&2
  exit 1
fi

echo "Generating TypeScript client from $SPEC..."

npx @openapitools/openapi-generator-cli generate \
  -i "$SPEC" \
  -g typescript-fetch \
  -o "$OUTPUT" \
  --additional-properties=typescriptThreePlus=true \
  --additional-properties=supportsES6=true \
  --additional-properties=npmName=trade-j-api \
  --additional-properties=npmVersion=1.0.0 \
  --type-mappings="integer=number,long=number"

echo "Generated files in $OUTPUT"
echo "Run 'cd trade_j_frontend && npx tsc --noEmit' to verify."
