#!/usr/bin/env bash
# ============================================================================
# Level 0: Platform Foundation Certification Script
# ============================================================================
# Validates: Configuration, Composition, Storage, Event System
# Exit codes: 0 = PASS, 1 = FAIL
# ============================================================================

set -euo pipefail

WORKSPACE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG_FILE="$WORKSPACE_ROOT/logs/certification-level-0.log"
REPORT_FILE="$WORKSPACE_ROOT/certification-reports/level-0-report.json"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# ANSI color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m' # No Color

mkdir -p "$WORKSPACE_ROOT/logs" "$WORKSPACE_ROOT/certification-reports"

# Initialize results
OVERALL="PASS"
CHECK_CONFIG_STATUS="PASS"
CHECK_CONFIG_DETAILS=""
CHECK_COMPOSITION_STATUS="PASS"
CHECK_COMPOSITION_DETAILS=""
CHECK_STORAGE_STATUS="PASS"
CHECK_STORAGE_DETAILS=""
CHECK_EVENT_STATUS="PASS"
CHECK_EVENT_DETAILS=""

log() {
    echo -e "$1" | tee -a "$LOG_FILE"
}

# ============================================================================
# Start Certification
# ============================================================================

log "${BOLD}═══════════════════════════════════════════════════════════${NC}"
log "${BOLD}  Level 0: Platform Foundation Certification${NC}"
log "${BOLD}  Timestamp: $TIMESTAMP${NC}"
log "${BOLD}═══════════════════════════════════════════════════════════${NC}"
log ""

cd "$WORKSPACE_ROOT"

# Clear previous log
> "$LOG_FILE"

# ============================================================================
# Check 1: Configuration Validation
# ============================================================================
log "${BOLD}── Check 1: Configuration Validation ──${NC}"

CONFIG_PASS=true

# Check properties files
PROPS_FILES=(
    "config/dhan-local.properties"
    "config/dhan-sandbox.properties"
    "config/upstox-live.properties"
    "config/upstox-sandbox.properties"
    "config/icici-local.properties"
)

for prop_file in "${PROPS_FILES[@]}"; do
    if [ -f "$WORKSPACE_ROOT/$prop_file" ]; then
        log "${GREEN}[OK]${NC} $prop_file exists"
    else
        log "${YELLOW}[WARN]${NC} $prop_file missing"
        CONFIG_PASS=false
    fi
done

# Check credential files
CRED_FILES=(
    "config/dhan-pin.txt"
    "config/dhan-totp-secret.txt"
    "config/icici-username.txt"
    "config/icici-password.txt"
    "config/icici-totp-secret.txt"
    "config/icici-api-session.txt"
)

for cred_file in "${CRED_FILES[@]}"; do
    if [ -f "$WORKSPACE_ROOT/$cred_file" ]; then
        log "${GREEN}[OK]${NC} $cred_file exists"
    else
        log "${YELLOW}[INFO]${NC} $cred_file missing (may be optional)"
    fi
done

if [ "$CONFIG_PASS" = true ]; then
    CHECK_CONFIG_STATUS="PASS"
    CHECK_CONFIG_DETAILS="All required configuration files present"
    log "${GREEN}[PASS]${NC} Configuration Validation"
else
    CHECK_CONFIG_STATUS="FAIL"
    CHECK_CONFIG_DETAILS="Missing required configuration files"
    OVERALL="FAIL"
    log "${RED}[FAIL]${NC} Configuration Validation"
fi
log ""

# ============================================================================
# Check 2: Composition Root
# ============================================================================
log "${BOLD}── Check 2: Composition Root ──${NC}"

# Verify composition module builds
if ./gradlew :composition:compileJava -x test >> "$LOG_FILE" 2>&1; then
    log "${GREEN}[PASS]${NC} Composition module compiles successfully"
    CHECK_COMPOSITION_STATUS="PASS"
    CHECK_COMPOSITION_DETAILS="Composition module compiled without errors"
else
    log "${RED}[FAIL]${NC} Composition module compilation failed"
    CHECK_COMPOSITION_STATUS="FAIL"
    CHECK_COMPOSITION_DETAILS="Composition module failed to compile"
    OVERALL="FAIL"
fi
log ""

# ============================================================================
# Check 3: Storage Initialization
# ============================================================================
log "${BOLD}── Check 3: Storage Initialization ──${NC}"

STORAGE_PASS=true

# Check DuckDB file
if [ -f "$WORKSPACE_ROOT/runtime-dev/trade.duckdb" ] || [ -f "$WORKSPACE_ROOT/app/runtime-dev/trade.duckdb" ]; then
    log "${GREEN}[OK]${NC} DuckDB database file exists"
else
    log "${YELLOW}[INFO]${NC} DuckDB database will be created on first use"
fi

# Check Chronicle Queue directories
CHRONICLE_DIRS=(
    "runtime-dev/chronicle"
    "app/runtime-dev/chronicle"
)

for chronicle_dir in "${CHRONICLE_DIRS[@]}"; do
    if [ -d "$WORKSPACE_ROOT/$chronicle_dir" ]; then
        log "${GREEN}[OK]${NC} Chronicle Queue directory: $chronicle_dir"
    else
        log "${YELLOW}[INFO]${NC} Chronicle Queue directory not found: $chronicle_dir (will be created)"
    fi
done

