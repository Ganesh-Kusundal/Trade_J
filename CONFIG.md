# Configuration and Spring Profiles

## Spring profiles

| Profile | Active when | Credential files | Broker API | Storage root |
|---------|-------------|------------------|------------|--------------|
| `dev` (default) | Local `bootRun` | `config/dhan-local.properties` + `config/dhan-sandbox.properties` | SANDBOX | `runtime-dev/` |
| `dev-live` | Live market data locally | Same as `dev` | LIVE | `runtime-dev/` |
| `test` | Spring runtime in test profile | `config/dhan-sandbox.properties` (+ optional local for TOTP) | SANDBOX | `runtime-test/` |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | `config/dhan-local.properties` only | LIVE | `runtime-prod/` |

Profile group `dev-live` loads `application-dev.yml` then `application-dev-live.yml` (live broker override plus the same `runtime-dev/` storage paths).

**Note:** Default `dev` (sandbox) starts Spring successfully but startup preflight may fail on portfolio/fund-limit calls that still use the live SDK path. Use `dev-live` for full runtime smoke (market data, WS, historical preflight).

## Credential files (gitignored)

| File | Used by |
|------|---------|
| [config/dhan-local.properties](config/dhan-local.properties) | Live broker, `dev-live`, `prod`, TOTP refresh |
| [config/dhan-sandbox.properties](config/dhan-sandbox.properties) | Sandbox orders, `dev`, `test` |
| [config/dhan-pin.txt](config/dhan-pin.txt) | TOTP live auth |
| [config/dhan-totp-secret.txt](config/dhan-totp-secret.txt) | TOTP live auth |

Copy from `config/dhan-local.properties.example` and [config/dhan-sandbox.properties.example](config/dhan-sandbox.properties.example).

## Run commands

```bash
# Sandbox broker (default)
./gradlew :trade-app:bootRun

# Live broker (market data, WS)
./gradlew :trade-app:bootRun --args='--spring.profiles.active=dev-live'

# Production
SPRING_PROFILES_ACTIVE=prod ./gradlew :trade-app:bootRun
```

## Naming clarifications

- **`trade.broker.environment`** (`LIVE` / `SANDBOX`) — which Dhan API host the single runtime broker uses (`api.dhan.co` vs `sandbox.dhan.co`).
- **Spring profile `test`** — Spring Boot config for isolated storage paths; not the same as Gradle `@Tag("integration")` or JUnit tags.
- **Gradle integration tests** — still load credential files via [LiveDhanTestSupport](trade-app/src/test/java/com/tradej/app/integration/LiveDhanTestSupport.java), independent of `spring.profiles.active`.

## Property binding

All `trade.*` settings bind to [TradingProperties](trade-app/src/main/java/com/tradej/app/config/TradingProperties.java). Profile YAML maps sandbox or live keys from the `dhan.*` property files into `trade.broker.*`.
