# Production Deployment Checklist

## Pre-deployment

- [ ] `trade.risk.enforce-margin=false` (enable after paper validation)
- [ ] `trade.risk.enforce-unrealized-loss=false`
- [ ] `trade.reconciliation.auto-halt=false` or `mismatch-tolerance-qty=1`
- [ ] `trade.runtime.mode=LIVE`
- [ ] Broker credentials valid and non-expired
- [ ] Kill switch disengaged (`POST /admin/risk/kill-switch/false`)
- [ ] Disk space for Chronicle and DuckDB paths
- [ ] `/actuator/health` returns UP (broker, market data, feed health)

## Smoke test

```bash
chmod +x scripts/production-smoke-test.sh
BASE_URL=http://localhost:8080 ./scripts/production-smoke-test.sh
```

## Staged feature rollout

1. Deploy with all risk feature flags OFF
2. Enable `trade.reconciliation.auto-halt` with tolerance=1 for 24h
3. Enable `trade.risk.enforce-unrealized-loss` in paper mode
4. Enable `trade.risk.enforce-margin` after margin API validation
5. Enable `trade.options.analytics-enabled` for vol surface / max pain APIs

## Rollback

- Disable feature flags in `application.yml` and restart
- Engage kill switch: `POST /admin/risk/kill-switch/true`

## New APIs

| Endpoint | Purpose |
|----------|---------|
| `POST /api/v1/orders/place` | REST order placement (LIVE only) |
| `PUT /api/v1/orders/{id}` | Modify order |
| `POST /api/v1/orders/{id}/cancel` | Cancel order |
| `GET /api/v1/options/volatility-surface` | IV surface + max pain |
| `POST /admin/reconciliation/acknowledge` | Clear reconciliation halt |