# Check Parquet storage paths
if [ -d "$WORKSPACE_ROOT/data" ]; then
    log "${GREEN}[OK]${NC} Data directory exists"
else
    log "${YELLOW}[WARN]${NC} Data directory missing"
    STORAGE_PASS=false
fi

if [ "$STORAGE_PASS" = true ]; then
    CHECK_STORAGE_STATUS="PASS"
    CHECK_STORAGE_DETAILS="Storage infrastructure initialized or will initialize on first use"
    log "${GREEN}[PASS]${NC} Storage Initialization"
else
    CHECK_STORAGE_STATUS="FAIL"
    CHECK_STORAGE_DETAILS="Missing critical storage directories"
    OVERALL="FAIL"
    log "${RED}[FAIL]${NC} Storage Initialization"
fi
log ""

# ============================================================================
# Check 4: Event System
# ============================================================================
log "${BOLD}── Check 4: Event System ──${NC}"

# Verify event classes compile
if ./gradlew :core:compileJava -x test >> "$LOG_FILE" 2>&1; then
    # Count event classes
    EVENT_COUNT=$(find "$WORKSPACE_ROOT/core/src/main/java/com/tradej/core/domain/event" -name "*.java" 2>/dev/null | wc -l | tr -d ' ')
    log "${GREEN}[OK]${NC} Event system compiled - $EVENT_COUNT event classes found"
    
    # Verify key event types exist
    KEY_EVENTS=(
        "DomainEvent.java"
        "MarketTickEvent.java"
        "CandleClosed.java"
        "OrderFilled.java"
    )
    
    EVENTS_PRESENT=true
    for event_file in "${KEY_EVENTS[@]}"; do
        if [ -f "$WORKSPACE_ROOT/core/src/main/java/com/tradej/core/domain/event/$event_file" ]; then
            log "${GREEN}[OK]${NC} $event_file present"
        else
            log "${RED}[FAIL]${NC} $event_file missing"
            EVENTS_PRESENT=false
        fi
    done
    
    if [ "$EVENTS_PRESENT" = true ]; then
        CHECK_EVENT_STATUS="PASS"
        CHECK_EVENT_DETAILS="Event system compiled successfully with $EVENT_COUNT event classes"
        log "${GREEN}[PASS]${NC} Event System"
    else
        CHECK_EVENT_STATUS="FAIL"
        CHECK_EVENT_DETAILS="Missing critical event classes"
        OVERALL="FAIL"
        log "${RED}[FAIL]${NC} Event System"
    fi
else
    CHECK_EVENT_STATUS="FAIL"
    CHECK_EVENT_DETAILS="Event system compilation failed"
    OVERALL="FAIL"
    log "${RED}[FAIL]${NC} Event System"
fi
log ""

# ============================================================================
# Generate JSON Report
# ============================================================================
log "${BOLD}── Generating Report ──${NC}"

cat > "$REPORT_FILE" << EOF
{
  "level": 0,
  "name": "Platform Foundation Certification",
  "timestamp": "$TIMESTAMP",
  "workspace": "$WORKSPACE_ROOT",
  "overall": "$OVERALL",
  "checks": [
    {
      "name": "Configuration",
      "status": "$CHECK_CONFIG_STATUS",
      "details": "$CHECK_CONFIG_DETAILS"
    },
    {
      "name": "Composition",
      "status": "$CHECK_COMPOSITION_STATUS",
      "details": "$CHECK_COMPOSITION_DETAILS"
    },
    {
      "name": "Storage",
      "status": "$CHECK_STORAGE_STATUS",
      "details": "$CHECK_STORAGE_DETAILS"
    },
    {
      "name": "Event System",
      "status": "$CHECK_EVENT_STATUS",
      "details": "$CHECK_EVENT_DETAILS"
    }
  ]
}
EOF

log "${GREEN}[DONE]${NC} Report saved to: $REPORT_FILE"
log ""

# ============================================================================
# Summary
# ============================================================================
log "${BOLD}═══════════════════════════════════════════════════════════${NC}"
if [ "$OVERALL" = "PASS" ]; then
    log "${GREEN}${BOLD}  OVERALL: PASS${NC}"
else
    log "${RED}${BOLD}  OVERALL: FAIL${NC}"
fi
log "${BOLD}═══════════════════════════════════════════════════════════${NC}"
log ""
log "Results:"
log "  $([ "$CHECK_CONFIG_STATUS" = "PASS" ] && echo -e "${GREEN}[PASS]${NC}" || echo -e "${RED}[FAIL]${NC}") Configuration"
log "  $([ "$CHECK_COMPOSITION_STATUS" = "PASS" ] && echo -e "${GREEN}[PASS]${NC}" || echo -e "${RED}[FAIL]${NC}") Composition"
log "  $([ "$CHECK_STORAGE_STATUS" = "PASS" ] && echo -e "${GREEN}[PASS]${NC}" || echo -e "${RED}[FAIL]${NC}") Storage"
log "  $([ "$CHECK_EVENT_STATUS" = "PASS" ] && echo -e "${GREEN}[PASS]${NC}" || echo -e "${RED}[FAIL]${NC}") Event System"
log ""
log "Full log: $LOG_FILE"
log "Report: $REPORT_FILE"
log ""

# Exit with appropriate code
if [ "$OVERALL" = "PASS" ]; then
    exit 0
else
    exit 1
fi
