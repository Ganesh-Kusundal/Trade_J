#!/bin/bash

# Quick test script for Reactive Dhan Data Endpoints
# This runs the standalone Java test with proper setup

echo "🚀 Reactive Dhan Broker - Data Endpoints Test"
echo "================================================"
echo ""

# Check if config file exists
if [ ! -f "config/dhan-sandbox.properties" ]; then
    echo "❌ Error: config/dhan-sandbox.properties not found"
    echo ""
    echo "Please ensure the config file exists with:"
    echo "  dhan.sandbox.clientId=your_client_id"
    echo "  dhan.sandbox.accessToken=your_access_token"
    exit 1
fi

echo "✅ Config file found: config/dhan-sandbox.properties"
echo "📡 Testing against Dhan Sandbox API"
echo ""

# Set Java home
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home

# Navigate to project root
cd /Users/apple/Downloads/Trade_J

# Run the test
echo "🧪 Running data endpoint tests..."
echo ""

./gradlew :broker-dhan-reactive:runDataTest

echo ""
echo "================================================"
echo "✅ Test complete!"
