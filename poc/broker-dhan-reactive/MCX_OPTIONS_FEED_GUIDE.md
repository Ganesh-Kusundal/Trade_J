# MCX Options Live Feed Test Guide

## Overview

This test demonstrates live MCX options feed monitoring for contracts with highest Open Interest (OI) and Volume.

## What It Does

1. **Fetches MCX Option Chain** for GOLD and SILVER
2. **Identifies Top Contracts** by OI and Volume
3. **Subscribes to Live WebSocket Feed** for real-time updates
4. **Displays Real-Time Data** for 30 seconds

## How to Run

### Prerequisites

1. Java 21 installed
2. Dhan live API credentials configured in `../../config/dhan-local.properties`
3. MCX market must be live (typically 9:00 AM - 11:30 PM IST)

### Run Command

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
cd /Users/apple/Downloads/Trade_J
./gradlew :broker-dhan-reactive:runMcxOptionsFeedTest --no-daemon
```

## Expected Output

```
=== MCX Options Live Feed - High OI & Volume ===

✓ Configuration loaded (MCX live market)
✓ HTTP client initialized

Step 1: Fetching MCX GOLD Options Chain...
  Nearest expiry: 2026-06-30

  ┌─ Highest OI Contracts ──────────────────┐
  │ CALL: Strike 75000    OI: 1,234 LTP: ₹450.50  │
  │ PUT:  Strike 74000    OI: 2,345 LTP: ₹380.20  │
  └──────────────────────────────────────────┘

  ┌─ Highest Volume Contracts ──────────────┐
  │ CALL: Strike 75000    Vol: 5,678 LTP: ₹450.50 │
  │ PUT:  Strike 74000    Vol: 6,789 LTP: ₹380.20 │
  └──────────────────────────────────────────┘
  ✓ Analyzed 45 contracts

Step 2: Fetching MCX SILVER Options Chain...
  [Similar output for SILVER]

Step 3: Subscribing to Live WebSocket Feed...
  Connecting to Dhan WebSocket for MCX live feed...
  (Monitoring top MCX contracts for 30 seconds)

  [15:30:45] GOLD: LTP=75234.50 Volume=1234
  [15:30:46] SILVER: LTP=87654.30 Volume=5678
  [15:30:47] GOLD: LTP=75238.20 Volume=1235
  ...

  ✓ Live feed completed (30 seconds)
  Total updates received: 156

✅ MCX OPTIONS LIVE FEED TEST COMPLETE!
```

## Key Features Tested

### 1. Option Chain Fetching
- Retrieves expiry list for MCX segment
- Fetches option chain for nearest expiry
- Parses CALL and PUT option details
- Extracts OI, Volume, LTP data

### 2. Contract Analysis
- Identifies highest OI contracts (calls & puts)
- Identifies highest volume contracts
- Displays strike price, OI, volume, LTP

### 3. Live WebSocket Feed
- Connects to Dhan WebSocket API
- Subscribes to MCX instruments
- Receives real-time LTP updates
- Monitors for 30 seconds

### 4. Rate Limiting
- Uses DATA rate limit (5 req/s)
- Automatically throttles API calls
- Prevents API ban under load

## MCX Trading Hours

- **Monday to Friday**: 9:00 AM - 11:30 PM IST
- **Saturday/Sunday**: Closed
- **Exchange Holidays**: Closed

> **Note**: Run this test during MCX market hours for live data!

## Top MCX Contracts

### GOLD Options
- **Symbol**: GOLD
- **Lot Size**: 1 gram
- **Tick Size**: ₹1
- **Strike Range**: ₹60,000 - ₹90,000
- **Expiry**: Monthly (last day of month)

### SILVER Options
- **Symbol**: SILVER
- **Lot Size**: 1 kg
- **Tick Size**: ₹1
- **Strike Range**: ₹70,000 - ₹120,000
- **Expiry**: Monthly (last day of month)

### CRUDEOIL Options
- **Symbol**: CRUDEOIL
- **Lot Size**: 100 barrels
- **Tick Size**: ₹1
- **Strike Range**: ₹3,000 - ₹8,000
- **Expiry**: Monthly

## Interpreting Results

### High Open Interest (OI)
- **High OI Call**: Resistance level (sellers active)
- **High OI Put**: Support level (buyers active)
- **OI Buildup**: New positions being created

### High Volume
- **High Volume**: Active trading, good liquidity
- **Volume > OI**: Intraday trading dominant
- **Volume < OI**: Position holding dominant

### LTP Analysis
- **LTP Movement**: Price discovery
- **Bid-Ask Spread**: Liquidity indicator
- **Volume Weighted**: Institutional activity

## Troubleshooting

### "No expiry data available"
- MCX market may be closed
- Check trading hours
- Verify API credentials

### "WebSocket connection failed"
- Check internet connection
- Verify token is valid
- Check firewall settings

### "Rate limit exceeded"
- Rate limiter is working correctly
- Wait for token refill
- Reduce request frequency

### "No option chain data"
- Underlying symbol incorrect
- Expiry date not available
- API endpoint changed

## Next Steps

After successful test:

1. **Analyze OI Data**: Identify support/resistance levels
2. **Track Volume**: Monitor liquidity patterns
3. **Build Strategy**: Use OI/volume for trading signals
4. **Automate**: Schedule regular monitoring
5. **Integrate**: Connect to trading system

## Related Tests

- **Multi-Symbol Historical**: `./gradlew :broker-dhan-reactive:runMultiSymbolLoadTest`
- **WebSocket Market Feed**: `./gradlew :broker-dhan-reactive:runDataTest`
- **Rate Limiter Tests**: `./gradlew :broker-dhan-reactive:test --tests "*LoadCapacityTest*"`

## API Documentation

- **Dhan API**: https://api.dhan.co/
- **Option Chain Endpoint**: `GET /optionchain`
- **Expiry List Endpoint**: `GET /optionchain/expirylist`
- **WebSocket Feed**: `wss://api.dhan.co/v2/feed`

---

**Status**: ✅ Ready for Testing  
**Market**: MCX (Multi Commodity Exchange)  
**Contracts**: GOLD, SILVER, CRUDEOIL Options  
**Features**: OI Analysis, Volume Tracking, Live Feed
