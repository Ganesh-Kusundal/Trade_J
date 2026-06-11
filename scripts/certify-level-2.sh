#!/usr/bin/env bash
# ============================================================================
# Level 2: Data Platform Certification Script
# ============================================================================
# Validates: Data Download, Parquet Persistence, DuckDB Persistence,
#            Data Integrity, Analytics
# Exit codes: 0 = PASS, 1 = FAIL
# ============================================================================

set -euo pipefail

WORKSPACE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG_FILE="$WORKSPACE_ROOT/logs/certification-level-2.log"
REPORT_FILE="$WORKSPACE_ROOT/certification-reports/level-2-report.json"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

mkdir -p "$WORKSPACE_ROOT/logs" "$WORKSPACE_ROOT/certification-reports"

> "$LOG_FILE"

log() {
    echo -e "$1" | tee -a "$LOG_FILE"
}

run_gradle_test() {
    local test_args="$1"
    local test_name="$2"
    local output
    output=$(./gradlew $test_args 2>&1 || true)
    
    if echo "$output" | grep -q "BUILD SUCCESSFUL"; then
        log "  ${GREEN}✓ $test_name: PASS${NC}"
        return 0
    else
        log "  ${RED}✗ $test_name: FAIL${NC}"
        echo "$output" | tail -20 >> "$LOG_FILE"
        return 1
    fi
}

log "${BOLD}═══════════════════════════════════════════════════════════${NC}"
log "${BOLD}  Level 2: Data Platform Certification${NC}"
log "${BOLD}  Timestamp: $TIMESTAMP${NC}"
log "${BOLD}═══════════════════════════════════════════════════════════${NC}"
log ""

cd "$WORKSPACE_ROOT"

OVERALL="PASS"

# Check status variables
CHECK_DATA_DOWNLOAD_STATUS="PENDING"
CHECK_DATA_DOWNLOAD_DETAILS=""
CHECK_PARQUET_STATUS="PENDING"
CHECK_PARQUET_DETAILS=""
CHECK_DUCKDB_STATUS="PENDING"
CHECK_DUCKDB_DETAILS=""
CHECK_DATA_INTEGRITY_STATUS="PENDING"
CHECK_DATA_INTEGRITY_DETAILS=""
CHECK_ANALYTICS_STATUS="PENDING"
CHECK_ANALYTICS_DETAILS=""

# ============================================================================
# Phase 1: Data Download & Quality Certification
# ============================================================================
log "${BOLD}${CYAN}── Phase 1: Data Download & Quality Certification ──${NC}"
log ""

# Verify historical data warehouse exists and has data
BARS_DIR="$WORKSPACE_ROOT/data/historical-equity/bars/interval=1m"
if [ -d "$BARS_DIR" ]; then
    SYMBOL_COUNT=$(ls -1d "$BARS_DIR"/symbol=* 2>/dev/null | wc -l | tr -d ' ')
    log "  Found $SYMBOL_COUNT symbols in parquet warehouse"
    
    if [ "$SYMBOL_COUNT" -gt 10 ]; then
        CHECK_DATA_DOWNLOAD_STATUS="PASS"
        CHECK_DATA_DOWNLOAD_DETAILS="Parquet warehouse has $SYMBOL_COUNT symbols"
        log "  ${GREEN}✓ Data Warehouse: PASS ($SYMBOL_COUNT symbols)${NC}"
    else
        CHECK_DATA_DOWNLOAD_STATUS="WARN"
        CHECK_DATA_DOWNLOAD_DETAILS="Only $SYMBOL_COUNT symbols found"
        log "  ${YELLOW}⚠ Data Warehouse: WARN (only $SYMBOL_COUNT symbols)${NC}"
    fi
else
    CHECK_DATA_DOWNLOAD_STATUS="FAIL"
    CHECK_DATA_DOWNLOAD_DETAILS="No parquet warehouse found"
    log "  ${RED}✗ Data Warehouse: FAIL (no warehouse)${NC}"
    OVERALL="FAIL"
fi
log ""

# Test candle data quality via Gradle
if run_gradle_test ":data-historical-ingest:test --tests *DataPlatformCertificationTest.candleDataQualityValidation" \
    "Candle data quality validation"; then
    CHECK_DATA_DOWNLOAD_STATUS="PASS"
    CHECK_DATA_DOWNLOAD_DETAILS="Candle OHLCV validation passed"
else
    CHECK_DATA_DOWNLOAD_STATUS="PARTIAL"
    CHECK_DATA_DOWNLOAD_DETAILS="Candle quality issues detected"
fi
log ""

# ============================================================================
# Phase 2: Parquet Persistence Certification
# ============================================================================
log "${BOLD}${CYAN}── Phase 2: Parquet Persistence Certification ──${NC}"
log ""

if run_gradle_test ":data-historical-ingest:test --tests *DataPlatformCertificationTest.writeAndReadParquetPreservesData" \
    "Parquet write-read integrity"; then
    CHECK_PARQUET_STATUS="PASS"
    CHECK_PARQUET_DETAILS="Parquet write and read preserves data integrity"
else
    CHECK_PARQUET_STATUS="FAIL"
    CHECK_PARQUET_DETAILS="Parquet persistence test failed"
    OVERALL="FAIL"
fi
log ""

