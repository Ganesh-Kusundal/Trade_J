# DhanHQ Safety Rules Implementation Plan

## Overview
Implement the missing safety rules from the DhanHQ skill reference to make the Trade-J Dhan adapter production-ready for live trading.

## Safety Rules to Implement (P0 - Blocking)

| # | Rule | Description | Location |
|---|------|-------------|----------|
| 1 | Confirm before live orders | Require explicit confirmation token for place/modify/cancel/kill-switch | `DhanOrderCommandAdapter`, `DhanRestOrderClient` |
| 2 | Show readable order preview | Expose `previewOrder()` returning `OrderPreview` with margin, notional, validation | New: `DhanOrderValidator`, `OrderPreview` |
| 3 | Default to LIMIT orders | Validate orderType; default to LIMIT if not specified; warn on MARKET | `DhanOrderValidator` |
| 4 | Warn when notional > ₹50,000 | Compute notional (qty × price/LTP); warn if > 5,000,000 paisa | `DhanOrderValidator` |
| 5 | Validate lot size for F&O | Quantity must be multiple of `lotSize` from instrument catalog | `DhanOrderValidator` |
| 6 | Never CNC/MTF for F&O/commodity/currency | Segment × ProductType validation matrix | `DhanOrderValidator` |

## Correctness Fixes (P1)

| # | Issue | Fix |
|---|-------|-----|
| 7 | Sandbox `setKillSwitch()` returns false positive | Throw `UnsupportedOperationException` |
| 8 | Modify order double-API-call race | Add idempotency key or fetch-before-modify pattern |
| 9 | Slice order payload may need slice-specific fields | Verify against Dhan API docs; adjust if needed |

## New Files to Create

1. **`DhanValidationException.java`** - Custom exception for validation failures
2. **`DhanOrderValidator.java`** - Central validation service
3. **`OrderPreview.java`** - Preview result model (in core domain)
4. **`OrderPreview.java` in broker-api** - Interface for preview capability

## Files to Modify

| File | Changes |
|------|---------|
| `DhanProtocolConstants.java` | Add `MAX_NOTIONAL_PAISA = 5_000_000L` |
| `DhanOrderCommandAdapter.java` | Inject `DhanOrderValidator`; validate in `placeOrder`/`modifyOrder`/`cancelOrder`/`setKillSwitch`; add `previewOrder()` |
| `DhanRestOrderClient.java` | Add validation in `baseOrderPayload`; fix sandbox `setKillSwitch` |
| `DhanBrokerConnection.java` | Wire `DhanOrderValidator`; expose `previewOrder()` on facade |
| `DhanSdkConverters.java` | Ensure productType conversion handles all cases |
| `DhanInstrumentCatalog.java` | Ensure `lotSize` is populated from security master |

## Test Files to Create/Update

| Test File | Purpose |
|-----------|---------|
| `DhanOrderValidatorUnitTest.java` | Unit tests for all validation rules |
| `DhanRestOrderClientValidationTest.java` | Sandbox order validation tests |
| `DhanOrderCommandAdapterValidationTest.java` | Live adapter validation tests |
| `DhanOrderPreviewIntegrationTest.java` | Integration test for preview flow |

## Validation Matrix (Segment × ProductType)

| Segment | Allowed Product Types | Rejected |
|---------|----------------------|----------|
| `NSE_EQ`, `BSE_EQ` | `INTRADAY`, `CNC`, `MARGIN` | `CARRY_FORWARD` |
| `NSE_FNO`, `BSE_FNO` | `INTRADAY`, `MARGIN` | `CNC`, `CARRY_FORWARD` |
| `MCX_COMM` | `INTRADAY`, `MARGIN` | `CNC`, `CARRY_FORWARD` |
| `NSE_CURRENCY`, `BSE_CURRENCY` | `INTRADAY`, `MARGIN` | `CNC`, `CARRY_FORWARD` |
| `IDX_I` | `INTRADAY`, `MARGIN` | `CNC`, `CARRY_FORWARD` |

## Implementation Steps

### Phase 1: Core Domain & Exceptions (Day 1)
1. Create `DhanValidationException` in `broker/dhan/exceptions/`
2. Add `OrderPreview` to `core/domain/model/` (or `broker-api/port/`)
3. Add `MAX_NOTIONAL_PAISA` to `DhanProtocolConstants`

### Phase 2: Validator Service (Day 1-2)
4. Create `DhanOrderValidator` with all validation methods
5. Add unit tests for each validation rule

### Phase 3: Integration with Adapters (Day 2)
6. Modify `DhanRestOrderClient` - add validation in `baseOrderPayload`, fix sandbox kill-switch
7. Modify `DhanOrderCommandAdapter` - inject validator, validate in all mutating methods
8. Add `previewOrder()` to `OrderCommand` interface (broker-api) and implement

### Phase 4: Wiring & Facade (Day 2-3)
9. Update `DhanBrokerConnection` to wire validator
10. Update `DhanSdkConverters` if needed
11. Ensure `DhanInstrumentCatalog` provides `lotSize`

### Phase 5: Testing (Day 3)
12. Write integration tests
13. Run existing test suite to ensure no regressions
14. Update `TRADEHULL_PARITY.md` if needed

## Dependencies
- Requires `DhanInstrumentResolver` to provide `lotSize` and `tickSizePaisa`
- Requires `MarketDataProvider` for LTP lookup (for MARKET order notional)
- Uses `PriceMath` for paisa conversions

## Rollout Strategy
1. Deploy validator in **warn-only mode** first (log warnings, don't throw)
2. After verification, enable **strict mode** (throw on violations)
3. Add configuration flag `dhan.validation.strict=true/false`

## Acceptance Criteria
- [ ] All 6 safety rules enforced (configurable strict/warn)
- [ ] `previewOrder()` returns accurate margin/notional/validation
- [ ] Sandbox `setKillSwitch()` throws `UnsupportedOperationException`
- [ ] All existing unit/integration tests pass
- [ ] New unit tests cover all validation rules (happy + error paths)
- [ ] Integration test verifies end-to-end validation flow