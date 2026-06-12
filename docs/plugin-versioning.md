# Trade-J Plugin Versioning Policy

## Overview

All Trade-J plugin SPIs carry version and compatibility metadata via `PluginDescriptor`.
The runtime validates plugin compatibility at registration time and rejects incompatible plugins.

## Version Scheme

All plugins use **Semantic Versioning 2.0.0** (`MAJOR.MINOR.PATCH`):

- **MAJOR** — Breaking changes to the SPI interface
- **MINOR** — New capabilities, backward-compatible
- **PATCH** — Bug fixes, no interface changes

## Platform Version

The platform exposes a monotonically increasing integer version:

```java
PluginDescriptor.CURRENT_PLATFORM_VERSION = 1
```

Each `PluginDescriptor` declares:

| Field | Purpose |
|-------|---------|
| `version` | Plugin's own semver |
| `minPlatformVersion` | Minimum platform version required |
| `maxPlatformVersion` | Maximum supported platform version (-1 = unlimited) |
| `dependencies` | Other plugin names this plugin requires |

## Compatibility Matrix

```
Plugin loads successfully when:
  platformVersion >= plugin.minPlatformVersion
  AND (plugin.maxPlatformVersion == -1 OR platformVersion <= plugin.maxPlatformVersion)
  AND all plugin.dependencies are installed
```

## SPI Versioning Rules

1. Each SPI interface starts at version `1.0.0`
2. Adding a `default` method to an SPI is a MINOR bump
3. Adding a required method is a MAJOR bump
4. Deprecating a method uses `@Deprecated(forRemoval = true)` one minor release before removal
5. Plugins declare their SPI version in their `version()` method

## Deprecation Timeline

| Action | Notice Period |
|--------|--------------|
| SPI method deprecated | 1 minor release warning |
| SPI method removed | 1 major release after deprecation |
| Plugin marked incompatible | Immediate on platform version bump |

## Runtime Behavior

- `PluginDescriptor.isCompatible()` — checks at registration
- `PluginLifecycleManager.install()` — validates before loading
- Incompatible plugins log a WARN and are skipped
- `tradej plugins` CLI shows version + compatibility status

## Current Plugin Versions

| Plugin Type | SPI | Current Version |
|------------|-----|----------------|
| Broker | BrokerProvider | 1.0.0 |
| Indicator | IndicatorProvider | 1.0.0 |
| Transformation | TransformationProvider | 1.0.0 |
| Strategy (Graph) | GraphStrategyPlugin | 1.0.0 |
| Strategy (Provider) | StrategyPluginProvider | 1.0.0 |
| Scanner | ScannerProvider | 1.0.0 |
| Analytics | AnalyticsProvider | 1.0.0 |
