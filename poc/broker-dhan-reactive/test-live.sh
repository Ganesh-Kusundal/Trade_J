#!/bin/bash

# Live Test Script for Reactive Dhan Broker
# This script runs integration tests against the Dhan sandbox API

echo "🚀 Reactive Dhan Broker - Live Integration Test"
echo "================================================"
echo ""

# Check for credentials
if [ -z "$DHAN_CLIENT_ID" ] || [ -z "$DHAN_API_SECRET" ]; then
    echo "❌ Error: Environment variables not set"
    echo ""
    echo "Please set:"
    echo "  export DHAN_CLIENT_ID=your_client_id"
    echo "  export DHAN_API_SECRET=your_api_secret"
    echo ""
    echo "You can find these in: config/dhan-sandbox.properties"
    exit 1
fi

echo "✅ Credentials found"
echo "📡 Testing against Dhan Sandbox API"
echo ""

# Set Java home
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home

# Run the live integration tests
echo "🧪 Running live integration tests..."
echo ""

cd /Users/apple/Downloads/Trade_J

./gradlew :broker-dhan-reactive:test \
    -Ddhan.reactive.live=true \
    --tests "com.tradej.broker.dhan.reactive.integration.DhanReactiveLiveIntegrationTest" \
    --info 2>&1 | grep -E "(LIVE:|✅|🔵|🔴|⏳|PASSED|FAILED|Error)"

echo ""
echo "================================================"
echo "✅ Live integration test complete!"
