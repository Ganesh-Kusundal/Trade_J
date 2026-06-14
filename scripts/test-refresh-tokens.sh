#!/usr/bin/env bash
set -euo pipefail

# Tests for scripts/refresh-tokens.sh. The script's decision
# logic is what we care about: given a token-state file with
# a known expiryEpochMs, does the script report the right
# status? We don't actually mint tokens (no broker creds in
# the test environment) — we verify the dry-run output.
#
# Usage:
#   bash scripts/test-refresh-tokens.sh
#
# The test creates a temp state file, points the script at it
# (via a wrapper that overrides the path), and checks the
# status field.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="${ROOT}/scripts/refresh-tokens.sh"

if [[ ! -x "$SCRIPT" ]]; then
    echo "FAIL: refresh-tokens.sh is not executable"
    exit 1
fi

# Build a tiny wrapper that overrides the BROKERS list in the
# script via the same dispatch. We use a custom copy of the
# script that points at /tmp state files.
TMP_DIR="$(mktemp -d)"
trap "rm -rf $TMP_DIR" EXIT

# 1. Token expired 60 minutes ago → status EXPIRED
cat > "$TMP_DIR/expired-state.json" <<EOF
{"accessToken":"fake","expiryEpochMs": $(($(date +%s)000 - 3600000)), "issuedAtEpochMs": $(($(date +%s)000 - 7200000)), "source":"TOTP_GENERATED"}
EOF
# 2. Token expires in 24 hours → status ok
cat > "$TMP_DIR/healthy-state.json" <<EOF
{"accessToken":"fake","expiryEpochMs": $(($(date +%s)000 + 86400000)), "issuedAtEpochMs": $(($(date +%s)000 - 3600000)), "source":"TOTP_GENERATED"}
EOF
# 3. Token expires in 5 minutes → status expiring-soon
cat > "$TMP_DIR/expiring-state.json" <<EOF
{"accessToken":"fake","expiryEpochMs": $(($(date +%s)000 + 300000)), "issuedAtEpochMs": $(($(date +%s)000 - 3600000)), "source":"TOTP_GENERATED"}
EOF

# Wrap the script in a version that points at /tmp state files
WRAP="$TMP_DIR/refresh-wrapper.sh"
sed -e "s|runtime/dhan-token-state.json|$TMP_DIR/expired-state.json|g" \
    -e "s|runtime/upstox-token-state.json|$TMP_DIR/healthy-state.json|g" \
    -e "s|runtime/icici-token-state.json|$TMP_DIR/expiring-state.json|g" \
    "$SCRIPT" > "$WRAP"
chmod +x "$WRAP"

echo "Test 1: --dry-run reports all 3 brokers with the right status"
out=$(bash "$WRAP" --dry-run 2>&1)
echo "$out" | grep -q "EXPIRED" || { echo "FAIL: expected EXPIRED for dhan"; echo "$out"; exit 1; }
echo "$out" | grep -q "ok.*min remaining" || { echo "FAIL: expected ok for upstox"; echo "$out"; exit 1; }
echo "$out" | grep -q "expiring-soon" || { echo "FAIL: expected expiring-soon for icici"; echo "$out"; exit 1; }
echo "  PASS"

echo "Test 2: --dry-run with one broker"
out=$(bash "$WRAP" dhan --dry-run 2>&1)
echo "$out" | grep -q "EXPIRED" || { echo "FAIL: expected EXPIRED for dhan only"; echo "$out"; exit 1; }
echo "$out" | grep -q "upstox" && { echo "FAIL: upstox should be filtered out"; exit 1; } || true
echo "$out" | grep -q "icici" && { echo "FAIL: icici should be filtered out"; exit 1; } || true
echo "  PASS"

echo "Test 3: --help prints usage"
out=$(bash "$SCRIPT" --help 2>&1)
echo "$out" | grep -q "Usage:" || { echo "FAIL: --help should print usage"; exit 1; }
echo "  PASS"

echo "Test 4: unknown broker arg is rejected"
out=$(bash "$SCRIPT" unknown 2>&1 || true)
echo "$out" | grep -q "Unknown argument" || { echo "FAIL: unknown arg should be rejected"; echo "$out"; exit 1; }
echo "  PASS"

echo ""
echo "All refresh-tokens.sh tests PASS"
