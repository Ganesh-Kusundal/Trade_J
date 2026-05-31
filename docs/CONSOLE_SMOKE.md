# Console smoke checklist

Verifies the React console (`/console/`) and the API paths it uses against a running `trade-j-app`.

## Automated script

```bash
./gradlew :app:bootRun
# separate terminal
./scripts/console-smoke.sh
```

Uses `TRADEJ_ATTACH_URL` (default `http://127.0.0.1:8080`).

## Expected admin contract (production)

| Endpoint | Shape |
|----------|--------|
| `GET /admin/runtime` | `{ websocketConnected, circuitBreakerOpen, subscriptions, catalogLoaded, catalogSize, brokerPreflightPassed, startupCompleted }` |
| `GET /admin/strategies` | `{ plugins: string[], pluginCount: number }` — **not** a JSON array |
| `GET /admin/summary` | Runtime/pipeline metrics map; **no** `killSwitch` field |
| `POST /admin/risk/kill-switch/{enabled}` | `{ enabled: boolean, acknowledged: boolean }` |

## Manual browser checks

1. **Prod:** `http://localhost:8080/console/` — WS indicator green when gateway enabled.
2. **Dev:** `cd frontend && npm run dev` → `http://localhost:5173/console/` (Vite proxies to 8080).
3. **Admin panel:** Strategies list shows plugin names; kill-switch toggle reflects `enabled` after POST.
4. **Network:** `studio/startup-candidates`, `studio/chart`, `pipeline/stream/metrics` (SSE).

## Known data dependencies

- Studio chart/candidates need parquet warehouse or scan data; empty chart may be valid when warehouse is absent.
- Gateway WebSocket requires `tradej.gateway.enabled=true`.
