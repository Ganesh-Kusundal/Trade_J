# Trade-J Terminal Integration Plan

**Date**: 2026-06-10  
**Status**: Planning Complete, Ready for Execution

---

## 🎯 Overview

Integrate the professional `trade-j-terminal` with the existing Trade-J backend to create a complete, production-ready trading platform.

---

## 📊 Current State

### trade-j-terminal (New)
- ✅ Professional UI with 7 workspaces
- ✅ TradingView charts
- ✅ OMS (Order Management System)
- ✅ Options chain
- ✅ Market depth
- ✅ Real-time updates (mock)
- ❌ Mock data only
- ❌ No backend integration
- ❌ No IST timezone

### frontend/ (Existing)
- ✅ Market View screen
- ✅ Backend API integration
- ✅ IST timezone support
- ✅ Mock API server working
- ❌ Single screen only
- ❌ No OMS
- ❌ No options chain

---

## 🔧 Integration Tasks

### Phase 1: Infrastructure Setup (30 min)

1. **Merge Terminal into Existing Frontend**
   - Copy `trade-j-terminal/src` to `frontend/src/terminal/`
   - Update imports and paths
   - Resolve dependency conflicts

2. **Update Dependencies**
   - Merge package.json files
   - Install missing dependencies
   - Verify TypeScript compatibility

3. **Configure Vite**
   - Update vite.config.ts
   - Add proxy configuration for backend
   - Configure environment variables

### Phase 2: Backend Integration (1 hour)

4. **Create API Adapter Layer**
   - Create `frontend/src/terminal/api/tradeApi.ts`
   - Map terminal types to backend types
   - Implement API client with error handling

5. **Replace Mock Data with Real API**
   - Watchlist → `/api/v1/market/candles`
   - Quotes → WebSocket or polling
   - Options chain → `/api/v1/market/options`
   - Orders → `/api/v1/orders`
   - Positions → `/api/v1/positions`

6. **Add WebSocket Support**
   - Connect to backend WebSocket
   - Real-time quote updates
   - Order status updates
   - Position PnL updates

### Phase 3: IST Timezone (15 min)

7. **Add IST Timezone Throughout**
   - Update all timestamp displays
   - Use `formatISTDateTime()` from existing code
   - Update chart time axis

### Phase 4: Testing & Validation (30 min)

8. **End-to-End Testing**
   - Test all 7 workspaces
   - Verify real data flows
   - Test order placement
   - Test options chain
   - Validate IST timezone

9. **Performance Testing**
   - Measure API response times
   - Test WebSocket latency
   - Verify chart rendering performance

---

## 📁 File Structure (After Integration)

```
frontend/
├── src/
│   ├── terminal/                    ← New terminal code
│   │   ├── App.tsx                  ← Main terminal app
│   │   ├── types.ts                 ← Domain types
│   │   ├── components/              ← UI components
│   │   │   ├── Header.tsx
│   │   │   ├── Watchlist.tsx
│   │   │   ├── TradingChart.tsx
│   │   │   ├── OptionChain.tsx
│   │   │   ├── MarketDepth.tsx
│   │   │   ├── TimeAndSales.tsx
│   │   │   ├── OrderEntryPanel.tsx
│   │   │   ├── TerminalTabs.tsx
│   │   │   └── ...
│   │   ├── api/                     ← NEW: API integration
│   │   │   ├── tradeApi.ts          ← Backend API client
│   │   │   ├── websocket.ts         ← WebSocket client
│   │   │   └── adapters.ts          ← Type adapters
│   │   ├── store/                   ← State management
│   │   │   └── terminalStore.ts
│   │   └── utils/                   ← Utilities
│   │       └── quantUtils.ts
│   │
│   ├── pages/                       ← Existing pages
│   │   └── MarketView.tsx
│   │
│   ├── store/                       ← Existing store
│   │   └── marketViewStore.ts
│   │
│   ├── types/                       ← Existing types
│   │   └── market.ts
│   │
│   └── main.tsx                     ← Entry point
```

---

## 🚀 Execution Order

1. ✅ Analyze terminal structure (DONE)
2. ⏳ Setup infrastructure
3. ⏳ Create API adapter layer
4. ⏳ Replace mock data
5. ⏳ Add WebSocket support
6. ⏳ Add IST timezone
7. ⏳ Test end-to-end
8. ⏳ Deploy

---

## 🎯 Success Criteria

- [ ] All 7 workspaces functional
- [ ] Real backend data (not mock)
- [ ] IST timezone throughout
- [ ] Order placement works
- [ ] Options chain shows real data
- [ ] WebSocket updates working
- [ ] Performance < 100ms for API calls
- [ ] Zero TypeScript errors
- [ ] All tests pass

---

## ⚠️ Risks & Mitigations

**Risk 1**: Dependency conflicts
- **Mitigation**: Use existing versions where possible, upgrade carefully

**Risk 2**: Type mismatches between terminal and backend
- **Mitigation**: Create adapter layer to map types

**Risk 3**: Backend API endpoints missing
- **Mitigation**: Use mock API server as fallback, implement missing endpoints later

**Risk 4**: Performance issues with real data
- **Mitigation**: Add caching, pagination, virtualization

---

## 📊 Timeline

| Phase | Duration | Status |
|-------|----------|--------|
| Infrastructure | 30 min | ⏳ Pending |
| Backend Integration | 60 min | ⏳ Pending |
| IST Timezone | 15 min | ⏳ Pending |
| Testing | 30 min | ⏳ Pending |
| **Total** | **~2.25 hours** | |

---

**Next Action**: Start Phase 1 - Infrastructure Setup
