# options-analytics (not wired)

This module is **not** included in `settings.gradle` and is not part of the production build.

It depends on pipeline events (`OptionChainUpdated`) that are not yet published on the hot path. Wire it only after:

1. Adding `OptionChainUpdated` to `:core` domain events
2. Including `trading-options-analytics` in `settings.gradle`
3. Registering `GreeksCalcNode` in the DAG template

Until then, use `:data-analytics` and broker option chain APIs for production analytics.
