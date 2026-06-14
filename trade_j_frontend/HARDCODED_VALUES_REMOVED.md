# Frontend Hardcoded Values Removed

## Summary
All hardcoded values have been centralized into `/src/config/terminal.config.ts` for easy management and configuration.

## Changes Made

### 1. Created Central Configuration File
**File:** `src/config/terminal.config.ts`

Centralized the following:
- ✅ Exchange segment mappings (EXCHANGE_MAP)
- ✅ Default symbols per exchange (DEFAULT_SYMBOLS)
- ✅ Broker exchange support matrix (BROKER_EXCHANGES)
- ✅ Timeframe configurations (TIMEFRAME_CONFIG)
- ✅ Index configurations with fallbacks and validation ranges (INDEX_CONFIG)
- ✅ Feed polling intervals (FEED_CONFIG)
- ✅ UI default settings (UI_DEFAULTS)
- ✅ Local storage keys (STORAGE_KEYS)

### 2. Updated Files to Use Configuration

#### `src/App.tsx`
**Before:** 35+ lines of hardcoded constants
**After:** Single import from config file
- Removed hardcoded EXCHANGE_MAP
- Removed hardcoded DEFAULT_SYMBOLS
- Removed hardcoded BROKER_EXCHANGES
- Removed hardcoded TIMEFRAME_CONFIG
- Replaced hardcoded localStorage keys with STORAGE_KEYS constants
- Replaced hardcoded defaults with UI_DEFAULTS

#### `src/api/SimulationFeed.ts`
**Before:** Hardcoded polling intervals
**After:** Uses FEED_CONFIG
- LTP_INTERVAL_MS: 2000 → FEED_CONFIG.LTP_INTERVAL_MS
- DEPTH_INTERVAL_MS: 3000 → FEED_CONFIG.DEPTH_INTERVAL_MS
- CANDLE_REFRESH_MS: 60000 → FEED_CONFIG.CANDLE_REFRESH_MS

#### `src/api/TerminalDataOrchestrator.ts`
**Before:** Hardcoded timing constants
**After:** Uses FEED_CONFIG
- SESSION_INTERVAL_MS: 60000 → FEED_CONFIG.SESSION_INTERVAL_MS
- HEALTH_INTERVAL_MS: 5000 → FEED_CONFIG.HEALTH_INTERVAL_MS
- STALE_AFTER_MS: 30000 → FEED_CONFIG.STALE_AFTER_MS
- DELAYED_AFTER_MS: 5000 → FEED_CONFIG.DELAYED_AFTER_MS
- STALE_CHECK_MS: 2000 → FEED_CONFIG.STALE_CHECK_MS

#### `src/hooks/useMarketIndices.ts`
**Before:** Hardcoded index data with magic numbers
**After:** Uses INDEX_CONFIG
- Index names, symbols, fallbacks, and validation ranges all from INDEX_CONFIG
- Dynamically generates INDEX_FALLBACKS, INDEX_SYMBOLS, and VALID_RANGES from config
- Removed hardcoded "NIFTY 50": 24500, "BANK NIFTY": 52800, "INDIA VIX": 14.2

## Benefits

1. **Single Source of Truth**: All configuration in one file
2. **Easy to Modify**: Change defaults, add exchanges, update intervals without hunting through code
3. **Type Safe**: TypeScript ensures config values are used correctly
4. **Testable**: Configuration can be mocked in tests
5. **Maintainable**: No more magic numbers scattered throughout the codebase

## Configuration Structure

```typescript
// Example: Add a new exchange
DEFAULT_SYMBOLS.NEW_EXCHANGE = ["SYM1", "SYM2"];

// Example: Change polling intervals
FEED_CONFIG.LTP_INTERVAL_MS = 1000; // Poll faster

// Example: Add a new index
INDEX_CONFIG.push({
  name: "NEW INDEX",
  apiSymbol: "NEW-INDEX",
  exchange: "NSE",
  segment: "IDX_I",
  fallback: 10000,
  min: 5000,
  max: 15000,
});

// Example: Change default broker
UI_DEFAULTS.defaultBroker = "UPSTOX";
```

## Files Modified
- ✅ `src/config/terminal.config.ts` (NEW)
- ✅ `src/App.tsx`
- ✅ `src/api/SimulationFeed.ts`
- ✅ `src/api/TerminalDataOrchestrator.ts`
- ✅ `src/hooks/useMarketIndices.ts`

## No Breaking Changes
All functionality remains the same - only the location of configuration values has changed.
