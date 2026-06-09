#!/usr/bin/env bash
set -euo pipefail

# Captures real broker payloads from Dhan for certification artifacts.
# Stores sanitized responses as regression baselines.
#
# Usage:
#   ./scripts/capture-broker-payload.sh [broker]
#   Default broker: dhan

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BROKER="${1:-dhan}"
OUTDIR="certification-artifacts/${BROKER}"
TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%SZ)

GREEN='\033[0;32m'
RED='\033[0;31m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

mkdir -p "$OUTDIR/market-feed" "$OUTDIR/depth" "$OUTDIR/options" "$OUTDIR/historical" "$OUTDIR/orders" "$OUTDIR/portfolio"

BROKER_UPPER=$(echo "$BROKER" | tr '[:lower:]' '[:upper:]')
echo -e "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${CYAN}  BROKER PAYLOAD CAPTURE: ${BROKER_UPPER}${NC}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "  Timestamp: $TIMESTAMP\n"

PASS=0
FAIL=0

capture() {
    local name="$1" args="$2" outfile="$3"
    echo -ne "  Capturing $name... "
    OUTPUT=$(./gradlew :cli:run --args="--json $args" --quiet 2>&1 || true)

    JSON_LINE=$(echo "$OUTPUT" | grep '^[{[]' | head -1)
    if [ -n "$JSON_LINE" ]; then
        echo "$JSON_LINE" > "$outfile"
        echo -e "${GREEN}✓ PASS${NC} → $outfile"
        PASS=$((PASS + 1))
    else
        echo "$OUTPUT" > "$outfile.raw"
        echo -e "${RED}✗ FAIL${NC} (raw output saved to $outfile.raw)"
        FAIL=$((FAIL + 1))
    fi
}

if [[ "$BROKER" == "dhan" ]]; then
    # Market Feed — Quote
    capture "Quote (RELIANCE)" \
        "broker dhan quote RELIANCE NSE_EQ" \
        "$OUTDIR/market-feed/quote-reliance.json"

    capture "LTP (NIFTY)" \
        "broker dhan ltp NIFTY IDX_I" \
        "$OUTDIR/market-feed/ltp-nifty.json"

    # Depth
    capture "Depth (RELIANCE)" \
        "broker dhan depth RELIANCE NSE_EQ" \
        "$OUTDIR/depth/depth-reliance.json"

    # Options
    capture "Option Chain (NIFTY)" \
        "broker dhan chain NIFTY IDX_I" \
        "$OUTDIR/options/chain-nifty.json"

    # Historical
    capture "Historical Candles (NIFTY 5m)" \
        "broker dhan historical NIFTY IDX_I --interval 5m --from 2026-06-01 --to 2026-06-07" \
        "$OUTDIR/historical/candles-nifty-5m.json"

    # Orders / Portfolio
    capture "Order Book" \
        "broker dhan orders" \
        "$OUTDIR/orders/order-book.json"

    capture "Positions" \
        "broker dhan positions" \
        "$OUTDIR/orders/positions.json"

    capture "Balance" \
        "broker dhan balance" \
        "$OUTDIR/portfolio/balance.json"
fi

# Sanitize secrets from all captured files
echo -e "\n  ${BOLD}Sanitizing secrets...${NC}"
find "$OUTDIR" -name "*.json" -exec sed -i '' \
    -e 's/eyJ[A-Za-z0-9_-]*\.[A-Za-z0-9_-]*\.[A-Za-z0-9_-]*/<JWT_TOKEN_REDACTED>/g' \
    -e 's/"clientId":"[0-9]*"/"clientId":"<REDACTED>"/g' \
    -e 's/"client_id":"[0-9]*"/"client_id":"<REDACTED>"/g' \
    {} + 2>/dev/null || true

echo ""
echo -e "${BOLD}  Results:${NC}"
echo -e "  ${GREEN}Captured: $PASS${NC}"
echo -e "  ${RED}Failed:   $FAIL${NC}"
echo -e "  Output:   ${CYAN}$OUTDIR/${NC}"
echo ""

# Generate manifest
cat > "$OUTDIR/manifest.json" <<EOF
{
  "broker": "$BROKER",
  "timestamp": "$TIMESTAMP",
  "captured": $PASS,
  "failed": $FAIL,
  "artifacts": [
$(find "$OUTDIR" -name "*.json" ! -name "manifest.json" -exec basename {} \; | sed 's/.*/    "&"/' | paste -sd, -)
  ]
}
EOF
echo -e "  Manifest: ${CYAN}$OUTDIR/manifest.json${NC}\n"
