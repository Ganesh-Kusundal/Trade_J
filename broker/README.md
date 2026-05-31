# broker/

| Path | Gradle ID | Role |
|------|-----------|------|
| `api/` | `:broker-api` | `IBrokerConnection`, broker port interfaces |
| `core/` | `:broker-core` | Shared auth, resilience, observability |
| `dhan/` | `:broker-dhan` | Dhan adapter |
| `upstox/` | `:broker-upstox` | Upstox adapter |

Spring configuration currently lives in `app/` — see [docs/CODE_EXTRACTION.md](../docs/CODE_EXTRACTION.md).
