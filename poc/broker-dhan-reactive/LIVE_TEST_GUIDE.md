# 🧪 Live Testing Guide - Reactive Dhan Broker

## Quick Start

### Option 1: Run All Live Tests

```bash
# 1. Set your Dhan sandbox credentials
export DHAN_CLIENT_ID="your_client_id"
export DHAN_API_SECRET="your_api_secret"

# 2. Run the test script
cd broker-dhan-reactive
./test-live.sh
```

### Option 2: Run Individual Tests via Gradle

```bash
# Set credentials first
export DHAN_CLIENT_ID="your_client_id"
export DHAN_API_SECRET="your_api_secret"

# Run all live tests
./gradlew :broker-dhan-reactive:test \
    -Ddhan.reactive.live=true \
    --tests "DhanReactiveLiveIntegrationTest"

# Run specific test
./gradlew :broker-dhan-reactive:test \
    -Ddhan.reactive.live=true \
    --tests "DhanReactiveLiveIntegrationTest.testGetLtp"
```

### Option 3: Create a Quick Manual Test

Create a simple Java file to test specific endpoints:

```java
// broker-dhan-reactive/src/test/java/com/tradej/broker/dhan/reactive/QuickManualTest.java
package com.tradej.broker.dhan.reactive;

public class QuickManualTest {
    public static void main(String[] args) {
        // Your test code here
        System.out.println("Testing reactive Dhan broker...");
    }
}
```

## What Gets Tested

### ✅ Market Data
- [x] Fetch LTP (Last Traded Price)
- [x] Fetch full quote (OHLCV)
- [x] Fetch batch LTP for multiple symbols
- [x] Fetch historical candles (intraday)

### ✅ Portfolio
- [x] Fetch fund limits
- [x] Fetch holdings
- [x] Fetch positions

### ⚠️ Orders (Use with caution!)
- [ ] Place real order (1 qty minimum)
- [ ] Cancel order
- [ ] Check order status

## Expected Output

```
🚀 Reactive Dhan Broker - Live Integration Test
================================================

✅ Credentials found
📡 Testing against Dhan Sandbox API

🧪 Running live integration tests...

🔵 Fetching LTP for RELIANCE...
✅ RELIANCE LTP: ₹2456.70

🔵 Fetching quote for RELIANCE...
✅ RELIANCE Quote:
   LTP: ₹2456.70
   Open: ₹2440.00
   High: ₹2470.00
   Low: ₹2435.00
   Volume: 1000000

🔵 Fetching fund limits...
✅ Fund Limits:
   Available: ₹100000.00
   Utilized: ₹25000.00
   Total: ₹125000.00

================================================
✅ Live integration test complete!
```

## Troubleshooting

### No Credentials Error
```
❌ Error: Environment variables not set
```
**Solution**: Export DHAN_CLIENT_ID and DHAN_API_SECRET

### Connection Timeout
```
Timeout on blocking read for 10000 MILLISECONDS
```
**Solution**: 
- Check internet connection
- Verify sandbox API is running (https://api.dhan.co)
- Increase timeout in DhanReactiveConnectionSettings

### Authentication Failed
```
401 Unauthorized
```
**Solution**: 
- Verify credentials are correct
- Check if token is expired
- Ensure sandbox mode is enabled

### Order Placement Fails
```
Insufficient margin
```
**Solution**: 
- Add funds to sandbox account
- Check if market is open (9:15 AM - 3:30 PM IST)
- Verify symbol is tradable

## Safety Notes

⚠️ **IMPORTANT**: 
- Tests run against **SANDBOX** environment only
- No real money is used
- Order tests use minimum quantity (1 share)
- Orders are automatically cancelled after placement
- Do NOT run during market hours if you want to avoid actual trades

## Next Steps

After live tests pass:
1. ✅ Reactive module is validated
2. ✅ Can be used in production (with live credentials)
3. ✅ All endpoints confirmed working
4. ✅ Ready for integration with trading strategies
