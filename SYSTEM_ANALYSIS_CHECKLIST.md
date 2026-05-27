# System Analysis Checklist

Use this checklist before signing off on an in-depth architecture review. All **required** rows in [REGRESSION_MANIFEST.md](REGRESSION_MANIFEST.md) must be green (or explicitly skipped with documented sandbox/live limitation).

## Expected behavior contract (summary)

| Area | Inputs | Outputs | Failure modes |
|------|--------|---------|---------------|
| Market data | Instrument key, live creds | Quote, candles, option chain | Auth expiry, rate limit |
| Orders (sandbox) | OrderRequest, catalog | orderId, cancel ack | DH-905 payload, unsupported endpoint |
| Execution | SignalPendingExecution | OrderAccepted / Suppressed | Circuit open, broker error |
| OMS | Order events | LifecycleState transitions | Invalid transition throws |
| Runtime | Spring config, subscriptions | Health, CandleDeveloping | Catalog load fail, preflight fail |

## Pre-analysis gates

1. Run `./scripts/run-full-regression.sh` (or `./gradlew fullRegressionTest --no-daemon` with env flags).
2. Confirm `config/dhan-local.properties` and `config/dhan-sandbox.properties` exist and preflight passes.
3. Review [TRADEHULL_PARITY.md](TRADEHULL_PARITY.md) for capability vs environment (live vs sandbox).

## Analysis focus areas

1. **Dual-environment routing** — Sandbox connections must not call live SDK (`DhanClientHolder` guard + REST order path).
2. **Order path parity** — Live uses SDK; sandbox uses `DhanRestOrderClient`; verify field mapping matches Dhan v2 docs.
3. **OMS vs broker truth** — `EventSourcedOrderRepository` vs broker positions (`ReconciliationScheduler`).
4. **Pipeline ordering** — Risk → Candle → Strategy → Execution → Dispatch (`DisruptorEventBus`).
5. **Idempotency** — `CaffeineIdempotencyCache` on correlation id for place order.

## Known sandbox limitations

- Simulated fills at price 100; not representative of slippage or latency.
- Subset of endpoints (e.g. `/pnlExit`, some super/forever paths may 404).
- `getOrder` / correlation lookup may be stubbed; tests assert place/cancel only.

## Sign-off

- [ ] `fullRegressionTest` completed locally
- [ ] Required manifest rows reviewed
- [ ] No silent live routing from sandbox tests
- [ ] Stale parity doc items reconciled with code
