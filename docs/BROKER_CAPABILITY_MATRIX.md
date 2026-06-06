# Broker Capability Matrix

Verified capability matrix for all three production brokers in the Trade-J gateway.

> Generated from `BrokerProvider.descriptor()` static declarations. For live probe results, use `BrokerInspector`.

## Port Interface Coverage

| Port Interface | Dhan | Upstox | ICICI |
|---|:---:|:---:|:---:|
| **MarketDataProvider** | YES | YES | YES |
| **OptionsProvider** | YES | YES | YES |
| **OrderCommand** | YES | YES | YES |
| **OrderQuery** | YES | YES | YES |
| **PortfolioProvider** | YES | YES | YES |
| **MarginProvider** | YES | YES | YES |
| **InstrumentResolver** | YES | YES | YES |
| **WebSocketMultiplexer** | YES | YES | YES |
| **FuturesProvider** | YES | YES | YES |
| **BracketOrderProvider** | YES | - | - |
| **GttOrderProvider** | YES | YES | - |
| **SliceOrderCommand** | YES | YES | - |
| **SessionRiskProvider** | YES | - | - |
| **ConditionalAlertProvider** | YES | YES | - |
| **NewsProvider** | - | YES | - |
| **MarketStatusProvider** | YES | YES | YES |
| **CoverOrderProvider** | NO | NO | NO(stub) |

## OrderCommand Methods

| Method | Dhan | Upstox | ICICI |
|---|:---:|:---:|:---:|
| `placeOrder` | YES | YES | YES |
| `modifyOrder` | YES | YES | YES |
| `cancelOrder` | YES | YES | YES |
| `cancelAllOpenOrders` | YES | YES | YES |
| `setKillSwitch` | YES | YES | - |
| `previewOrder` | YES | Stub | YES |

## OptionsProvider Methods

| Method | Dhan | Upstox | ICICI |
|---|:---:|:---:|:---:|
| `getExpiries` | YES | YES | YES |
| `getOptionChain` | YES | YES | YES |
| `getGreeks` | YES | YES | YES |
| `getOptionContracts` | YES | YES | YES |
| `selectStrikePaisa` | YES | YES | YES |

## Authentication Modes

| Broker | Auth Modes |
|---|---|
| **Dhan** | STATIC, TOTP, WEB_RENEWABLE |
| **Upstox** | PKCE + refresh token |
| **ICICI** | BROWSER_AUTOMATED, STATIC, TOTP_6DIGIT, TOTP_EXTERNAL |

## Exchange Segments

| Broker | Segments |
|---|---|
| **Dhan** | NSE_EQ, BSE_EQ, NSE_FNO, BSE_FNO, MCX_COMM, NSE_CURRENCY, BSE_CURRENCY, IDX_I |
| **Upstox** | NSE_EQ, BSE_EQ, NSE_FNO, BSE_FNO, MCX_COMM, IDX_I |
| **ICICI** | NSE_EQ, BSE_EQ, NSE_FNO, BSE_FNO, MCX_COMM |

## Rate Limits

| Broker | Limits |
|---|---|
| **Dhan** | Orders: 10rps, Data: 5rps, Quotes: 1rps, OptionChain: 1rps |
| **Upstox** | Orders: 10rps/500rpm, Data: 50rps/500rpm, OptionChain: 1rps |
| **ICICI** | Breeze API rate limits apply |

## Environment Support

| Broker | Environments |
|---|---|
| **Dhan** | LIVE, SANDBOX |
| **Upstox** | LIVE, SANDBOX |
| **ICICI** | LIVE only |

## Broker-Specific Extras

| Feature | Dhan (`DhanExtras`) | Upstox (`UpstoxExtras`) |
|---|---|---|
| News | - | YES (`NewsProvider`) |
| Session Risk | YES (`SessionRiskProvider`) | - |
| Data Services | - | Planned |

## Notes

- **ICICI**: No MARKET orders, no kill switch, no square-off batch
- **Upstox**: `previewOrder` is stub-only (returns basic estimate without broker call)
- **Dhan**: Most comprehensive capability coverage (15/15 port interfaces)
