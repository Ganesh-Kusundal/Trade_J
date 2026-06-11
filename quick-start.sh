#!/bin/bash
# ============================================================================
# Trade-J Quick Start - Daily Operations
# ============================================================================

set -euo pipefail

WORKSPACE_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$WORKSPACE_ROOT"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  Trade-J Quick Start - Daily Operations${NC}"
echo -e "${BOLD}  $(date -u +"%Y-%m-%d %H:%M:%S UTC")${NC}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════════${NC}"
echo ""

# Step 1: Check credentials
echo -e "${BOLD}Step 1: Checking broker credentials...${NC}"
./scripts/validate-credentials.sh
echo ""

# Step 2: Run quick certification
echo -e "${BOLD}Step 2: Running quick certification (levels -1, 0, 1)...${NC}"
echo -e "${YELLOW}Note: Full certification takes ~2.5 hours${NC}"
echo -e "${YELLOW}      Run 'tradej certify all' for complete certification${NC}"
echo ""

# Step 3: Start application
echo -e "${BOLD}Step 3: Starting Trade-J application...${NC}"
echo ""
echo -e "${CYAN}Choose a profile:${NC}"
echo "  1. Development (Sandbox)"
echo "  2. Production (Live)"
echo "  3. Paper Trading"
echo "  4. Exit"
echo ""
read -p "Select option [1-4]: " option

case $option in
    1)
        echo -e "${GREEN}Starting in Development mode (Sandbox)...${NC}"
        ./gradlew :app:bootRun --args='--spring.profiles.active=dev'
        ;;
    2)
        echo -e "${YELLOW}Starting in Production mode (Live)...${NC}"
        echo -e "${RED}⚠️  WARNING: This involves REAL capital!${NC}"
        read -p "Are you sure? (yes/no): " confirm
        if [ "$confirm" = "yes" ]; then
            ./gradlew :app:bootRun --args='--spring.profiles.active=dev-live'
        else
            echo "Aborted."
            exit 0
        fi
        ;;
    3)
        echo -e "${GREEN}Starting in Paper Trading mode...${NC}"
        ./gradlew :app:bootRun --args='--spring.profiles.active=paper'
        ;;
    4)
        echo "Exiting."
        exit 0
        ;;
    *)
        echo -e "${RED}Invalid option${NC}"
        exit 1
        ;;
esac