# ============================================================================
# Phase 3: DuckDB Persistence Certification
# ============================================================================
log "${BOLD}${CYAN}── Phase 3: DuckDB Persistence Certification ──${NC}"
log ""

if run_gradle_test ":data-historical-ingest:test --tests *DataPlatformCertificationTest.duckdbQueryReturnsCorrectResults" \
    "DuckDB query correctness"; then
    CHECK_DUCKDB_STATUS="PASS"
    CHECK_DUCKDB_DETAILS="DuckDB queries return correct results"
else
    CHECK_DUCKDB_STATUS="FAIL"
    CHECK_DUCKDB_DETAILS="DuckDB query test failed"
    OVERALL="FAIL"
fi
log ""

# ============================================================================
# Phase 4: Data Integrity Certification (Cross-Layer)
# ============================================================================
log "${BOLD}${CYAN}── Phase 4: Data Integrity Certification (Cross-Layer) ──${NC}"
log ""

if run_gradle_test ":data-historical-ingest:test --tests *DataPlatformCertificationTest.dataIntegrityAcrossStorageLayers" \
    "Data integrity across storage layers"; then
    CHECK_DATA_INTEGRITY_STATUS="PASS"
    CHECK_DATA_INTEGRITY_DETAILS="Write=Parquet=DuckDB Read verified"
else
    CHECK_DATA_INTEGRITY_STATUS="FAIL"
    CHECK_DATA_INTEGRITY_DETAILS="Data integrity test failed"
    OVERALL="FAIL"
fi
log ""

# ============================================================================
# Phase 5: Analytics Certification
# ============================================================================
log "${BOLD}${CYAN}── Phase 5: Analytics Certification ──${NC}"
log ""

if run_gradle_test ":data-historical-ingest:test --tests *DataPlatformCertificationTest.analyticsQueriesWorkCorrectly" \
    "Analytics queries"; then
    CHECK_ANALYTICS_STATUS="PASS"
    CHECK_ANALYTICS_DETAILS="Analytics queries work correctly"
else
    CHECK_ANALYTICS_STATUS="FAIL"
    CHECK_ANALYTICS_DETAILS="Analytics query test failed"
    OVERALL="FAIL"
fi
log ""

# Additional analytics test
if run_gradle_test ":data-historical-ingest:test --tests *DataPlatformCertificationTest.availableSymbolsQueryWorks" \
    "Available symbols query"; then
    log "  ${GREEN}✓ Symbol discovery: PASS${NC}"
else
    log "  ${YELLOW}⚠ Symbol discovery: Issues detected${NC}"
fi
log ""

# ============================================================================
# Generate JSON Report
# ============================================================================
log "${BOLD}── Generating Report ──${NC}"

cat > "$REPORT_FILE" << EOF
{
  "level": 2,
  "name": "Data Platform Certification",
  "timestamp": "$TIMESTAMP",
  "workspace": "$WORKSPACE_ROOT",
  "overall": "$OVERALL",
  "checks": [
    {
      "name": "Data Download",
      "status": "$CHECK_DATA_DOWNLOAD_STATUS",
      "details": "$CHECK_DATA_DOWNLOAD_DETAILS"
    },
    {
      "name": "Parquet Persistence",
      "status": "$CHECK_PARQUET_STATUS",
      "details": "$CHECK_PARQUET_DETAILS"
    },
    {
      "name": "DuckDB Persistence",
      "status": "$CHECK_DUCKDB_STATUS",
      "details": "$CHECK_DUCKDB_DETAILS"
    },
    {
      "name": "Data Integrity",
      "status": "$CHECK_DATA_INTEGRITY_STATUS",
      "details": "$CHECK_DATA_INTEGRITY_DETAILS"
    },
    {
      "name": "Analytics",
      "status": "$CHECK_ANALYTICS_STATUS",
      "details": "$CHECK_ANALYTICS_DETAILS"
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
log "${BOLD}  Level 2: Data Platform Certification Summary${NC}"
log "${BOLD}═══════════════════════════════════════════════════════════${NC}"
log ""

print_status() {
    local name="$1" status="$2"
    case "$status" in
        PASS)    echo -e "  ${GREEN}■ $name: PASS${NC}" ;;
        PARTIAL) echo -e "  ${YELLOW}■ $name: PARTIAL${NC}" ;;
        WARN)    echo -e "  ${YELLOW}■ $name: WARN${NC}" ;;
        FAIL)    echo -e "  ${RED}■ $name: FAIL${NC}" ;;
        *)       echo -e "  $name: $status" ;;
    esac
}

print_status "Data Download" "$CHECK_DATA_DOWNLOAD_STATUS"
print_status "Parquet Persistence" "$CHECK_PARQUET_STATUS"
print_status "DuckDB Persistence" "$CHECK_DUCKDB_STATUS"
print_status "Data Integrity" "$CHECK_DATA_INTEGRITY_STATUS"
print_status "Analytics" "$CHECK_ANALYTICS_STATUS"

log ""
log "Full log: $LOG_FILE"
log "Report: $REPORT_FILE"
log ""

if [ "$OVERALL" = "PASS" ]; then
    log "${GREEN}${BOLD}LEVEL 2 CERTIFICATION: PASS${NC}"
    exit 0
else
    log "${RED}${BOLD}LEVEL 2 CERTIFICATION: FAIL${NC}"
    exit 1
fi
