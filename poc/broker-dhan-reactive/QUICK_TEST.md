# 🧪 Quick Test - Reactive Dhan Data Endpoints

## 📦 What's Here

A simple standalone Java test that verifies all reactive data endpoints work correctly.

**Location**: `poc/broker-dhan-reactive/` (isolated POC folder)

---

## 🚀 Quick Start

### Option 1: Run Script (Easiest)

```bash
# Just run! (uses config/dhan-sandbox.properties)
cd poc/broker-dhan-reactive
./run-data-test.sh
```

### Option 2: Gradle Command

```bash
# Run from project root (uses config/dhan-sandbox.properties)
cd /Users/apple/Downloads/Trade_J
./gradlew :broker-dhan-reactive:runDataTest
```

### Option 3: From IDE

1. Open `ReactiveDhanDataTest.java`
2. Set environment variables in run configuration
3. Right-click → Run `ReactiveDhanDataTest.main()`

---

## 📊 What Gets Tested

| # | Test | Endpoint | What It Verifies |
|---|------|----------|------------------|
| 1 | **Get LTP** | `/marketfeed/ltp` | Last traded price |
| 2 | **Get Quote** | `/marketfeed/quote` | Full OHLCV data |
| 3 | **Fund Limits** | `/fundlimit` | Available balance |
| 4 | **Holdings** | `/holdings` | Portfolio holdings |
| 5 | **Positions** | `/positions` | Live positions |
| 6 | **Historical Candles** | `/charts/intraday` | 5-min candles |
| 7 | **Batch LTP** | `/marketfeed/ltp` | Multiple symbols |

---

## ✅ Expected Output

```
🚀 Reactive Dhan Broker - Data Endpoints Test
============================================================

📡 Connecting to Dhan Sandbox API...
   Client ID: test123

✅ Broker initialized successfully

🔵 Test 1: Fetch LTP for RELIANCE
   ✅ LTP: ₹2456.70

🔵 Test 2: Fetch Quote for RELIANCE
   ✅ Quote Data:
      LTP: ₹2456.70
      Open: ₹2440.00
      High: ₹2470.00
      Low: ₹2435.00
      Volume: 1000000

🔵 Test 3: Fetch Fund Limits
   ✅ Fund Limits:
      Available: ₹100000.00
      Utilized: ₹25000.00
      Total: ₹125000.00

🔵 Test 4: Fetch Holdings
   ✅ Holdings Count: 5
   Sample Holdings:
      - RELIANCE: 10 shares
      - TCS: 5 shares
      - INFY: 20 shares

🔵 Test 5: Fetch Positions
   ✅ Positions Count: 2
   Sample Positions:
      - NIFTY24JUN24000CE: 50 qty
      - BANKNIFTY24JUN48000PE: 25 qty

🔵 Test 6: Fetch Historical Candles (5-min, last 1 day)
   ✅ Candles Received: 5
   Latest Candle:
      O: 244000, H: 244500, L: 243800, C: 244200

🔵 Test 7: Fetch Batch LTP (RELIANCE, TCS)
   ✅ Batch LTP:
      RELIANCE: ₹2456.70
      TCS: ₹3845.50

============================================================
✅ ALL TESTS PASSED!
============================================================
```

---

## 🔍 Architecture

```
ReactiveDhanDataTest (main method)
  ↓
DhanReactiveBroker (facade)
  ↓
├── DhanReactiveMarketDataProvider
│   ├── getLtpPaisa()
│   ├── getQuote()
│   ├── getCandles()
│   └── getLtpBatch()
│
└── DhanReactivePortfolioProvider
    ├── getFundLimits()
    ├── getHoldings()
    └── getPositions()
```

All using **reactive** patterns (Mono/Flux) with **WebClient** instead of blocking HTTP!

---

## 🎯 Key Benefits

1. **100% Isolated** - In `poc/` folder, no impact on `broker/dhan`
2. **Simple to Run** - Just set 2 env vars and run script
3. **Fast Feedback** - Tests all endpoints in ~10 seconds
4. **Real Validation** - Makes actual API calls to sandbox
5. **Reactive Proof** - Confirms Mono/Flux patterns work

---

## 🛠️ Troubleshooting

### "Config file not found"
```bash
# Ensure config file exists
ls -la config/dhan-sandbox.properties

# It should contain:
# dhan.sandbox.clientId=your_client_id
# dhan.sandbox.accessToken=your_access_token
```

### "Connection timeout"
- Check internet connection
- Verify sandbox API is running
- Increase timeout in `DhanReactiveConnectionSettings`

### "401 Unauthorized"
- Verify credentials are correct
- Check if using sandbox vs live credentials
- Ensure token is valid

---

## 📝 Next Steps

After tests pass:
1. ✅ Reactive module validated
2. ✅ Can add options chain tests
3. ✅ Can add order placement tests
4. ✅ Ready for production use (with live credentials)

---

**Happy Testing! 🚀**
