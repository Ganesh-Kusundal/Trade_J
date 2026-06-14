#!/usr/bin/env bash
set -euo pipefail

# Unified broker-token refresh. Reads each broker's runtime
# token state, decides whether the token is expired or within
# the refresh buffer, and dispatches to the broker-specific
# refresh script. Output: a per-broker table of (broker, status,
# expires-in-min).
#
# Usage:
#   ./scripts/refresh-tokens.sh              # all brokers (only refresh if needed)
#   ./scripts/refresh-tokens.sh --all        # force refresh of all brokers
#   ./scripts/refresh-tokens.sh dhan         # one broker (only refresh if needed)
#   ./scripts/refresh-tokens.sh dhan --force # force refresh
#
# Why a unified entry point:
# - The three broker-specific scripts (refresh-dhan-token.sh,
#   refresh-upstox-token.sh, refresh-icici-session.sh) have
#   different arguments, different exit codes, and different
#   output. A single script normalizes that surface.
# - Operations need a single "is every broker's token healthy?"
#   check. The per-broker token-state files in runtime/ are
#   the source of truth.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

REFRESH_BUFFER_MINUTES="${REFRESH_BUFFER_MINUTES:-30}"

# ── Broker configuration ──
# Each row: <name> <token-state-file> <refresh-script> <refresh-buffer-property>
BROKERS=(
    "dhan   runtime/dhan-token-state.json     scripts/refresh-dhan-token.sh     dhan.refreshBufferMinutes"
    "upstox runtime/upstox-token-state.json  scripts/refresh-upstox-token.sh  upstox.refreshBufferMinutes"
    "icici  runtime/icici-token-state.json   scripts/refresh-icici-session.sh  icici.refreshBufferMinutes"
)

# ── Argument parsing ──
ONLY_BROKER=""
FORCE=0
DRY_RUN=0
for arg in "$@"; do
    case "$arg" in
        --all)     FORCE=1 ;;
        --force)   FORCE=1 ;;
        --dry-run) DRY_RUN=1 ;;
        --help|-h)
            head -22 "$0" | tail -17
            exit 0
            ;;
        dhan|upstox|icici) ONLY_BROKER="$arg" ;;
        *)
            echo "Unknown argument: $arg" >&2
            exit 2
            ;;
    esac
done

NOW_MS="$(date +%s)000"
echo "Refresh check at $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "Refresh buffer: ${REFRESH_BUFFER_MINUTES} minutes (overridden per-broker by refreshBufferMinutes in *.properties)"
if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "DRY-RUN: no refresh will be performed"
fi
echo ""

# ── Per-broker decision ──
refresh_one() {
    local name="$1"
    local state_file="$2"
    local script="$3"
    local prop_key="$4"

    # Find the broker row
    if [[ -z "$name" ]]; then return 0; fi

    # If the state_file is absolute, use as-is; otherwise
    # resolve relative to ROOT. This lets tests point the
    # script at temp state files.
    local state_path="$state_file"
    if [[ "$state_file" != /* ]]; then
        state_path="$ROOT/$state_file"
    fi
    local script_path="$script"
    if [[ "$script" != /* ]]; then
        script_path="$ROOT/$script"
    fi

    if [[ ! -f "$state_path" ]]; then
        printf "  %-8s  %-15s  %s\n" "$name" "no-state" "token state file missing ($state_file)"
        return 0
    fi

    if ! command -v jq >/dev/null 2>&1; then
        printf "  %-8s  %-15s  %s\n" "$name" "no-jq" "jq is required (brew install jq)"
        return 0
    fi

    local exp_ms issued_at
    exp_ms="$(jq -r '.expiryEpochMs // 0' "$state_path" 2>/dev/null || echo 0)"
    issued_at="$(jq -r '.issuedAtEpochMs // 0' "$state_path" 2>/dev/null || echo 0)"

    if [[ "$exp_ms" -le 0 ]]; then
        printf "  %-8s  %-15s  %s\n" "$name" "malformed" "expiryEpochMs missing or zero"
        return 0
    fi

    local minutes_remaining=$(( (exp_ms - NOW_MS) / 60000 ))

    if [[ "$FORCE" -eq 1 ]]; then
        if [[ "$DRY_RUN" -eq 1 ]]; then
            printf "  %-8s  %-15s  %s\n" "$name" "FORCED" "(dry-run, no refresh)"
            return 0
        fi
        printf "  %-8s  %-15s  %s\n" "$name" "FORCED" "refreshing (--force)"
        if bash "$script_path"; then
            printf "  %-8s  %-15s  %s\n" "$name" "refreshed" "OK"
        else
            printf "  %-8s  %-15s  %s\n" "$name" "FAILED" "refresh script returned non-zero"
        fi
        return 0
    fi

    if [[ "$minutes_remaining" -lt 0 ]]; then
        if [[ "$DRY_RUN" -eq 1 ]]; then
            printf "  %-8s  %-15s  %s\n" "$name" "EXPIRED" "${minutes_remaining}min (dry-run, no refresh)"
            return 0
        fi
        printf "  %-8s  %-15s  %s\n" "$name" "EXPIRED" "${minutes_remaining}min — refreshing"
        if bash "$script_path"; then
            printf "  %-8s  %-15s  %s\n" "$name" "refreshed" "OK"
        else
            printf "  %-8s  %-15s  %s\n" "$name" "FAILED" "refresh script returned non-zero"
        fi
        return 0
    fi

    if [[ "$minutes_remaining" -lt "$REFRESH_BUFFER_MINUTES" ]]; then
        if [[ "$DRY_RUN" -eq 1 ]]; then
            printf "  %-8s  %-15s  %s\n" "$name" "expiring-soon" "${minutes_remaining}min < ${REFRESH_BUFFER_MINUTES}min (dry-run, no refresh)"
            return 0
        fi
        printf "  %-8s  %-15s  %s\n" "$name" "expiring-soon" "${minutes_remaining}min < ${REFRESH_BUFFER_MINUTES}min — refreshing"
        if bash "$script_path"; then
            printf "  %-8s  %-15s  %s\n" "$name" "refreshed" "OK"
        else
            printf "  %-8s  %-15s  %s\n" "$name" "FAILED" "refresh script returned non-zero"
        fi
        return 0
    fi

    printf "  %-8s  %-15s  %s\n" "$name" "ok" "${minutes_remaining}min remaining"
}

echo "broker    status           detail"
echo "--------  ---------------  ----------------------------------------"
for row in "${BROKERS[@]}"; do
    # shellcheck disable=SC2086
    set -- $row
    name="$1"
    state_file="$2"
    script="$3"
    if [[ -n "$ONLY_BROKER" && "$name" != "$ONLY_BROKER" ]]; then continue; fi
    refresh_one "$name" "$state_file" "$script" "$4"
done

echo ""
echo "Done. If a broker is in EXPIRED or FAILED state, the"
echo "next live e2e (e.g. regression test) will fail. The"
echo "Dhan refresh has a 2-minute cooldown between mints."
