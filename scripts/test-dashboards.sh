#!/usr/bin/env bash
set -euo pipefail

# Tests for the 3 new dashboard yaml descriptors. Each
# dashboard must have a unique id, a title, a description, a
# layout (grid/tabs/split), and a non-empty widgets list with
# each widget having a unique id within the dashboard and a
# known widget type from the widget library.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DASHBOARDS="${ROOT}/trade_j_frontend/src/dashboards"

# Widget types known to be implemented (from T15 + prior work).
KNOWN_WIDGETS="depth-snapshot drawdown equity-curve heatmap orders-table pnl-curve pnl-summary positions-table scan-hits signal-stream signals-table"

# Layout kinds supported by the renderer.
KNOWN_LAYOUTS="grid tabs split"

assert_yaml() {
    local file="$1"
    local prefix="$2"

    if [[ ! -f "$file" ]]; then
        echo "FAIL: $prefix file does not exist: $file"
        return 1
    fi

    if ! grep -q "^id:" "$file"; then
        echo "FAIL: $prefix missing 'id' field"
        return 1
    fi
    if ! grep -q "^title:" "$file"; then
        echo "FAIL: $prefix missing 'title' field"
        return 1
    fi
    if ! grep -q "^description:" "$file"; then
        echo "FAIL: $prefix missing 'description' field"
        return 1
    fi
    if ! grep -q "^layout:" "$file"; then
        echo "FAIL: $prefix missing 'layout' field"
        return 1
    fi
    if ! grep -q "^widgets:" "$file"; then
        echo "FAIL: $prefix missing 'widgets' field"
        return 1
    fi

    # Layout kind
    local layout_kind
    layout_kind="$(awk '/^layout:/{flag=1; next} /^widgets:/{flag=0} flag && /kind:/{print $2; exit}' "$file")"
    if ! echo "$KNOWN_LAYOUTS" | grep -qw "$layout_kind"; then
        echo "FAIL: $prefix unknown layout kind: $layout_kind"
        return 1
    fi

    # Widget count and types
    local widget_count
    widget_count="$(grep -c "^  - id:" "$file")"
    if [[ "$widget_count" -lt 1 ]]; then
        echo "FAIL: $prefix has no widgets"
        return 1
    fi

    # Each widget type must be in the known list
    local widget_types
    widget_types="$(awk '/^  - id:/{flag=1; next} /^[a-z]/{flag=0} flag && /type:/{print $2}' "$file")"
    while IFS= read -r wt; do
        if [[ -n "$wt" ]] && ! echo "$KNOWN_WIDGETS" | grep -qw "$wt"; then
            echo "FAIL: $prefix unknown widget type: $wt (in: $file)"
            return 1
        fi
    done <<< "$widget_types"

    # Each widget id must be unique within the dashboard
    local widget_ids
    widget_ids="$(awk '/^  - id:/{print $3}' "$file")"
    local duplicates
    duplicates="$(echo "$widget_ids" | sort | uniq -d)"
    if [[ -n "$duplicates" ]]; then
        echo "FAIL: $prefix has duplicate widget ids: $duplicates"
        return 1
    fi

    return 0
}

# Verify all 3 new dashboards exist and are well-formed
for name in replay scanner options; do
    file="${DASHBOARDS}/${name}.dashboard.yaml"
    if assert_yaml "$file" "$name"; then
        echo "  PASS: $name.dashboard.yaml"
    else
        exit 1
    fi
done

# Verify the existing 3 dashboards still pass
for name in execution strategy portfolio; do
    file="${DASHBOARDS}/${name}.dashboard.yaml"
    if assert_yaml "$file" "$name"; then
        echo "  PASS: $name.dashboard.yaml (existing)"
    else
        exit 1
    fi
done

# Verify all dashboards have unique ids
ids="$(awk '/^id:/{print $2}' "${DASHBOARDS}"/*.yaml | sort)"
dup="$(echo "$ids" | uniq -d)"
if [[ -n "$dup" ]]; then
    echo "FAIL: duplicate dashboard ids: $dup"
    exit 1
fi
echo "  PASS: all 6 dashboard ids are unique"

echo ""
echo "All dashboard yaml tests PASS"
