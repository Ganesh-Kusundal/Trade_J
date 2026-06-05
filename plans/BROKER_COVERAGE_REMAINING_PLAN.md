# Remaining Broker Coverage & Hygiene Plan

## 1. Delete `broker-common`
- Empty module with zero source files. Adds build complexity for no value.
- Remove from `settings.gradle`, delete directory.

## 2. Move Benchmarks to JMH Source Set
- `BrokerPerformanceBenchmark` and `BrokerParallelLoadBenchmarkTest` in `broker-core/src/test`
- Create `broker-core/src/jmh/java/...` and move both files
- JMH plugin already applied in root `build.gradle`

## 3. HTTP Failure Tests (Mock HttpClient)
- **Dhan**: `DhanAuthenticatedHttpClient` — 401 auth failure, 500 server error, malformed JSON, network IOException
- **ICICI**: `BreezeAuthenticatedHttpClient` — 401, 500, malformed JSON, v2 API errors
- **Upstox**: `UpstoxHttpClient` — 401, 500, network failure
- Use injected `HttpClient` mock to simulate failures without live connectivity.

## 4. WebSocket Testability Refactor
- **ICICI**: `BreezeWebSocketMultiplexer` — make `handleDisconnect`, `reconnectManager`, `healthExecutor` package-visible or injectable
- **Upstox**: `UpstoxWebSocketMultiplexer` — same for `closeSockets`, `cancelHealthChecks`, `reconnect`
- Rewrite existing reflection-based tests to use package-visible seams.
- Add new disconnect/reconnect tests for both brokers.

## 5. CI Workflow Update
- Fix Java version from 21 → 26 (match project toolchain)
- Add SpotBugs + Checkstyle report upload as artifacts
- Add `broker-common` removal validation step

## 6. JaCoCo (Blocked)
- Wait for JaCoCo release supporting Java 26 class files (major version 70).
