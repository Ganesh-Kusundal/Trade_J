#!/bin/bash

# Test script for Reactive Dhan - LIVE/PRODUCTION API
# Fetches REAL historical data from live market (not sandbox)

echo "🚀 Reactive Dhan Broker - LIVE Historical Data Test"
echo "======================================================"
echo ""

# Check if config file exists
if [ ! -f "config/dhan-local.properties" ]; then
    echo "❌ Error: config/dhan-local.properties not found"
    echo ""
    echo "Please ensure the config file exists with:"
    echo "  dhan.clientId=your_client_id"
    echo "  dhan.accessToken=your_access_token"
    exit 1
fi

echo "✅ Config file found: config/dhan-local.properties"
echo "📡 Testing against Dhan LIVE/Production API"
echo "⚠️  This will fetch REAL market data!"
echo ""

# Set Java home
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home

# Navigate to project root
cd /Users/apple/Downloads/Trade_J

# Run the test
echo "🧪 Running live historical data tests..."
echo ""

./gradlew :broker-dhan-reactive:runDataTest

echo ""
echo "======================================================"
echo "✅ Live historical data test complete!"
