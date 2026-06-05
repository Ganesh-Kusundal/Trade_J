#!/bin/bash
# Quick backend-frontend connection test script

echo "🔍 Testing Trade-J Backend-Frontend Connection..."
echo ""

# Test 1: Check if backend is running
echo "1️⃣ Testing backend health..."
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "   ✅ Backend is running on port 8080"
    curl -s http://localhost:8080/actuator/health | head -3
else
    echo "   ❌ Backend is NOT running"
    echo "   Start with: ./gradlew :app:bootRun"
    exit 1
fi
echo ""

# Test 2: Check console static files
echo "2️⃣ Testing console static files..."
if curl -s http://localhost:8080/console/ | grep -q "Trade-J"; then
    echo "   ✅ Console is accessible"
else
    echo "   ❌ Console not found"
fi
echo ""

# Test 3: Test REST API
echo "3️⃣ Testing REST API endpoints..."
if curl -s http://localhost:8080/api/v1/symbols > /dev/null 2>&1; then
    echo "   ✅ Symbols API responding"
else
    echo "   ⚠️  Symbols API not responding (may need data)"
fi

if curl -s http://localhost:8080/admin/runtime > /dev/null 2>&1; then
    echo "   ✅ Admin API responding"
else
    echo "   ⚠️  Admin API not responding"
fi
echo ""

# Test 4: WebSocket endpoint
echo "4️⃣ Testing WebSocket endpoint..."
if curl -s -I http://localhost:8080/ws/gateway 2>&1 | grep -q "Upgrade"; then
    echo "   ✅ WebSocket endpoint available"
else
    echo "   ⚠️  WebSocket may not be accessible via HTTP (try WS client)"
fi
echo ""

echo "✅ Connection test complete!"
echo ""
echo "📖 Open in browser: http://localhost:8080/console/"
echo "📋 Full docs: FRONTEND_BACKEND_CONNECTION_TEST.md"
